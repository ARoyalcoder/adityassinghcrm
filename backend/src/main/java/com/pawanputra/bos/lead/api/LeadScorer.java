package com.pawanputra.bos.lead.api;

/**
 * Turns what is known about a lead into a score with reasons.
 *
 * <p>This is the seam for other ways of scoring. Today there is one implementation, a fixed set of
 * rules. A scorer backed by a model would implement this same interface: it receives the same
 * {@link LeadFacts}, must return the same {@link LeadScore} (including the factors that explain
 * it), and is selected with {@code app.leads.scoring.engine}. Everything else (priority, health,
 * qualification status, storage, the API and the screens) is unaffected by which scorer runs.
 */
public interface LeadScorer {

    /** The name the scorer is selected by and recorded under, including its version: {@code rules-v1}. */
    String engine();

    /** Must be deterministic for the rules engine, and must never return a score without factors explaining it. */
    LeadScore score(LeadFacts facts);
}
