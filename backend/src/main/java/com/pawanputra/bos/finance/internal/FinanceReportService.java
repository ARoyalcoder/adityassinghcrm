package com.pawanputra.bos.finance.internal;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What is owed, to date. Everything here is read straight from the invoices and payments as they
 * stand at this moment; nothing is stored, so the figures cannot drift from the records.
 */
@Service
@Transactional(readOnly = true)
public class FinanceReportService {

    /** What an invoice still has owing, in SQL. Must say the same as {@link Invoice#getOutstanding()}. */
    private static final String OUTSTANDING = "(total + debit_total - credit_total - amount_paid)";
    private static final String OPEN_INVOICES = "organization_id = ? AND status IN ('ISSUED', 'PARTIALLY_PAID') "
            + "AND deleted_at IS NULL";

    private final JdbcTemplate jdbc;
    private final FinanceContext context;

    FinanceReportService(JdbcTemplate jdbc, FinanceContext context) {
        this.jdbc = jdbc;
        this.context = context;
    }

    /**
     * @param outstanding         owed on all issued invoices
     * @param overdue             the part of it that is past its due date
     * @param dueSoon             the part of it that falls due within the next seven days
     * @param unallocatedPayments received, and neither put against an invoice nor refunded
     * @param invoicedThisMonth   total of the invoices dated this month, cancelled ones left out
     * @param receivedThisMonth   payments dated this month, reversed ones left out
     */
    public record Summary(
            BigDecimal outstanding, BigDecimal overdue, BigDecimal dueSoon, long openInvoices, long overdueInvoices,
            long draftInvoices, BigDecimal unallocatedPayments, BigDecimal invoicedThisMonth,
            BigDecimal receivedThisMonth, String currency, LocalDate asOf) {
    }

    /** The defaults a new invoice starts from, and the date the server takes as today. */
    public record Settings(String currency, int defaultPaymentTermsDays, BigDecimal defaultTaxPercent, LocalDate today) {
    }

    public Settings settings() {
        FinanceProperties properties = context.properties();
        return new Settings(properties.currency(), properties.defaultPaymentTermsDays(),
                properties.defaultTaxPercent(), context.today());
    }

    public Summary summary(UUID organizationId) {
        LocalDate today = context.today();
        Date day = Date.valueOf(today);
        Date monthStart = Date.valueOf(today.withDayOfMonth(1));
        Date nextMonth = Date.valueOf(today.withDayOfMonth(1).plusMonths(1));
        return jdbc.queryForObject("""
                SELECT (SELECT coalesce(sum(%1$s), 0) FROM invoices WHERE %2$s),
                       (SELECT coalesce(sum(%1$s), 0) FROM invoices WHERE %2$s AND due_date < ?),
                       (SELECT coalesce(sum(%1$s), 0) FROM invoices WHERE %2$s AND due_date >= ? AND due_date < ?),
                       (SELECT count(*) FROM invoices WHERE %2$s),
                       (SELECT count(*) FROM invoices WHERE %2$s AND due_date < ?),
                       (SELECT count(*) FROM invoices WHERE organization_id = ? AND status = 'DRAFT' AND deleted_at IS NULL),
                       (SELECT coalesce(sum(amount - allocated_amount - refunded_amount), 0) FROM payments
                         WHERE organization_id = ? AND status = 'RECEIVED'),
                       (SELECT coalesce(sum(total), 0) FROM invoices
                         WHERE organization_id = ? AND status NOT IN ('DRAFT', 'CANCELLED')
                           AND issue_date >= ? AND issue_date < ?),
                       (SELECT coalesce(sum(amount), 0) FROM payments
                         WHERE organization_id = ? AND status = 'RECEIVED' AND received_on >= ? AND received_on < ?)
                """.formatted(OUTSTANDING, OPEN_INVOICES),
                // An empty sum comes back as a bare 0; every amount leaves here with its two decimals.
                (row, index) -> new Summary(
                        Money.of(row.getBigDecimal(1)), Money.of(row.getBigDecimal(2)), Money.of(row.getBigDecimal(3)),
                        row.getLong(4), row.getLong(5), row.getLong(6), Money.of(row.getBigDecimal(7)),
                        Money.of(row.getBigDecimal(8)), Money.of(row.getBigDecimal(9)),
                        context.properties().currency(), today),
                organizationId,
                organizationId, day,
                organizationId, day, Date.valueOf(today.plusDays(7)),
                organizationId,
                organizationId, day,
                organizationId,
                organizationId,
                organizationId, monthStart, nextMonth,
                organizationId, monthStart, nextMonth);
    }

    /** Who owes what, by how long it has been due. As of today. */
    public Ageing.Report ageing(UUID organizationId) {
        List<Ageing.Item> open = jdbc.query(
                "SELECT customer_id, customer_name, due_date, " + OUTSTANDING + " FROM invoices WHERE " + OPEN_INVOICES,
                (row, index) -> new Ageing.Item(row.getObject(1, UUID.class), row.getString(2),
                        row.getObject(3, LocalDate.class), row.getBigDecimal(4)),
                organizationId);
        return Ageing.report(context.today(), context.properties().ageingBucketDays(), open);
    }
}
