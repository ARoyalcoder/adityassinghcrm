package com.pawanputra.bos.finance;

import static com.pawanputra.bos.support.ApiClient.as;
import static com.pawanputra.bos.support.ApiClient.tokenFor;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import com.pawanputra.bos.finance.api.PaymentIntake;
import com.pawanputra.bos.finance.api.PaymentIntake.GatewayPayment;
import com.pawanputra.bos.finance.api.PaymentIntake.Receipt;
import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.TestAccounts;
import com.pawanputra.bos.support.TestAccounts.Account;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Finance over real HTTP and PostgreSQL: invoices, payments and their allocation, notes, refunds,
 * ageing, and the three promises the module makes: nothing is changed destructively, money is never
 * counted twice, and a repeated request takes effect once.
 */
class FinanceIT extends AbstractIntegrationTest {

    private static final String INVOICES = "/api/v1/invoices";
    private static final String PAYMENTS = "/api/v1/payments";
    private static final String METHODS = "/api/v1/payment-methods";
    private static final String FINANCE = "/api/v1/finance";
    private static final String CUSTOMERS = "/api/v1/customers";
    /** Dates in the books are the business's dates, wherever the server is. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentIntake paymentIntake;

    Account adminAccount;
    /** Creates customers; prepares and issues invoices, records payments. May not approve. */
    String admin;
    /** Everything in finance, including approving. */
    String finance;
    String customerId;
    String cash;
    String upi;
    LocalDate today;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        adminAccount = accounts.activeUser(RoleCodes.ADMIN);
        admin = tokenFor(adminAccount);
        finance = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.FINANCE));
        customerId = customer(admin, "Acme Solar", "9876543210");
        Response methods = as(finance).get(METHODS);
        cash = methods.path("find { it.code == 'CASH' }.id");
        upi = methods.path("find { it.code == 'UPI' }.id");
        today = LocalDate.now(BUSINESS_ZONE);
    }

    // ------------------------------------------------------------------ invoices

    @Test
    void anInvoiceIsPreparedAsADraftThenIssuedAndFrozen() {
        as(admin).body(Map.of("customerId", customerId, "items", List.of())).post(INVOICES)
                .then().statusCode(400).body("errors.field", hasItem("items"));
        as(admin).body(Map.of("customerId", UUID.randomUUID().toString(), "items", List.of(line("Survey", 1, 500, 0))))
                .post(INVOICES).then().statusCode(400).body("errors[0].field", equalTo("customerId"));

        Response created = as(admin).body(Map.of(
                        "customerId", customerId, "reference", " PO-7781 ", "notes", "Thank you.",
                        "items", List.of(
                                line("Dome camera, installed", 8, 12500, 18, Map.of("discountPercent", 10, "hsnSac", "8525", "unit", "pcs")),
                                line("Site survey", 1, 500, 0))))
                .post(INVOICES);
        created.then().statusCode(201)
                .body("invoice.status", equalTo("DRAFT"))
                .body("invoice.invoiceNumber", nullValue())
                .body("invoice.customerName", equalTo("Acme Solar"))
                .body("invoice.outstanding", equalTo(0.0f))
                .body("reference", equalTo("PO-7781"))
                .body("paymentTermsDays", equalTo(15))
                .body("subtotal", equalTo(100500.0f))
                .body("discountTotal", equalTo(10000.0f))
                .body("taxTotal", equalTo(16200.0f))
                .body("invoice.total", equalTo(106700.0f))
                .body("items.description", contains("Dome camera, installed", "Site survey"))
                .body("items[0].quantity", equalTo(8))
                .body("items[0].lineTotal", equalTo(106200.0f))
                .body("items[0].hsnSac", equalTo("8525"))
                .body("actions.edit", equalTo(true)).body("actions.issue", equalTo(true))
                .body("actions.recordPayment", equalTo(false));
        String id = created.path("invoice.id");
        int version = created.path("version");

        // What the lines add up to can be asked for without saving anything.
        as(admin).body(Map.of("items", List.of(line("Cable", 62.5, 48, 18)))).post(INVOICES + "/preview")
                .then().statusCode(200)
                .body("subtotal", equalTo(3000.0f)).body("taxTotal", equalTo(540.0f)).body("total", equalTo(3540.0f))
                .body("lines[0].lineTotal", equalTo(3540.0f));

        // A draft can be changed, by whoever has its latest version.
        Map<String, Object> changed = Map.of("customerId", customerId, "paymentTermsDays", 30,
                "items", List.of(line("Dome camera, installed", 10, 12500, 18)), "version", version);
        as(admin).body(changed).put(INVOICES + "/" + id).then().statusCode(200)
                .body("invoice.total", equalTo(147500.0f)).body("items", hasSize(1)).body("paymentTermsDays", equalTo(30));
        as(admin).body(changed).put(INVOICES + "/" + id).then().statusCode(409);

        Response issued = as(admin).post(INVOICES + "/" + id + "/issue");
        issued.then().statusCode(200)
                .body("invoice.status", equalTo("ISSUED"))
                .body("invoice.invoiceNumber", matchesPattern("INV-\\d{4}-000001"))
                .body("invoice.issueDate", equalTo(today.toString()))
                .body("invoice.dueDate", equalTo(today.plusDays(30).toString()))
                .body("invoice.outstanding", equalTo(147500.0f))
                .body("issuedByName", equalTo("Test User"))
                .body("actions.edit", equalTo(false)).body("actions.discard", equalTo(false))
                .body("actions.recordPayment", equalTo(true))
                // The administrator prepares and issues, but does not approve corrections.
                .body("actions.creditNote", equalTo(false)).body("actions.cancel", equalTo(false));
        String number = issued.path("invoice.invoiceNumber");

        // Issued means frozen: no edit, no delete, no second issue.
        int issuedVersion = issued.path("version");
        Map<String, Object> late = new HashMap<>(changed);
        late.put("version", issuedVersion);
        as(admin).body(late).put(INVOICES + "/" + id).then().statusCode(422)
                .body("detail", equalTo("Invoice " + number + " has been issued and can no longer be changed. "
                        + "Use a credit note or a debit note instead."));
        as(admin).delete(INVOICES + "/" + id).then().statusCode(422).body("detail", containsString("Cancel it instead"));
        as(admin).post(INVOICES + "/" + id + "/issue").then().statusCode(422)
                .body("detail", equalTo("Invoice " + number + " has already been issued"));
        as(admin).get(INVOICES + "/" + id).then().statusCode(200).body("invoice.total", equalTo(147500.0f));
    }

    @Test
    void aDraftCanBeDiscardedAndInvoiceNumbersHaveNoGaps() {
        String first = issue(draft(customerId, 1000), null, null).path("invoice.invoiceNumber");

        // A draft that is dropped never had a number, so it leaves no hole in the series.
        String dropped = draft(customerId, 300);
        as(admin).delete(INVOICES + "/" + dropped).then().statusCode(204);
        as(admin).get(INVOICES + "/" + dropped).then().statusCode(404);
        // Nor does an attempt to issue that fails.
        String undated = draft(customerId, 400);
        as(admin).body(Map.of("issueDate", today.plusDays(1).toString())).post(INVOICES + "/" + undated + "/issue")
                .then().statusCode(400).body("errors[0].field", equalTo("issueDate"));
        as(admin).body(Map.of("dueDate", today.minusDays(1).toString())).post(INVOICES + "/" + undated + "/issue")
                .then().statusCode(400).body("errors[0].field", equalTo("dueDate"));

        String second = issue(undated, null, null).path("invoice.invoiceNumber");
        assertThat(first).endsWith("-000001");
        assertThat(second).endsWith("-000002");

        // The discarded draft is gone from the list but still in the table, marked deleted.
        as(admin).get(INVOICES).then().body("totalItems", equalTo(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE id = ?::uuid AND deleted_at IS NOT NULL",
                Integer.class, dropped)).isEqualTo(1);
    }

    @Test
    void numbersRunSeparatelyForEachFiscalYearAndEachOrganization() {
        int fiscalYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        LocalDate lastYearEnd = LocalDate.of(fiscalYear, 3, 31);
        String thisYear = "%02d%02d".formatted(fiscalYear % 100, (fiscalYear + 1) % 100);
        String lastYear = "%02d%02d".formatted((fiscalYear - 1) % 100, fiscalYear % 100);

        issue(draft(customerId, 100), null, null).then().body("invoice.invoiceNumber", equalTo("INV-" + thisYear + "-000001"));
        issue(draft(customerId, 100), lastYearEnd, lastYearEnd.plusDays(15))
                .then().body("invoice.invoiceNumber", equalTo("INV-" + lastYear + "-000001"));
        issue(draft(customerId, 100), null, null).then().body("invoice.invoiceNumber", equalTo("INV-" + thisYear + "-000002"));

        String otherAdmin = tokenFor(accounts.activeUser(RoleCodes.ADMIN));
        String theirCustomer = customer(otherAdmin, "Elsewhere Ltd", "9876500099");
        String theirs = as(otherAdmin).body(Map.of("customerId", theirCustomer, "items", List.of(line("Work", 1, 100, 0))))
                .post(INVOICES).then().statusCode(201).extract().path("invoice.id");
        as(otherAdmin).post(INVOICES + "/" + theirs + "/issue").then().statusCode(200)
                .body("invoice.invoiceNumber", equalTo("INV-" + thisYear + "-000001"));
    }

    // ------------------------------------------------------------------ payments and allocation

    @Test
    void aPaymentSettlesInvoicesAndNeverByMoreThanIsOwed() {
        String a = issued(customerId, 1000);
        String b = issued(customerId, 500);

        Response paid = pay(customerId, 1200, upi, Map.of("reference", "UTR 4411", "notes", "Part of October",
                "allocations", List.of(alloc(a, 1000), alloc(b, 200))));
        paid.then().statusCode(201)
                .body("payment.paymentNumber", matchesPattern("RCT-\\d{4}-000001"))
                .body("payment.status", equalTo("RECEIVED"))
                .body("payment.customerName", equalTo("Acme Solar"))
                .body("payment.methodName", equalTo("UPI"))
                .body("payment.receivedOn", equalTo(today.toString()))
                .body("payment.amount", equalTo(1200.0f))
                .body("payment.allocated", equalTo(1200.0f))
                .body("payment.unallocated", equalTo(0.0f))
                .body("recordedByName", equalTo("Test User"))
                .body("source", equalTo("MANUAL"))
                .body("allocations.amount", hasItems(1000.0f, 200.0f))
                .body("allocations.released", contains(false, false));
        String paymentNumber = paid.path("payment.paymentNumber");

        as(admin).get(INVOICES + "/" + a).then()
                .body("invoice.status", equalTo("PAID")).body("invoice.outstanding", equalTo(0.0f))
                .body("invoice.amountPaid", equalTo(1000.0f))
                .body("payments.paymentNumber", contains(paymentNumber)).body("payments[0].methodName", equalTo("UPI"))
                .body("actions.recordPayment", equalTo(false));
        as(admin).get(INVOICES + "/" + b).then()
                .body("invoice.status", equalTo("PARTIALLY_PAID")).body("invoice.outstanding", equalTo(300.0f));
        as(admin).queryParam("customerId", customerId).get(INVOICES + "/open").then().statusCode(200)
                .body("id", contains(b)).body("[0].outstanding", equalTo(300.0f));

        // More than the invoice is owed; more than the payment holds; a paid invoice.
        pay(customerId, 400, cash, Map.of("allocations", List.of(alloc(b, 400)))).then().statusCode(422)
                .body("detail", matchesPattern("Invoice INV-\\d{4}-000002 has only ₹300 outstanding"));
        pay(customerId, 100, cash, Map.of("allocations", List.of(alloc(b, 150)))).then().statusCode(400)
                .body("errors[0].field", equalTo("allocations"))
                .body("errors[0].message", equalTo("The allocations add up to ₹150, but only ₹100 of the payment is unallocated"));
        pay(customerId, 100, cash, Map.of("allocations", List.of(alloc(a, 100)))).then().statusCode(422)
                .body("detail", containsString("is paid and cannot take a payment"));
        pay(customerId, 100, cash, Map.of("allocations", List.of(alloc(b, 50), alloc(b, 50)))).then().statusCode(400)
                .body("errors[0].message", equalTo("The same invoice is listed twice"));

        // A draft is not owed, and another customer's invoice is not this customer's debt.
        pay(customerId, 100, cash, Map.of("allocations", List.of(alloc(draft(customerId, 100), 100))))
                .then().statusCode(422).body("detail", equalTo("Invoice (draft) is draft and cannot take a payment"));
        String other = customer(admin, "Bright Interiors", "9876500001");
        pay(customerId, 100, cash, Map.of("allocations", List.of(alloc(issued(other, 100), 100))))
                .then().statusCode(422).body("detail", containsString("belongs to another customer"));

        // What the method needs, and when the money can have arrived.
        pay(customerId, 100, upi, Map.of()).then().statusCode(400)
                .body("errors[0].field", equalTo("reference")).body("errors[0].message", equalTo("A reference is required for UPI"));
        pay(customerId, 100, cash, Map.of("receivedOn", today.plusDays(1).toString())).then().statusCode(400)
                .body("errors[0].field", equalTo("receivedOn"));
        pay(customerId, 0, cash, Map.of()).then().statusCode(400).body("errors[0].field", equalTo("amount"));
        pay(customerId, 10.005, cash, Map.of()).then().statusCode(400).body("errors[0].field", equalTo("amount"));

        // None of those attempts recorded anything or used a receipt number.
        pay(customerId, 300, cash, Map.of("allocations", List.of(alloc(b, 300)))).then().statusCode(201)
                .body("payment.paymentNumber", matchesPattern("RCT-\\d{4}-000002"));
        as(admin).get(INVOICES + "/" + b).then().body("invoice.status", equalTo("PAID"));
        as(admin).get(PAYMENTS).then().body("totalItems", equalTo(2));
    }

    @Test
    void anAdvanceIsAllocatedLaterAndUnnamedMoneySettlesTheOldestDebtFirst() {
        Response advance = pay(customerId, 1000, cash, Map.of());
        advance.then().statusCode(201).body("payment.unallocated", equalTo(1000.0f)).body("allocations", empty())
                .body("actions.allocate", equalTo(true));
        String advanceId = advance.path("payment.id");

        String recent = issued(customerId, 600);
        String old = issue(draft(customerId, 700), today.minusDays(40), today.minusDays(25)).path("invoice.id");

        as(admin).body(Map.of("allocations", List.of(alloc(recent, 600)))).post(PAYMENTS + "/" + advanceId + "/allocations")
                .then().statusCode(201).body("payment.allocated", equalTo(600.0f)).body("payment.unallocated", equalTo(400.0f));
        as(admin).body(Map.of("allocations", List.of(alloc(old, 500)))).post(PAYMENTS + "/" + advanceId + "/allocations")
                .then().statusCode(400).body("errors[0].field", equalTo("allocations"));

        // No invoices named: the one due longest ago is settled first.
        String another = issued(customerId, 900);
        pay(customerId, 1000, cash, Map.of("autoAllocate", true)).then().statusCode(201)
                .body("payment.allocated", equalTo(1000.0f))
                .body("allocations", hasSize(2))
                .body("allocations.find { it.invoiceId == '" + old + "' }.amount", equalTo(700.0f))
                .body("allocations.find { it.invoiceId == '" + another + "' }.amount", equalTo(300.0f));
        as(admin).get(INVOICES + "/" + old).then().body("invoice.status", equalTo("PAID"));
        as(admin).get(INVOICES + "/" + another).then().body("invoice.outstanding", equalTo(600.0f));

        // More than the customer owes: the rest waits, unallocated.
        pay(customerId, 5000, cash, Map.of("autoAllocate", true)).then().statusCode(201)
                .body("payment.allocated", equalTo(600.0f)).body("payment.unallocated", equalTo(4400.0f));

        as(admin).queryParam("unallocated", true).queryParam("sort", "amount,asc").get(PAYMENTS).then().statusCode(200)
                .body("items.unallocated", contains(400.0f, 4400.0f));
        as(admin).get(FINANCE + "/summary").then().statusCode(200)
                .body("unallocatedPayments", equalTo(4800.0f)).body("outstanding", equalTo(0.0f));
    }

    // ------------------------------------------------------------------ corrections

    @Test
    void mistakesAreCorrectedByNewRecordsNotByEditing() {
        String invoice = issued(customerId, 1000);
        Response paid = pay(customerId, 1000, cash, Map.of("allocations", List.of(alloc(invoice, 1000))));
        String payment = paid.path("payment.id");
        String allocation = paid.path("allocations[0].id");
        String release = PAYMENTS + "/" + payment + "/allocations/" + allocation + "/release";

        // Taking money back off an invoice needs approval.
        as(admin).body(Map.of("reason", "Wrong invoice")).post(release).then().statusCode(403);
        as(finance).body(Map.of()).post(release).then().statusCode(400);

        as(finance).body(Map.of("reason", "Wrong invoice")).post(release).then().statusCode(200)
                .body("payment.allocated", equalTo(0.0f)).body("payment.unallocated", equalTo(1000.0f))
                // The allocation is still there to be read, marked as taken back, with why.
                .body("allocations", hasSize(1))
                .body("allocations[0].released", equalTo(true))
                .body("allocations[0].releaseReason", equalTo("Wrong invoice"));
        as(finance).get(INVOICES + "/" + invoice).then()
                .body("invoice.status", equalTo("ISSUED")).body("invoice.outstanding", equalTo(1000.0f))
                .body("payments[0].released", equalTo(true));
        as(finance).body(Map.of("reason", "Again")).post(release).then().statusCode(422)
                .body("detail", equalTo("This allocation has already been released"));
        // Two rows that cancel out, not one row removed.
        assertThat(jdbc.queryForList("SELECT amount FROM payment_allocations WHERE payment_id = ?::uuid ORDER BY amount",
                BigDecimal.class, payment)).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("-1000"), new BigDecimal("1000"));

        // Unallocated money can go back to the customer, but no more than there is.
        String refunds = PAYMENTS + "/" + payment + "/refunds";
        as(admin).body(Map.of("amount", 400, "methodId", cash, "reason", "Overpaid")).post(refunds).then().statusCode(403);
        as(finance).body(Map.of("amount", 400, "methodId", cash, "reason", "Overpaid")).post(refunds).then().statusCode(201)
                .body("payment.refunded", equalTo(400.0f)).body("payment.unallocated", equalTo(600.0f))
                .body("refunds[0].refundNumber", matchesPattern("RFD-\\d{4}-000001"))
                .body("refunds[0].reason", equalTo("Overpaid")).body("refunds[0].methodName", equalTo("Cash"))
                .body("actions.reverse", equalTo(false));
        as(finance).body(Map.of("amount", 700, "methodId", cash, "reason", "The rest")).post(refunds).then().statusCode(422)
                .body("detail", startsWith("Only ₹600 of payment RCT-"));
        as(finance).body(Map.of("amount", 100, "methodId", upi, "reason", "By UPI")).post(refunds).then().statusCode(400)
                .body("errors[0].field", equalTo("reference"));
        as(finance).body(Map.of("reason", "Entered twice")).post(PAYMENTS + "/" + payment + "/reverse")
                .then().statusCode(422).body("detail", containsString("has been partly refunded and cannot be reversed"));

        // A payment that should never have been recorded: a cheque that bounced.
        Response cheque = pay(customerId, 1000, cash, Map.of("allocations", List.of(alloc(invoice, 1000))));
        String bounced = cheque.path("payment.id");
        as(finance).get(INVOICES + "/" + invoice).then().body("invoice.status", equalTo("PAID"));
        as(admin).body(Map.of("reason", "Cheque bounced")).post(PAYMENTS + "/" + bounced + "/reverse").then().statusCode(403);
        as(finance).body(Map.of("reason", "Cheque bounced")).post(PAYMENTS + "/" + bounced + "/reverse").then().statusCode(200)
                .body("payment.status", equalTo("REVERSED"))
                .body("payment.amount", equalTo(1000.0f)).body("payment.allocated", equalTo(0.0f))
                .body("payment.unallocated", equalTo(0.0f))
                .body("reversalReason", equalTo("Cheque bounced")).body("reversedByName", equalTo("Test User"))
                .body("allocations[0].released", equalTo(true))
                .body("allocations[0].releaseReason", equalTo("Payment reversed: Cheque bounced"))
                .body("actions.allocate", equalTo(false)).body("actions.refund", equalTo(false));
        as(finance).get(INVOICES + "/" + invoice).then()
                .body("invoice.status", equalTo("ISSUED")).body("invoice.outstanding", equalTo(1000.0f));

        // It stays on record, and nothing more can be done with it.
        as(finance).get(PAYMENTS + "/" + bounced).then().statusCode(200).body("payment.status", equalTo("REVERSED"));
        as(finance).body(Map.of("reason", "Again")).post(PAYMENTS + "/" + bounced + "/reverse").then().statusCode(422);
        as(finance).body(Map.of("allocations", List.of(alloc(invoice, 100)))).post(PAYMENTS + "/" + bounced + "/allocations")
                .then().statusCode(422).body("detail", containsString("has been reversed"));
        as(finance).queryParam("status", "REVERSED").get(PAYMENTS).then().body("items.id", contains(bounced));
    }

    @Test
    void creditAndDebitNotesChangeWhatIsOwedOnAnIssuedInvoice() {
        String invoice = issued(customerId, 1000);
        String credits = INVOICES + "/" + invoice + "/credit-notes";
        String debits = INVOICES + "/" + invoice + "/debit-notes";

        as(admin).body(Map.of("amount", 300, "reason", "One camera returned")).post(credits).then().statusCode(403);
        as(finance).body(Map.of("amount", 300)).post(credits).then().statusCode(400);

        as(finance).body(Map.of("amount", 300, "reason", " One camera returned ")).post(credits).then().statusCode(201)
                .body("invoice.status", equalTo("ISSUED"))
                .body("invoice.total", equalTo(1000.0f)).body("creditTotal", equalTo(300.0f))
                .body("invoice.outstanding", equalTo(700.0f))
                .body("creditNotes[0].noteNumber", matchesPattern("CRN-\\d{4}-000001"))
                .body("creditNotes[0].reason", equalTo("One camera returned"))
                .body("creditNotes[0].noteDate", equalTo(today.toString()))
                .body("creditNotes[0].issuedByName", equalTo("Test User"))
                // Once anything has been settled against it, an invoice can no longer simply be cancelled.
                .body("actions.cancel", equalTo(false));
        as(finance).body(Map.of("amount", 800, "reason", "Too much")).post(credits).then().statusCode(422)
                .body("detail", containsString("has only ₹700 outstanding"));
        as(finance).body(Map.of("amount", 10, "reason", "Dated early", "noteDate", today.minusDays(3).toString()))
                .post(credits).then().statusCode(400).body("errors[0].field", equalTo("noteDate"));

        pay(customerId, 700, cash, Map.of("allocations", List.of(alloc(invoice, 700)))).then().statusCode(201);
        as(finance).get(INVOICES + "/" + invoice).then().body("invoice.status", equalTo("PAID"));
        as(finance).body(Map.of("amount", 100, "reason", "Goodwill")).post(credits).then().statusCode(422)
                .body("detail", containsString("has nothing outstanding"));

        // A debit note makes a settled invoice owing again.
        as(finance).body(Map.of("amount", 250, "reason", "Extra cabling")).post(debits).then().statusCode(201)
                .body("invoice.status", equalTo("PARTIALLY_PAID"))
                .body("debitTotal", equalTo(250.0f)).body("invoice.outstanding", equalTo(250.0f))
                .body("debitNotes[0].noteNumber", matchesPattern("DBN-\\d{4}-000001"));
        as(finance).body(Map.of("reason", "Start again")).post(INVOICES + "/" + invoice + "/cancel").then().statusCode(422)
                .body("detail", containsString("has payments or notes against it and cannot be cancelled"));

        // A draft is simply changed; it takes no notes.
        String draft = draft(customerId, 100);
        as(finance).body(Map.of("amount", 10, "reason", "x")).post(INVOICES + "/" + draft + "/credit-notes")
                .then().statusCode(422).body("detail", startsWith("A draft has nothing to credit"));
        as(finance).body(Map.of("amount", 10, "reason", "x")).post(INVOICES + "/" + draft + "/debit-notes")
                .then().statusCode(422);
    }

    @Test
    void anUntouchedInvoiceCanBeCancelledAndKeepsItsNumber() {
        String draft = draft(customerId, 800);
        as(finance).body(Map.of("reason", "Not needed")).post(INVOICES + "/" + draft + "/cancel").then().statusCode(422)
                .body("detail", equalTo("A draft is discarded, not cancelled"));

        Response issued = issue(draft, null, null);
        String number = issued.path("invoice.invoiceNumber");
        as(admin).body(Map.of("reason", "Raised twice")).post(INVOICES + "/" + draft + "/cancel").then().statusCode(403);
        as(finance).body(Map.of()).post(INVOICES + "/" + draft + "/cancel").then().statusCode(400);

        as(finance).body(Map.of("reason", "Raised twice")).post(INVOICES + "/" + draft + "/cancel").then().statusCode(200)
                .body("invoice.status", equalTo("CANCELLED"))
                .body("invoice.invoiceNumber", equalTo(number))
                .body("invoice.total", equalTo(800.0f)).body("invoice.outstanding", equalTo(0.0f))
                .body("cancellationReason", equalTo("Raised twice")).body("cancelledAt", notNullValue())
                .body("actions.recordPayment", equalTo(false)).body("actions.creditNote", equalTo(false));

        as(finance).body(Map.of("reason", "Again")).post(INVOICES + "/" + draft + "/cancel").then().statusCode(422);
        pay(customerId, 100, cash, Map.of("allocations", List.of(alloc(draft, 100)))).then().statusCode(422)
                .body("detail", containsString("is cancelled and cannot take a payment"));
        as(finance).body(Map.of("amount", 10, "reason", "x")).post(INVOICES + "/" + draft + "/debit-notes")
                .then().statusCode(422);
        // The number is not given to anyone else.
        issue(draft(customerId, 100), null, null).then().body("invoice.invoiceNumber", equalTo(next(number)));
        as(finance).get(FINANCE + "/summary").then().body("outstanding", equalTo(100.0f));
    }

    // ------------------------------------------------------------------ due dates, overdue, ageing

    @Test
    void overdueFollowsFromTheDueDateAndShowsInTheListTheAgeingAndTheSummary() {
        String veryOld = issue(draft(customerId, 1000), today.minusDays(100), today.minusDays(95)).path("invoice.id");
        String old = issue(draft(customerId, 2000), today.minusDays(50), today.minusDays(45)).path("invoice.id");
        String fresh = issue(draft(customerId, 4000), null, null).path("invoice.id");
        String dueToday = issue(draft(customerId, 50), today.minusDays(7), today).path("invoice.id");
        String other = customer(admin, "Bright Interiors", "9876500001");
        String theirs = issue(draft(other, 300), today.minusDays(10), today.minusDays(1)).path("invoice.id");
        pay(customerId, 500, cash, Map.of("allocations", List.of(alloc(old, 500)))).then().statusCode(201);

        as(admin).get(INVOICES + "/" + veryOld).then()
                .body("invoice.status", equalTo("OVERDUE")).body("invoice.daysOverdue", equalTo(95));
        // Part paid and past due is overdue; due today is not overdue yet.
        as(admin).get(INVOICES + "/" + old).then()
                .body("invoice.status", equalTo("OVERDUE")).body("invoice.outstanding", equalTo(1500.0f));
        as(admin).get(INVOICES + "/" + dueToday).then()
                .body("invoice.status", equalTo("ISSUED")).body("invoice.daysOverdue", nullValue());
        // Nothing was written to make that so.
        assertThat(jdbc.queryForObject("SELECT status FROM invoices WHERE id = ?::uuid", String.class, veryOld))
                .isEqualTo("ISSUED");

        as(admin).queryParam("status", "OVERDUE").queryParam("sort", "dueDate,asc").get(INVOICES).then()
                .body("items.id", contains(veryOld, old, theirs));
        as(admin).queryParam("status", "ISSUED").queryParam("sort", "total,asc").get(INVOICES).then()
                .body("items.id", contains(dueToday, fresh));
        as(admin).queryParam("status", "PARTIALLY_PAID").get(INVOICES).then().body("items", empty());
        as(admin).queryParam("outstanding", true).get(INVOICES).then().body("totalItems", equalTo(5));
        as(admin).queryParam("customerId", other).get(INVOICES).then().body("items.id", contains(theirs));
        as(admin).queryParam("search", "bright").get(INVOICES).then().body("items.id", contains(theirs));

        as(admin).get(FINANCE + "/ageing").then().statusCode(200)
                .body("asOf", equalTo(today.toString()))
                .body("buckets.label", contains("Not due", "1-30 days", "31-60 days", "61-90 days", "Over 90 days"))
                .body("totals", contains(4050.0f, 300.0f, 1500.0f, 0.0f, 1000.0f))
                .body("total", equalTo(6850.0f)).body("overdue", equalTo(2800.0f))
                .body("customers.customerName", contains("Acme Solar", "Bright Interiors"))
                .body("customers[0].amounts", contains(4050.0f, 0.0f, 1500.0f, 0.0f, 1000.0f))
                .body("customers[0].total", equalTo(6550.0f))
                .body("customers[0].oldestDaysOverdue", equalTo(95)).body("customers[0].invoices", equalTo(4))
                .body("customers[1].amounts", contains(0.0f, 300.0f, 0.0f, 0.0f, 0.0f));

        as(admin).get(FINANCE + "/summary").then().statusCode(200)
                .body("outstanding", equalTo(6850.0f)).body("overdue", equalTo(2800.0f))
                .body("dueSoon", equalTo(50.0f))
                .body("openInvoices", equalTo(5)).body("overdueInvoices", equalTo(3)).body("draftInvoices", equalTo(0))
                .body("receivedThisMonth", equalTo(500.0f)).body("unallocatedPayments", equalTo(0.0f))
                .body("currency", equalTo("INR")).body("asOf", equalTo(today.toString()));
        as(admin).get(FINANCE + "/settings").then().statusCode(200)
                .body("defaultPaymentTermsDays", equalTo(15)).body("defaultTaxPercent", equalTo(18))
                .body("today", equalTo(today.toString()));

        // The customer 360 hears about it without the customer module knowing finance exists.
        as(admin).get(CUSTOMERS + "/" + customerId + "/overview").then().statusCode(200)
                .body("health.signals.code", hasItem("INVOICES_OVERDUE"))
                .body("health.signals.find { it.code == 'INVOICES_OVERDUE' }.impact", equalTo("CRITICAL"))
                .body("health.signals.find { it.code == 'INVOICES_OVERDUE' }.message",
                        equalTo("2 invoices are overdue: ₹2,500 owing, the oldest by 95 days"));
        as(admin).get(CUSTOMERS + "/" + other + "/overview").then()
                .body("health.signals.find { it.code == 'INVOICES_OVERDUE' }.impact", equalTo("WARNING"))
                .body("health.signals.find { it.code == 'INVOICES_OVERDUE' }.message",
                        equalTo("1 invoice is overdue: ₹300 owing, the oldest by 1 day"));
        as(admin).queryParam("category", "FINANCE").get(CUSTOMERS + "/" + customerId + "/timeline").then().statusCode(200)
                .body("events.title", hasItems("Invoice issued", "Payment received"))
                .body("events.find { it.type == 'PAYMENT_RECEIVED' }.description", matchesPattern("RCT-\\d{4}-000001 for ₹500"))
                .body("events.find { it.type == 'PAYMENT_RECEIVED' }.reference.entity", equalTo("Payment"))
                .body("events.findAll { it.type == 'INVOICE_GENERATED' }", hasSize(4));
    }

    // ------------------------------------------------------------------ idempotency

    @Test
    void aRequestRepeatedWithTheSameKeyTakesEffectOnce() {
        String invoice = issued(customerId, 1000);
        Map<String, Object> body = Map.of("customerId", customerId, "amount", 600, "methodId", cash,
                "allocations", List.of(alloc(invoice, 600)));
        String key = UUID.randomUUID().toString();

        Response first = as(admin).header("Idempotency-Key", key).body(body).post(PAYMENTS);
        first.then().statusCode(201).header("Idempotent-Replayed", nullValue());
        String id = first.path("payment.id");

        // The reply was lost and the client tries again: same answer, nothing recorded twice.
        as(admin).header("Idempotency-Key", key).body(body).post(PAYMENTS).then().statusCode(201)
                .header("Idempotent-Replayed", equalTo("true"))
                .body("payment.id", equalTo(id)).body("payment.paymentNumber", equalTo(first.path("payment.paymentNumber")));
        as(admin).get(INVOICES + "/" + invoice).then().body("invoice.outstanding", equalTo(400.0f));
        as(admin).get(PAYMENTS).then().body("totalItems", equalTo(1));

        // The same key on a different request is a mistake, not a retry.
        Map<String, Object> different = new HashMap<>(body);
        different.put("amount", 601);
        as(admin).header("Idempotency-Key", key).body(different).post(PAYMENTS).then().statusCode(422)
                .body("detail", startsWith("This Idempotency-Key was already used for a different request"));
        as(admin).header("Idempotency-Key", "k".repeat(201)).body(body).post(PAYMENTS).then().statusCode(400);

        // Without a key every request stands for itself.
        pay(customerId, 50, cash, Map.of()).then().statusCode(201);
        pay(customerId, 50, cash, Map.of()).then().statusCode(201);
        as(admin).get(PAYMENTS).then().body("totalItems", equalTo(3));

        // The other requests that move money take a key too.
        String noteKey = UUID.randomUUID().toString();
        Map<String, Object> note = Map.of("amount", 100, "reason", "Goodwill");
        as(finance).header("Idempotency-Key", noteKey).body(note).post(INVOICES + "/" + invoice + "/credit-notes")
                .then().statusCode(201).header("Idempotent-Replayed", nullValue()).body("creditNotes", hasSize(1));
        as(finance).header("Idempotency-Key", noteKey).body(note).post(INVOICES + "/" + invoice + "/credit-notes")
                .then().statusCode(201).header("Idempotent-Replayed", equalTo("true"))
                .body("creditNotes", hasSize(1)).body("invoice.outstanding", equalTo(300.0f));

        String advance = pay(customerId, 500, cash, Map.of()).path("payment.id");
        String refundKey = UUID.randomUUID().toString();
        Map<String, Object> refund = Map.of("amount", 200, "methodId", cash, "reason", "Returned");
        as(finance).header("Idempotency-Key", refundKey).body(refund).post(PAYMENTS + "/" + advance + "/refunds")
                .then().statusCode(201);
        as(finance).header("Idempotency-Key", refundKey).body(refund).post(PAYMENTS + "/" + advance + "/refunds")
                .then().statusCode(201).header("Idempotent-Replayed", equalTo("true"))
                .body("refunds", hasSize(1)).body("payment.unallocated", equalTo(300.0f));
    }

    @Test
    void aRequestSentSeveralTimesAtOnceWithOneKeyRecordsOnePayment() throws Exception {
        String invoice = issued(customerId, 1000);
        Map<String, Object> body = Map.of("customerId", customerId, "amount", 1000, "methodId", cash,
                "allocations", List.of(alloc(invoice, 1000)));
        String key = UUID.randomUUID().toString();

        List<Response> responses = atOnce(6, () -> as(admin).header("Idempotency-Key", key).body(body).post(PAYMENTS));

        assertThat(responses).extracting(Response::statusCode).containsOnly(201);
        assertThat(responses).extracting(response -> response.<String>path("payment.id")).containsOnly(
                responses.get(0).<String>path("payment.id"));
        assertThat(responses).filteredOn(response -> response.header("Idempotent-Replayed") == null).hasSize(1);
        assertThat(count("payments")).isEqualTo(1);
        as(admin).get(INVOICES + "/" + invoice).then()
                .body("invoice.status", equalTo("PAID")).body("payments", hasSize(1));
    }

    // ------------------------------------------------------------------ database transactions

    @Test
    void paymentsArrivingAtOnceCannotBothSettleTheSameDebt() throws Exception {
        String invoice = issued(customerId, 1000);

        // Six people each record a payment in full for the same invoice, at the same moment.
        List<Response> responses = atOnce(6, () -> pay(customerId, 1000, cash, Map.of("allocations", List.of(alloc(invoice, 1000)))));

        assertThat(responses).filteredOn(response -> response.statusCode() == 201).hasSize(1);
        assertThat(responses).filteredOn(response -> response.statusCode() == 422).hasSize(5)
                .allSatisfy(refused -> assertThat(refused.<String>path("detail")).contains("cannot take a payment"));

        // Each failed attempt rolled back whole: no payment, no allocation, no receipt number used.
        assertThat(count("payments")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM payment_allocations WHERE invoice_id = ?::uuid",
                BigDecimal.class, invoice)).isEqualByComparingTo("1000");
        as(admin).get(INVOICES + "/" + invoice).then()
                .body("invoice.status", equalTo("PAID")).body("invoice.amountPaid", equalTo(1000.0f));
        pay(customerId, 10, cash, Map.of()).then().statusCode(201)
                .body("payment.paymentNumber", matchesPattern("RCT-\\d{4}-000002"));
    }

    @Test
    void theRunningTotalsAlwaysAgreeWithTheRecordsBehindThem() {
        String a = issued(customerId, 1000);
        String b = issued(customerId, 2500);
        Response first = pay(customerId, 1500, cash, Map.of("allocations", List.of(alloc(a, 1000), alloc(b, 500))));
        pay(customerId, 800, cash, Map.of("allocations", List.of(alloc(b, 300))));
        as(finance).body(Map.of("amount", 200, "reason", "Discount")).post(INVOICES + "/" + b + "/credit-notes");
        as(finance).body(Map.of("amount", 75, "reason", "Freight")).post(INVOICES + "/" + a + "/debit-notes");
        as(finance).body(Map.of("reason", "Wrong invoice"))
                .post(PAYMENTS + "/" + first.path("payment.id") + "/allocations/"
                        + first.path("allocations.find { it.invoiceId == '" + b + "' }.id") + "/release");
        as(finance).body(Map.of("amount", 100, "methodId", cash, "reason", "Returned"))
                .post(PAYMENTS + "/" + first.path("payment.id") + "/refunds");

        // Every invoice: its stored totals are the sums of its allocations and notes.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM invoices i
                WHERE i.organization_id = ?
                  AND (i.amount_paid <> coalesce((SELECT sum(amount) FROM payment_allocations WHERE invoice_id = i.id), 0)
                    OR i.credit_total <> coalesce((SELECT sum(amount) FROM credit_notes WHERE invoice_id = i.id), 0)
                    OR i.debit_total <> coalesce((SELECT sum(amount) FROM debit_notes WHERE invoice_id = i.id), 0))
                """, Integer.class, adminAccount.organizationId())).isZero();
        // Every payment likewise.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM payments p
                WHERE p.organization_id = ?
                  AND (p.allocated_amount <> coalesce((SELECT sum(amount) FROM payment_allocations WHERE payment_id = p.id), 0)
                    OR p.refunded_amount <> coalesce((SELECT sum(amount) FROM refunds WHERE payment_id = p.id), 0))
                """, Integer.class, adminAccount.organizationId())).isZero();
        as(admin).get(INVOICES + "/" + a).then().body("invoice.outstanding", equalTo(75.0f));
        as(admin).get(INVOICES + "/" + b).then().body("invoice.outstanding", equalTo(2000.0f));
    }

    @Test
    void theDatabaseItselfRefusesToChangeOrDeleteFinancialRecords() {
        String invoice = issued(customerId, 1000);
        String draft = draft(customerId, 100);
        Response paid = pay(customerId, 1000, cash, Map.of("allocations", List.of(alloc(invoice, 600))));
        String payment = paid.path("payment.id");
        as(finance).body(Map.of("amount", 50, "reason", "Discount")).post(INVOICES + "/" + invoice + "/credit-notes");
        as(finance).body(Map.of("amount", 50, "reason", "Freight")).post(INVOICES + "/" + invoice + "/debit-notes");
        as(finance).body(Map.of("amount", 100, "methodId", cash, "reason", "Returned")).post(PAYMENTS + "/" + payment + "/refunds");

        // Whatever the application is told to do, these statements cannot succeed.
        for (String table : List.of("payment_allocations", "refunds", "credit_notes", "debit_notes")) {
            refused("UPDATE " + table + " SET amount = 1", "never changed or deleted");
            refused("DELETE FROM " + table, "never changed or deleted");
        }
        refused("DELETE FROM payments WHERE id = '" + payment + "'", "Payments are never deleted");
        refused("UPDATE payments SET amount = 1 WHERE id = '" + payment + "'", "A recorded payment cannot be changed");
        refused("UPDATE payments SET received_on = received_on - 30 WHERE id = '" + payment + "'", "cannot be changed");
        refused("UPDATE payments SET allocated_amount = 5000 WHERE id = '" + payment + "'", "ck_payments_not_overspent");
        refused("DELETE FROM invoices WHERE id = '" + invoice + "'", "Invoices are never deleted");
        refused("DELETE FROM invoices WHERE id = '" + draft + "'", "Invoices are never deleted");
        refused("UPDATE invoices SET total = 1 WHERE id = '" + invoice + "'", "An issued invoice cannot be changed");
        refused("UPDATE invoices SET due_date = due_date + 90 WHERE id = '" + invoice + "'", "cannot be changed");
        refused("UPDATE invoices SET status = 'DRAFT', invoice_number = NULL WHERE id = '" + invoice + "'", "cannot go back");
        refused("UPDATE invoices SET amount_paid = 99999 WHERE id = '" + invoice + "'", "ck_invoices_not_oversettled");
        refused("UPDATE invoices SET deleted_at = now() WHERE id = '" + invoice + "'", "ck_invoices_discarded");
        refused("UPDATE invoice_items SET unit_price = 1 WHERE invoice_id = '" + invoice + "'", "lines of an issued invoice");
        refused("DELETE FROM invoice_items WHERE invoice_id = '" + invoice + "'", "lines of an issued invoice");
        refused("INSERT INTO invoice_items (invoice_id, position, description, quantity, unit_price, line_subtotal, "
                + "discount_amount, tax_amount, line_total) VALUES ('" + invoice + "', 9, 'Slipped in', 1, 1, 1, 0, 0, 1)",
                "lines of an issued invoice");

        // A draft is not yet a financial record: its lines are free.
        assertThat(jdbc.update("UPDATE invoice_items SET description = 'Reworded' WHERE invoice_id = ?::uuid", draft)).isEqualTo(1);

        // And the records are exactly as they were.
        as(admin).get(INVOICES + "/" + invoice).then()
                .body("invoice.total", equalTo(1000.0f)).body("invoice.amountPaid", equalTo(600.0f))
                .body("invoice.outstanding", equalTo(400.0f)).body("items", hasSize(1));
        as(admin).get(PAYMENTS + "/" + payment).then()
                .body("payment.amount", equalTo(1000.0f)).body("payment.unallocated", equalTo(300.0f));
    }

    // ------------------------------------------------------------------ payment gateways

    @Test
    void aGatewayPaymentIsRecordedOnceHoweverOftenItIsReported() {
        String invoice = issued(customerId, 1000);
        UUID organization = adminAccount.organizationId();
        GatewayPayment reported = new GatewayPayment(organization, "razorpay", "pay_N4kq81", UUID.fromString(customerId),
                UUID.fromString(invoice), new BigDecimal("700.00"), null, "UPI", "pay_N4kq81");

        Receipt first = paymentIntake.record(reported);
        assertThat(first.duplicate()).isFalse();
        assertThat(first.paymentNumber()).matches("RCT-\\d{4}-000001");

        // The webhook is delivered again, and again.
        Receipt second = paymentIntake.record(reported);
        Receipt third = paymentIntake.record(reported);
        assertThat(second).isEqualTo(new Receipt(first.paymentId(), first.paymentNumber(), true));
        assertThat(third.duplicate()).isTrue();
        assertThat(count("payments")).isEqualTo(1);

        as(admin).get(PAYMENTS + "/" + first.paymentId()).then().statusCode(200)
                .body("source", equalTo("GATEWAY")).body("gateway", equalTo("razorpay"))
                .body("payment.reference", equalTo("pay_N4kq81")).body("payment.methodName", equalTo("UPI"))
                .body("recordedByName", equalTo("Payment gateway (razorpay)"))
                .body("payment.allocated", equalTo(700.0f));
        as(admin).get(INVOICES + "/" + invoice).then()
                .body("invoice.status", equalTo("PARTIALLY_PAID")).body("invoice.outstanding", equalTo(300.0f));

        // More than the invoice still needs: the money is taken all the same, and the rest waits.
        Receipt over = paymentIntake.record(new GatewayPayment(organization, "razorpay", "pay_N4kq99",
                UUID.fromString(customerId), UUID.fromString(invoice), new BigDecimal("500"), today, "UPI", null));
        as(admin).get(PAYMENTS + "/" + over.paymentId()).then()
                .body("payment.allocated", equalTo(300.0f)).body("payment.unallocated", equalTo(200.0f));
        as(admin).get(INVOICES + "/" + invoice).then().body("invoice.status", equalTo("PAID"));

        // The same gateway id for a different amount is not a repeat; something is wrong, so say so.
        assertThatThrownBy(() -> paymentIntake.record(new GatewayPayment(organization, "razorpay", "pay_N4kq81",
                UUID.fromString(customerId), null, new BigDecimal("701"), null, "UPI", null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> paymentIntake.record(new GatewayPayment(organization, "razorpay", "pay_other",
                UUID.fromString(customerId), null, new BigDecimal("10"), null, "NO_SUCH_METHOD", null)))
                .isInstanceOf(FieldValidationException.class);
        // Another gateway may use the same id for a payment of its own.
        assertThat(paymentIntake.record(new GatewayPayment(organization, "payu", "pay_N4kq81",
                UUID.fromString(customerId), null, new BigDecimal("10"), null, "CARD", null)).duplicate()).isFalse();
        assertThat(count("payments")).isEqualTo(3);
        // Beneath the idempotency keys, the table itself holds each gateway payment once.
        refused("INSERT INTO payments (organization_id, payment_number, customer_id, customer_name, received_on, amount, "
                + "method_id, method_name, source, gateway, gateway_payment_id, recorded_by_name, created_by, updated_by) "
                + "SELECT organization_id, 'RCT-X', customer_id, customer_name, received_on, amount, method_id, "
                + "method_name, source, gateway, gateway_payment_id, recorded_by_name, 'test', 'test' FROM payments "
                + "WHERE id = '" + first.paymentId() + "'", "uq_payments_gateway");
    }

    // ------------------------------------------------------------------ payment methods

    @Test
    void paymentMethodsAreConfiguredByEachOrganization() {
        as(finance).get(METHODS).then().statusCode(200)
                .body("code", contains("CASH", "UPI", "BANK_TRANSFER", "CHEQUE", "CARD"))
                .body("find { it.code == 'CHEQUE' }.requiresReference", equalTo(true))
                .body("find { it.code == 'CASH' }.requiresReference", equalTo(false));

        Response created = as(finance).body(Map.of("code", "WALLET", "name", "Wallet", "displayOrder", 9)).post(METHODS);
        created.then().statusCode(201).body("code", equalTo("WALLET")).body("active", equalTo(true))
                .body("requiresReference", equalTo(false));
        String wallet = created.path("id");
        as(finance).body(Map.of("code", "WALLET", "name", "Another")).post(METHODS).then().statusCode(400)
                .body("errors[0].field", equalTo("code"));
        as(finance).body(Map.of("code", "EWALLET", "name", " wallet ")).post(METHODS).then().statusCode(400)
                .body("errors[0].field", equalTo("name"));
        as(finance).body(Map.of("code", "wallet", "name", "Lower case")).post(METHODS).then().statusCode(400)
                .body("errors[0].field", equalTo("code"));

        // A payment keeps the name its method had when it was taken.
        String byWallet = pay(customerId, 100, wallet, Map.of()).path("payment.id");

        int version = created.path("version");
        Map<String, Object> off = Map.of("code", "WALLET", "name", "Mobile wallet", "requiresReference", true,
                "active", false, "displayOrder", 9, "version", version);
        as(finance).body(off).put(METHODS + "/" + wallet).then().statusCode(200)
                .body("name", equalTo("Mobile wallet")).body("active", equalTo(false)).body("code", equalTo("WALLET"));
        as(finance).body(off).put(METHODS + "/" + wallet).then().statusCode(409);
        as(finance).get(METHODS).then().body("code", hasSize(5));
        as(finance).queryParam("includeInactive", true).get(METHODS).then().body("code", hasItem("WALLET"));
        pay(customerId, 100, wallet, Map.of("reference", "W-1")).then().statusCode(400)
                .body("errors[0].field", equalTo("methodId"));
        as(finance).get(PAYMENTS + "/" + byWallet).then().body("payment.methodName", equalTo("Wallet"));

        // Removing every method leaves the list empty: the defaults are a starting point, given once.
        List<String> ids = as(finance).queryParam("includeInactive", true).get(METHODS).path("id");
        ids.forEach(id -> as(finance).delete(METHODS + "/" + id).then().statusCode(204));
        as(finance).queryParam("includeInactive", true).get(METHODS).then().statusCode(200).body("", empty());
        pay(customerId, 100, cash, Map.of()).then().statusCode(400).body("errors[0].field", equalTo("methodId"));
        as(finance).get(PAYMENTS + "/" + byWallet).then().statusCode(200).body("payment.methodName", equalTo("Wallet"));

        // Another organization has its own list and cannot touch this one.
        String elsewhere = tokenFor(accounts.activeUser(RoleCodes.FINANCE));
        as(elsewhere).get(METHODS).then().body("code", hasSize(5));
        as(elsewhere).delete(METHODS + "/" + wallet).then().statusCode(404);

        // Viewing finance does not include configuring it.
        String director = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.DIRECTOR));
        as(director).get(METHODS).then().statusCode(200);
        as(director).body(Map.of("code", "BARTER", "name", "Barter")).post(METHODS).then().statusCode(403);
    }

    // ------------------------------------------------------------------ who may do what

    @Test
    void financeIsLimitedByPermissionAndByOrganization() {
        String invoice = issued(customerId, 1000);
        String payment = pay(customerId, 200, cash, Map.of()).path("payment.id");
        Map<String, Object> newInvoice = Map.of("customerId", customerId, "items", List.of(line("Work", 1, 100, 0)));

        given().get(INVOICES).then().statusCode(401);
        given().get(PAYMENTS + "/" + payment).then().statusCode(401);

        // Sales sees none of it.
        String sales = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.SALES_EXECUTIVE));
        as(sales).get(INVOICES).then().statusCode(403);
        as(sales).get(INVOICES + "/" + invoice).then().statusCode(403);
        as(sales).get(PAYMENTS).then().statusCode(403);
        as(sales).get(FINANCE + "/ageing").then().statusCode(403);
        as(sales).get(FINANCE + "/summary").then().statusCode(403);
        as(sales).get(METHODS).then().statusCode(403);
        as(sales).body(newInvoice).post(INVOICES).then().statusCode(403);

        // A director reads and approves, but does not keep the books.
        String director = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.DIRECTOR));
        as(director).get(INVOICES + "/" + invoice).then().statusCode(200)
                .body("actions.recordPayment", equalTo(false)).body("actions.creditNote", equalTo(true))
                .body("actions.cancel", equalTo(true));
        as(director).get(FINANCE + "/ageing").then().statusCode(200);
        as(director).body(newInvoice).post(INVOICES).then().statusCode(403);
        as(director).body(Map.of("items", List.of(line("Work", 1, 100, 0)))).post(INVOICES + "/preview").then().statusCode(403);
        as(director).body(Map.of("customerId", customerId, "amount", 10, "methodId", cash)).post(PAYMENTS).then().statusCode(403);
        as(director).body(Map.of("amount", 100, "reason", "Goodwill")).post(INVOICES + "/" + invoice + "/credit-notes")
                .then().statusCode(201);

        // Another organization's finance team cannot see or use any of it.
        Account outsiderAccount = accounts.activeUser(RoleCodes.FINANCE);
        String outsider = tokenFor(outsiderAccount);
        String theirCash = as(outsider).get(METHODS).path("find { it.code == 'CASH' }.id");
        as(outsider).get(INVOICES + "/" + invoice).then().statusCode(404);
        as(outsider).get(PAYMENTS + "/" + payment).then().statusCode(404);
        as(outsider).get(INVOICES).then().body("totalItems", equalTo(0));
        as(outsider).queryParam("customerId", customerId).get(INVOICES + "/open").then().body("", empty());
        as(outsider).post(INVOICES + "/" + invoice + "/issue").then().statusCode(404);
        as(outsider).body(Map.of("reason", "x")).post(INVOICES + "/" + invoice + "/cancel").then().statusCode(404);
        as(outsider).body(Map.of("reason", "x")).post(PAYMENTS + "/" + payment + "/reverse").then().statusCode(404);
        as(outsider).body(newInvoice).post(INVOICES).then().statusCode(400).body("errors[0].field", equalTo("customerId"));
        as(outsider).body(Map.of("customerId", customerId, "amount", 10, "methodId", theirCash)).post(PAYMENTS)
                .then().statusCode(400).body("errors[0].field", equalTo("customerId"));
        as(outsider).get(FINANCE + "/ageing").then().body("customers", empty()).body("total", equalTo(0.0f));
        // Nor can this organization pay with the other one's payment method.
        pay(customerId, 10, theirCash, Map.of()).then().statusCode(400).body("errors[0].field", equalTo("methodId"));
    }

    @Test
    void everyFinancialRecordLeavesAnAuditTrail() {
        String invoice = issued(customerId, 1000);
        Response paid = pay(customerId, 1000, cash, Map.of("allocations", List.of(alloc(invoice, 400))));
        as(finance).body(Map.of("amount", 50, "reason", "Discount")).post(INVOICES + "/" + invoice + "/credit-notes");
        as(finance).body(Map.of("amount", 100, "methodId", cash, "reason", "Returned"))
                .post(PAYMENTS + "/" + paid.path("payment.id") + "/refunds");

        List<String> trail = jdbc.queryForList(
                "SELECT entity_type || ':' || action FROM audit_logs WHERE organization_id = ? AND module = 'finance'",
                String.class, adminAccount.organizationId());

        // Written by the audit module from the entities themselves; finance has no audit code of its own.
        assertThat(trail).contains("Invoice:CREATE", "Invoice:UPDATE", "Payment:CREATE", "Payment:UPDATE",
                "PaymentAllocation:CREATE", "CreditNote:CREATE", "Refund:CREATE");
        assertThat(jdbc.queryForObject(
                "SELECT entity_label FROM audit_logs WHERE organization_id = ? AND entity_type = 'Payment' AND action = 'CREATE'",
                String.class, adminAccount.organizationId())).isEqualTo(paid.<String>path("payment.paymentNumber"));
    }

    // ------------------------------------------------------------------ helpers

    private String customer(String token, String name, String phone) {
        return as(token).body(Map.of("type", "BUSINESS", "companyName", name, "phone", phone))
                .post(CUSTOMERS).then().statusCode(201).extract().path("id");
    }

    private static Map<String, Object> line(String description, Object quantity, Object unitPrice, Object taxPercent) {
        return line(description, quantity, unitPrice, taxPercent, Map.of());
    }

    private static Map<String, Object> line(
            String description, Object quantity, Object unitPrice, Object taxPercent, Map<String, Object> extra) {
        Map<String, Object> line = new HashMap<>(extra);
        line.put("description", description);
        line.put("quantity", quantity);
        line.put("unitPrice", unitPrice);
        line.put("taxPercent", taxPercent);
        return line;
    }

    /** A draft with one untaxed line for the amount. */
    private String draft(String customer, int amount) {
        return as(admin).body(Map.of("customerId", customer, "items", List.of(line("Work done", 1, amount, 0))))
                .post(INVOICES).then().statusCode(201).extract().path("invoice.id");
    }

    private Response issue(String invoiceId, LocalDate issueDate, LocalDate dueDate) {
        Map<String, Object> body = new HashMap<>();
        if (issueDate != null) {
            body.put("issueDate", issueDate.toString());
        }
        if (dueDate != null) {
            body.put("dueDate", dueDate.toString());
        }
        Response issued = as(admin).body(body).post(INVOICES + "/" + invoiceId + "/issue");
        issued.then().statusCode(200);
        return issued;
    }

    /** An invoice issued today for the amount, due in fifteen days. */
    private String issued(String customer, int amount) {
        return issue(draft(customer, amount), null, null).path("invoice.id");
    }

    private Response pay(String customer, Object amount, String methodId, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>(extra);
        body.put("customerId", customer);
        body.put("amount", amount);
        body.put("methodId", methodId);
        return as(admin).body(body).post(PAYMENTS);
    }

    private static Map<String, Object> alloc(String invoiceId, Object amount) {
        return Map.of("invoiceId", invoiceId, "amount", amount);
    }

    /** INV-2627-000001 gives INV-2627-000002. */
    private static String next(String number) {
        int dash = number.lastIndexOf('-');
        return number.substring(0, dash + 1) + "%06d".formatted(Integer.parseInt(number.substring(dash + 1)) + 1);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE organization_id = ?", Integer.class,
                adminAccount.organizationId());
    }

    private void refused(String sql, String because) {
        assertThatThrownBy(() -> jdbc.update(sql)).as(sql)
                .isInstanceOf(DataAccessException.class).hasMessageContaining(because);
    }

    /** Runs the same request from several threads, released together, and returns every response. */
    private static List<Response> atOnce(int times, Callable<Response> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(times);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Response>> pending = new ArrayList<>();
            for (int i = 0; i < times; i++) {
                pending.add(pool.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : pending) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }
}
