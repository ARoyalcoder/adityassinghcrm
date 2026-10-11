package com.pawanputra.bos.lead.internal;

import com.pawanputra.bos.lead.api.LeadFacts;
import com.pawanputra.bos.lead.api.LeadHealth;
import com.pawanputra.bos.lead.api.LeadPriority;
import com.pawanputra.bos.lead.api.LeadScore;
import com.pawanputra.bos.lead.api.QualificationStatus;
import java.time.Instant;
import java.util.List;

/**
 * Everything that is concluded about a lead at one moment: its score (from a scorer) and the three
 * things derived from it and from the facts by fixed rules. Each conclusion carries its reason.
 *
 * <p>Priority, health and qualification status are decided here, not by the scorer, so they stay
 * the same simple rules whichever scorer produced the score.
 */
public record LeadAssessment(
        LeadScore score,
        LeadPriority priority,
        String priorityReason,
        LeadHealth health,
        String healthReason,
        QualificationStatus qualificationStatus,
        String qualificationReason,
        Instant computedAt) {

    /**
     * @param requiredQuestions how many active questions are required
     * @param requiredAnswered  how many of those the lead has answered
     */
    static LeadAssessment of(
            LeadFacts facts, LeadScore score, int requiredQuestions, int requiredAnswered,
            LeadScoringProperties settings) {

        // ---- qualification status: about the questions only
        List<LeadFacts.Answer> ruledOutBy = facts.answers().stream().filter(LeadFacts.Answer::disqualifies).toList();
        QualificationStatus status;
        String statusReason;
        if (!ruledOutBy.isEmpty()) {
            status = QualificationStatus.DISQUALIFIED;
            LeadFacts.Answer first = ruledOutBy.getFirst();
            statusReason = "Ruled out by the answer to \"" + first.questionLabel() + "\": " + first.optionLabel();
        } else if (facts.answers().isEmpty()) {
            status = QualificationStatus.NOT_STARTED;
            statusReason = "No qualification question has been answered yet";
        } else if (requiredAnswered < requiredQuestions) {
            status = QualificationStatus.IN_PROGRESS;
            statusReason = requiredAnswered + " of " + requiredQuestions + " required questions answered";
        } else {
            status = QualificationStatus.QUALIFIED;
            statusReason = requiredQuestions == 0
                    ? "No question is required, and no answer rules the lead out"
                    : "All " + requiredQuestions + " required questions answered, and no answer rules the lead out";
        }

        // ---- priority: from the score, unless the lead is ruled out
        LeadPriority priority;
        String priorityReason;
        if (status == QualificationStatus.DISQUALIFIED) {
            priority = LeadPriority.LOW;
            priorityReason = "The lead is disqualified";
        } else if (score.score() >= settings.highPriorityScore()) {
            priority = LeadPriority.HIGH;
            priorityReason = "Score " + score.score() + " is " + settings.highPriorityScore() + " or more";
        } else if (score.score() >= settings.mediumPriorityScore()) {
            priority = LeadPriority.MEDIUM;
            priorityReason = "Score " + score.score() + " is between " + settings.mediumPriorityScore() + " and "
                    + (settings.highPriorityScore() - 1);
        } else {
            priority = LeadPriority.LOW;
            priorityReason = "Score " + score.score() + " is below " + settings.mediumPriorityScore();
        }

        // ---- health: how long the lead has gone without anyone doing anything about it
        Instant lastTouch = facts.lastActivityAt() != null ? facts.lastActivityAt() : facts.createdAt();
        long silentDays = RuleBasedLeadScorer.daysBetween(lastTouch, facts.asOf());
        String silence = facts.lastActivityAt() != null
                ? "No activity for " + RuleBasedLeadScorer.days(silentDays)
                : "No activity since the lead was created " + RuleBasedLeadScorer.days(silentDays) + " ago";
        LeadHealth health;
        String healthReason;
        if (silentDays >= settings.coldAfterDays()) {
            health = LeadHealth.AT_RISK;
            healthReason = silence + " (" + settings.coldAfterDays() + " or more puts a lead at risk)";
        } else if (silentDays >= settings.inactiveAfterDays()) {
            health = LeadHealth.NEEDS_ATTENTION;
            healthReason = silence + " (" + settings.inactiveAfterDays() + " or more needs a follow-up)";
        } else {
            health = LeadHealth.GOOD;
            healthReason = facts.lastActivityAt() != null
                    ? "Last activity was " + (silentDays == 0 ? "today" : silentDays == 1 ? "yesterday" : silentDays + " days ago")
                    : "The lead is new";
        }

        return new LeadAssessment(score, priority, priorityReason, health, healthReason, status, statusReason, facts.asOf());
    }
}
