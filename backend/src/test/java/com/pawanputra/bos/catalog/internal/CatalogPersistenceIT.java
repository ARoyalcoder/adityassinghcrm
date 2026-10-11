package com.pawanputra.bos.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CatalogPersistenceIT extends AbstractIntegrationTest {

    @Autowired ServiceVerticalRepository verticals;
    @Autowired ServiceCategoryRepository categories;
    @Autowired ServiceOfferingRepository services;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    void everyEnumConstantHasASeededActiveVertical() {
        assertThat(verticals.findAllByActiveTrueOrderByDisplayOrderAsc())
                .extracting(ServiceVertical::getCode)
                .containsExactly(ServiceVerticalCode.values());
        for (ServiceVerticalCode code : ServiceVerticalCode.values()) {
            ServiceVertical vertical = verticals.findByCode(code).orElseThrow();
            assertThat(vertical.getId()).isNotNull();
            assertThat(vertical.getCreatedAt()).isNotNull();
        }
    }

    @Test
    void persistsCategoryAndServiceUnderAVertical() {
        ServiceCategory category = categories.save(new ServiceCategory(solar(), "ROOFTOP", "Rooftop systems"));
        ServiceOffering service = new ServiceOffering(category, "SOLAR_ROOFTOP_3KW", "3 kW rooftop installation");
        service.setDescription("Supply and installation of a 3 kW on-grid rooftop plant");
        services.saveAndFlush(service);
        em.clear();

        ServiceOffering loaded = services.findByCode("SOLAR_ROOFTOP_3KW").orElseThrow();

        assertThat(loaded.getCategory().getCode()).isEqualTo("ROOFTOP");
        assertThat(loaded.getCategory().getVertical().getCode()).isEqualTo(ServiceVerticalCode.SOLAR);
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getCreatedBy()).isEqualTo("system");
        assertThat(categories.findAllByVerticalCodeOrderByDisplayOrderAscNameAsc(ServiceVerticalCode.SOLAR))
                .extracting(ServiceCategory::getCode).contains("ROOFTOP");
        assertThat(services.findAllByCategoryIdOrderByDisplayOrderAscNameAsc(category.getId())).hasSize(1);
    }

    @Test
    void categoryCodeIsUniqueWithinAVerticalOnly() {
        ServiceVertical itSupport = verticals.findByCode(ServiceVerticalCode.IT_SUPPORT).orElseThrow();
        categories.saveAndFlush(new ServiceCategory(solar(), "MAINTENANCE", "Maintenance"));
        categories.saveAndFlush(new ServiceCategory(itSupport, "MAINTENANCE", "Maintenance")); // other vertical: fine

        assertThatThrownBy(() -> categories.saveAndFlush(new ServiceCategory(solar(), "MAINTENANCE", "AMC")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_service_categories_vertical_code");
    }

    @Test
    void categoryNameIsUniqueWithinAVerticalIgnoringCase() {
        categories.saveAndFlush(new ServiceCategory(solar(), "MAINTENANCE", "Maintenance"));

        assertThatThrownBy(() -> categories.saveAndFlush(new ServiceCategory(solar(), "AMC", "MAINTENANCE")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_service_categories_vertical_name");
    }

    @Test
    void serviceCodeIsUniqueAcrossTheWholeCatalog() {
        ServiceVertical cctv = verticals.findByCode(ServiceVerticalCode.CCTV_SECURITY).orElseThrow();
        ServiceCategory solarCategory = categories.save(new ServiceCategory(solar(), "INSTALL", "Installation"));
        ServiceCategory cctvCategory = categories.save(new ServiceCategory(cctv, "INSTALL", "Installation"));
        services.saveAndFlush(new ServiceOffering(solarCategory, "SITE_SURVEY", "Site survey"));

        assertThatThrownBy(() -> services.saveAndFlush(
                new ServiceOffering(cctvCategory, "SITE_SURVEY", "CCTV site survey")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_services_code");
    }

    @Test
    void softDeletedServiceIsHiddenButKeptAndFreesItsCode() {
        ServiceCategory category = categories.save(new ServiceCategory(solar(), "INSTALL", "Installation"));
        ServiceOffering service = services.saveAndFlush(new ServiceOffering(category, "SITE_SURVEY", "Site survey"));

        service.markDeleted(Instant.now());
        services.saveAndFlush(service);
        em.clear();

        assertThat(services.findById(service.getId())).isEmpty();
        assertThat(services.findByCode("SITE_SURVEY")).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM services WHERE id = ? AND deleted_at IS NOT NULL", Long.class, service.getId()))
                .isEqualTo(1);

        ServiceCategory reloaded = categories.findById(category.getId()).orElseThrow();
        services.saveAndFlush(new ServiceOffering(reloaded, "SITE_SURVEY", "Site survey"));
    }

    @Test
    void categoryMustBelongToAnExistingVertical() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO service_categories (vertical_id, code, name, created_by, updated_by) "
                        + "VALUES (gen_random_uuid(), 'ORPHAN', 'Orphan', 'test', 'test')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_service_categories_vertical");
    }

    @Test
    void staleUpdateIsRejectedByTheVersionColumn() {
        ServiceCategory category = categories.saveAndFlush(new ServiceCategory(solar(), "INSTALL", "Installation"));
        // Someone else changes the row behind this session's back.
        jdbc.update("UPDATE service_categories SET version = version + 1 WHERE id = ?", category.getId());

        category.setName("Installation services");

        assertThatThrownBy(() -> categories.saveAndFlush(category))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    private ServiceVertical solar() {
        return verticals.findByCode(ServiceVerticalCode.SOLAR).orElseThrow();
    }
}
