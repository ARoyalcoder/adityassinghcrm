package com.pawanputra.bos.fieldops.internal;

import com.pawanputra.bos.fieldops.api.JobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface FieldJobRepository extends JpaRepository<FieldJob, UUID>, JpaSpecificationExecutor<FieldJob> {

    /** Always scope by organization: a job of another organization must look like it does not exist. */
    Optional<FieldJob> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query(value = "select nextval('field_job_number_seq')", nativeQuery = true)
    long nextJobNumber();

    /** A technician's jobs in the given statuses, scheduled in [from, to), earliest first. */
    List<FieldJob> findAllByTechnicianIdAndStatusInAndScheduledStartGreaterThanEqualAndScheduledStartLessThanOrderByScheduledStartAsc(
            UUID technicianId, Collection<JobStatus> statuses, Instant from, Instant to);

    /** A technician's finished jobs, most recently finished first. */
    List<FieldJob> findAllByTechnicianIdAndStatusInOrderByCompletedAtDesc(
            UUID technicianId, Collection<JobStatus> statuses, Pageable pageable);
}
