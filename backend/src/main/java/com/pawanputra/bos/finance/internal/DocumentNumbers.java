package com.pawanputra.bos.finance.internal;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hands out document numbers: {@code INV-2627-000001}, consecutive within an organization, a kind
 * of document and a fiscal year.
 *
 * <p>The counter row is advanced inside the caller's transaction and stays locked until it ends.
 * Two documents being numbered at once therefore queue up, and a transaction that fails gives its
 * number back, so the series has no gaps and no repeats. (A database sequence would be faster and
 * would have both.) Call it as late as possible, once nothing else can fail.
 */
@Component
class DocumentNumbers {

    private final JdbcTemplate jdbc;
    private final FinanceProperties properties;

    DocumentNumbers(JdbcTemplate jdbc, FinanceProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /** @param date the date on the document; decides which fiscal year's series the number comes from */
    String next(UUID organizationId, DocumentType type, LocalDate date) {
        int fiscalYear = FiscalYear.startingIn(date, properties.fiscalYearStartMonth());
        Integer number = jdbc.queryForObject("""
                INSERT INTO finance_document_sequences (organization_id, document_type, fiscal_year, last_number)
                VALUES (?, ?, ?, 1)
                ON CONFLICT (organization_id, document_type, fiscal_year)
                DO UPDATE SET last_number = finance_document_sequences.last_number + 1
                RETURNING last_number
                """, Integer.class, organizationId, type.name(), fiscalYear);
        return "%s-%s-%06d".formatted(
                prefix(type), FiscalYear.label(fiscalYear, properties.fiscalYearStartMonth()), number);
    }

    private String prefix(DocumentType type) {
        FinanceProperties.Numbering numbering = properties.numbering();
        return switch (type) {
            case INVOICE -> numbering.invoicePrefix();
            case PAYMENT -> numbering.paymentPrefix();
            case CREDIT_NOTE -> numbering.creditNotePrefix();
            case DEBIT_NOTE -> numbering.debitNotePrefix();
            case REFUND -> numbering.refundPrefix();
        };
    }
}
