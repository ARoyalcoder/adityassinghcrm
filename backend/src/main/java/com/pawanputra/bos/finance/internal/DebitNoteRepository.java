package com.pawanputra.bos.finance.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebitNoteRepository extends JpaRepository<DebitNote, UUID> {

    List<DebitNote> findAllByInvoiceIdOrderByCreatedAtAscIdAsc(UUID invoiceId);
}
