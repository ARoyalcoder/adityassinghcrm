package com.pawanputra.bos.quotation.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationItemRepository extends JpaRepository<QuotationItem, UUID> {

    List<QuotationItem> findByVersionIdOrderByItemOrderAsc(UUID versionId);
}
