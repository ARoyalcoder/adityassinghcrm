package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.audit.api.Audited;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Reduces what the customer owes on an invoice: a return, a price correction, a goodwill discount. */
@Entity
@Audited(module = "finance", entity = "CreditNote", label = "noteNumber")
@Table(name = "credit_notes")
public class CreditNote extends AdjustmentNote {

    protected CreditNote() {
        // for JPA
    }

    public CreditNote(
            UUID organizationId, String noteNumber, UUID invoiceId, UUID customerId, LocalDate noteDate,
            BigDecimal amount, String reason, String issuedByName) {
        super(organizationId, noteNumber, invoiceId, customerId, noteDate, amount, reason, issuedByName);
    }
}
