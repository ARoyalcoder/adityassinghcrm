package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.audit.api.Audited;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Adds to what the customer owes on an invoice: extra work, an under-billed quantity, a late fee. */
@Entity
@Audited(module = "finance", entity = "DebitNote", label = "noteNumber")
@Table(name = "debit_notes")
public class DebitNote extends AdjustmentNote {

    protected DebitNote() {
        // for JPA
    }

    public DebitNote(
            UUID organizationId, String noteNumber, UUID invoiceId, UUID customerId, LocalDate noteDate,
            BigDecimal amount, String reason, String issuedByName) {
        super(organizationId, noteNumber, invoiceId, customerId, noteDate, amount, reason, issuedByName);
    }
}
