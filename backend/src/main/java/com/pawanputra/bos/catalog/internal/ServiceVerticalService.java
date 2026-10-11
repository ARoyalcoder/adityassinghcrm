package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading the verticals and changing how they are presented. There is no way to add or remove one. */
@Service
@Transactional(readOnly = true)
public class ServiceVerticalService {

    private final ServiceVerticalRepository repository;

    public ServiceVerticalService(ServiceVerticalRepository repository) {
        this.repository = repository;
    }

    /** What the rest of the product offers to users. */
    public List<ServiceVertical> listActive() {
        return repository.findAllByActiveTrueOrderByDisplayOrderAsc();
    }

    /** For administration: includes verticals that are switched off. */
    public List<ServiceVertical> listAll() {
        return repository.findAllByOrderByDisplayOrderAsc();
    }

    public ServiceVertical getByCode(String code) {
        return ServiceVerticalCode.fromCode(code)
                .flatMap(repository::findByCode)
                .orElseThrow(() -> new ResourceNotFoundException("Service vertical", code));
    }

    /** @param expectedVersion the version the editor loaded; a different current version means someone else saved first */
    @Transactional
    public ServiceVertical update(
            String code, String name, String description, int displayOrder, long expectedVersion) {
        ServiceVertical vertical = getByCode(code);
        requireVersion(vertical, expectedVersion);
        if (repository.existsByNameIgnoreCaseAndCodeNot(name, vertical.getCode())) {
            throw new ConflictException("Another vertical is already called '" + name + "'");
        }
        if (repository.existsByDisplayOrderAndCodeNot(displayOrder, vertical.getCode())) {
            throw new ConflictException("Another vertical already uses display order " + displayOrder);
        }
        vertical.setName(name);
        vertical.setDescription(description);
        vertical.setDisplayOrder(displayOrder);
        return repository.saveAndFlush(vertical);
    }

    /**
     * Switching a vertical off hides it from the rest of the product. Its categories and services
     * are kept as they are, so switching it back on restores everything.
     */
    @Transactional
    public ServiceVertical setActive(String code, boolean active) {
        ServiceVertical vertical = getByCode(code);
        vertical.setActive(active);
        return repository.saveAndFlush(vertical);
    }

    private static void requireVersion(ServiceVertical vertical, long expectedVersion) {
        if (vertical.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "This vertical was changed by someone else. Reload it and apply your changes again.");
        }
    }
}
