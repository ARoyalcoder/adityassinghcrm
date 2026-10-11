package com.pawanputra.bos.lead.internal;

import com.pawanputra.bos.lead.api.LeadStatus;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps stored scores true as time passes. A lead that nobody touches still changes: after three
 * quiet days it loses points and needs attention, and no event tells the system so. This job
 * reassesses every open lead on a schedule; closed leads are left as they were when they closed.
 */
@Component
public class LeadRescoringJob {

    private static final Logger log = LoggerFactory.getLogger(LeadRescoringJob.class);
    private static final int BATCH = 200;

    private final LeadRepository leads;
    private final LeadQualificationService qualification;

    public LeadRescoringJob(LeadRepository leads, LeadQualificationService qualification) {
        this.leads = leads;
        this.qualification = qualification;
    }

    /** @return how many leads ended up with a different assessment */
    @Scheduled(cron = "${app.leads.scoring.rescore-cron:0 15 * * * *}", zone = "UTC")
    public int rescoreOpenLeads() {
        int changed = 0;
        int failed = 0;
        int page = 0;
        Slice<Lead> slice;
        do {
            slice = leads.findAllByStatusOrderById(LeadStatus.OPEN, PageRequest.of(page++, BATCH));
            for (Lead lead : slice.getContent()) {
                UUID id = lead.getId();
                try {
                    // Each lead in its own transaction: one bad lead must not stop the rest.
                    changed += qualification.rescore(id) ? 1 : 0;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("Could not rescore lead {}", id, e);
                }
            }
        } while (slice.hasNext());
        if (changed > 0 || failed > 0) {
            log.info("Lead rescoring: {} changed, {} failed", changed, failed);
        }
        return changed;
    }
}
