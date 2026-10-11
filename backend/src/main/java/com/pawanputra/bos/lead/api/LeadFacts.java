package com.pawanputra.bos.lead.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything a scorer may know about a lead, as plain values. A scorer is given this and nothing
 * else: it cannot reach the database, so the same facts always give the same score.
 *
 * @param asOf            the moment the lead is judged at: now for an open lead, the moment it was
 *                        closed for a won or lost one
 * @param lastActivityAt  the latest logged activity; {@code null} if none was ever logged
 * @param answers         the qualification questions answered so far
 */
public record LeadFacts(
        UUID leadId,
        UUID serviceId,
        LeadStatus status,
        BigDecimal estimatedValue,
        boolean hasPhone,
        boolean hasEmail,
        Instant createdAt,
        Instant lastActivityAt,
        int activityCount,
        List<Answer> answers,
        Instant asOf) {

    /**
     * One answered qualification question.
     *
     * @param points       what the chosen option is worth, as configured by the administrator
     * @param disqualifies whether the chosen option rules the lead out
     */
    public record Answer(
            String questionKey, String questionLabel, String optionValue, String optionLabel, int points,
            boolean disqualifies) {
    }

    public LeadFacts {
        answers = List.copyOf(answers);
    }
}
