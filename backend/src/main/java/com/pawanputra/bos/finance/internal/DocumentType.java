package com.pawanputra.bos.finance.internal;

/** The kinds of numbered document. Persisted by name in {@code finance_document_sequences}. */
enum DocumentType {
    INVOICE, PAYMENT, CREDIT_NOTE, DEBIT_NOTE, REFUND
}
