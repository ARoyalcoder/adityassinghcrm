package com.pawanputra.bos.finance.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How the company keeps its books. Configuration, not code: the fiscal year, the payment terms and
 * the ageing bands are the accountant's decisions.
 *
 * @param timeZone               the business day: issue dates, due dates and "overdue" are decided here
 * @param fiscalYearStartMonth   1 to 12; document numbers restart when the fiscal year does
 * @param defaultPaymentTermsDays offered on a new invoice: days from issue until it is due
 * @param defaultTaxPercent      offered on a new invoice line
 * @param ageingBucketDays       upper ends of the ageing bands in days past due, ascending: 30, 60, 90
 *                               gives 1-30, 31-60, 61-90 and over 90
 * @param defaultPaymentMethods  given to an organization the first time it needs payment methods
 */
@Validated
@ConfigurationProperties("app.finance")
public record FinanceProperties(
        @NotNull ZoneId timeZone,
        @Min(1) @Max(12) int fiscalYearStartMonth,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @Min(0) @Max(365) int defaultPaymentTermsDays,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal defaultTaxPercent,
        @NotEmpty List<@Min(1) Integer> ageingBucketDays,
        @NotNull @Valid Numbering numbering,
        @NotNull List<@Valid DefaultMethod> defaultPaymentMethods,
        @NotNull @Valid Health health) {

    /** The letters each kind of document number starts with: {@code INV-2627-000001}. */
    public record Numbering(
            @NotBlank @Pattern(regexp = PREFIX) String invoicePrefix,
            @NotBlank @Pattern(regexp = PREFIX) String paymentPrefix,
            @NotBlank @Pattern(regexp = PREFIX) String creditNotePrefix,
            @NotBlank @Pattern(regexp = PREFIX) String debitNotePrefix,
            @NotBlank @Pattern(regexp = PREFIX) String refundPrefix) {

        private static final String PREFIX = "[A-Z]{2,5}";
    }

    public record DefaultMethod(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,29}") String code,
            @NotBlank String name,
            boolean requiresReference) {
    }

    /** @param criticalAfterDays an invoice overdue for longer than this puts the customer at risk */
    public record Health(@Min(1) int criticalAfterDays) {
    }
}
