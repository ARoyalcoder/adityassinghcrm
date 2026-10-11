package com.pawanputra.bos.fieldops.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobStatusTest {

    @Test
    void aJobMovesThroughTheWorkflowOneStepAtATime() {
        List<JobStatus> path = new ArrayList<>(List.of(JobStatus.ASSIGNED));
        while (path.getLast().next().isPresent()) {
            path.add(path.getLast().next().get());
        }

        assertThat(path).containsExactly(
                JobStatus.ASSIGNED, JobStatus.ACCEPTED, JobStatus.EN_ROUTE, JobStatus.ARRIVED, JobStatus.IN_PROGRESS,
                JobStatus.COMPLETED, JobStatus.CUSTOMER_CONFIRMED);
    }

    @Test
    void nothingFollowsAnUnassignedConfirmedOrCancelledJob() {
        assertThat(JobStatus.UNASSIGNED.next()).isEmpty(); // it must be assigned first, which is not a technician's step
        assertThat(JobStatus.CUSTOMER_CONFIRMED.next()).isEmpty();
        assertThat(JobStatus.CANCELLED.next()).isEmpty();
    }

    @Test
    void onlyConfirmedAndCancelledJobsAreClosed() {
        assertThat(JobStatus.values()).filteredOn(JobStatus::isClosed)
                .containsExactly(JobStatus.CUSTOMER_CONFIRMED, JobStatus.CANCELLED);
        // Completed is not closed: the customer has still to confirm.
        assertThat(JobStatus.COMPLETED.isClosed()).isFalse();
        assertThat(JobStatus.COMPLETED.isDone()).isTrue();
    }

    @Test
    void aTechnicianCanBeChangedOnlyUntilSomeoneSetsOff() {
        assertThat(JobStatus.values()).filteredOn(JobStatus::canBeReassigned)
                .containsExactly(JobStatus.UNASSIGNED, JobStatus.ASSIGNED, JobStatus.ACCEPTED);
    }

    @Test
    void workIsRecordedFromAcceptingTheJobUntilTheCustomerConfirms() {
        assertThat(JobStatus.values()).filteredOn(JobStatus::acceptsWork).containsExactly(
                JobStatus.ACCEPTED, JobStatus.EN_ROUTE, JobStatus.ARRIVED, JobStatus.IN_PROGRESS, JobStatus.COMPLETED);
    }
}
