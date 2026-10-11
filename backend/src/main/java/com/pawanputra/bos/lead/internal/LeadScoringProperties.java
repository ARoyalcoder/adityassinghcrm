package com.pawanputra.bos.lead.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The numbers behind the built-in scoring rules. They are configuration, not code: what counts as
 * a high value, and how many days of silence is too many, are business decisions that get tuned.
 * (The points for qualification answers are not here: administrators set those per question.)
 *
 * @param engine               which {@code LeadScorer} to use; {@code rules-v1} is the only one today
 * @param highValueAmount      an estimated value of at least this (INR) is "high"
 * @param mediumValueAmount    an estimated value of at least this, below high, is "medium"
 * @param recentActivityDays   an activity within this many days counts in the lead's favour
 * @param inactiveAfterDays    this many days without activity counts against it and needs attention
 * @param coldAfterDays        this many days without activity counts against it more, and puts it at risk
 * @param completeContactPoints given when both a phone number and an email address are known
 * @param highPriorityScore    a score of at least this is high priority
 * @param mediumPriorityScore  a score of at least this, below high, is medium priority
 */
@Validated
@ConfigurationProperties("app.leads.scoring")
public record LeadScoringProperties(
        @NotBlank String engine,
        @NotNull BigDecimal highValueAmount,
        @Min(0) @Max(100) int highValuePoints,
        @NotNull BigDecimal mediumValueAmount,
        @Min(0) @Max(100) int mediumValuePoints,
        @Min(1) int recentActivityDays,
        @Min(0) @Max(100) int recentActivityPoints,
        @Min(1) int inactiveAfterDays,
        @Min(-100) @Max(0) int inactivePoints,
        @Min(1) int coldAfterDays,
        @Min(-100) @Max(0) int coldPoints,
        @Min(0) @Max(100) int completeContactPoints,
        @Min(1) @Max(100) int highPriorityScore,
        @Min(1) @Max(100) int mediumPriorityScore) {

    public LeadScoringProperties {
        if (highValueAmount != null && mediumValueAmount != null && mediumValueAmount.compareTo(highValueAmount) >= 0) {
            throw new IllegalArgumentException("app.leads.scoring.medium-value-amount must be below high-value-amount");
        }
        if (coldAfterDays <= inactiveAfterDays) {
            throw new IllegalArgumentException("app.leads.scoring.cold-after-days must be more than inactive-after-days");
        }
        if (recentActivityDays > inactiveAfterDays) {
            throw new IllegalArgumentException(
                    "app.leads.scoring.recent-activity-days must not be more than inactive-after-days");
        }
        if (mediumPriorityScore >= highPriorityScore) {
            throw new IllegalArgumentException(
                    "app.leads.scoring.medium-priority-score must be below high-priority-score");
        }
    }
}
