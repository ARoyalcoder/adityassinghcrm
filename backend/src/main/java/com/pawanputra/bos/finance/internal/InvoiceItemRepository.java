package com.pawanputra.bos.finance.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, UUID> {

    List<InvoiceItem> findAllByInvoiceIdOrderByPositionAsc(UUID invoiceId);

    /** Only ever reaches the lines of a draft: the database refuses it for an issued invoice. */
    @Modifying(flushAutomatically = true)
    @Query("delete from InvoiceItem i where i.invoiceId = :invoiceId")
    void deleteAllByInvoiceId(@Param("invoiceId") UUID invoiceId);
}
