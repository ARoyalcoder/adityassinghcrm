package com.pawanputra.bos.lead.api;

import java.util.List;

/**
 * A lead's score with its explanation.
 *
 * @param score   0 to 100
 * @param total   the sum of the factors before it was limited to 0..100
 * @param factors why: every reason that added or removed points. A score without factors is not
 *                acceptable from any scorer, learned or not
 * @param engine  which scorer produced it, e.g. {@code rules-v1}
 */
public record LeadScore(int score, int total, List<Factor> factors, String engine) {

    public enum Category {
        /** What the business may be worth. */
        VALUE,
        /** What has, or has not, happened lately. */
        ENGAGEMENT,
        /** The answers to the qualification questions. */
        QUALIFICATION,
        /** What is known about the lead itself. */
        PROFILE
    }

    /**
     * One reason. {@code points} is positive or negative, never zero.
     *
     * @param code  stable identifier of the reason, e.g. {@code HIGH_VALUE} or {@code QUESTION:budget}
     * @param label the reason in words, ready to show: "High estimated value (₹5,00,000 or more)"
     */
    public record Factor(String code, Category category, String label, int points) {
    }

    public LeadScore {
        factors = List.copyOf(factors);
    }

    /** Builds a score from its factors: the sum, limited to 0..100. */
    public static LeadScore of(List<Factor> factors, String engine) {
        int total = factors.stream().mapToInt(Factor::points).sum();
        return new LeadScore(Math.max(0, Math.min(100, total)), total, factors, engine);
    }
}
