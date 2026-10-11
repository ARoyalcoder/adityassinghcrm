package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.catalog.api.ServiceDirectory;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CatalogServiceDirectory implements ServiceDirectory {

    private final ServiceOfferingRepository services;

    CatalogServiceDirectory(ServiceOfferingRepository services) {
        this.services = services;
    }

    @Override
    public Optional<ServiceSummary> find(UUID serviceId) {
        return services.findWithCategoryById(serviceId).map(CatalogServiceDirectory::summary);
    }

    @Override
    public Map<UUID, ServiceSummary> findAll(Collection<UUID> serviceIds) {
        Map<UUID, ServiceSummary> found = new LinkedHashMap<>();
        for (ServiceOffering service : services.findAllWithCategoryByIdIn(serviceIds)) {
            found.put(service.getId(), summary(service));
        }
        return found;
    }

    private static ServiceSummary summary(ServiceOffering service) {
        return new ServiceSummary(service.getId(), service.getCode(), service.getName(),
                service.getCategory().getVertical().getName(), service.isActive());
    }
}
