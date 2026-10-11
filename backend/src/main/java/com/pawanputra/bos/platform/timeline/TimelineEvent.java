package com.pawanputra.bos.platform.timeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * One thing that happened, as shown on a timeline.
 *
 * <p>A timeline event is a <b>view</b> of a record that lives somewhere else (a call log entry, an
 * invoice, an audit row). It is built on demand by the module that owns the record and is never
 * stored: there is no timeline table. That is what keeps a timeline from duplicating records or
 * disagreeing with them.
 *
 * @param id          unique across all sources and stable between requests: {@code "<source>:<record id>"}.
 *                    The same record must always produce the same id, so merging can drop repeats
 * @param type        what happened, from {@link TimelineEventTypes} (or a new code defined by the module)
 * @param occurredAt  when it happened in the business sense (when the call took place, not when it was typed in)
 * @param title       a few words in the past tense: "Quotation sent"
 * @param description the specifics: "QT-0042 for 3 kW rooftop, ₹1,85,000". May be {@code null}
 * @param actor       who did it, as a display name. May be {@code null}
 * @param reference   the record behind the event, so the UI can link to it. May be {@code null}
 * @param details     a few extra facts to show as "label: value". May be {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimelineEvent(
        String id,
        String type,
        TimelineCategory category,
        Instant occurredAt,
        String title,
        String description,
        String actor,
        Reference reference,
        Map<String, String> details) {

    /**
     * @param entity what kind of record: "Quotation", "Invoice", "Interaction"
     * @param id     its id
     */
    public record Reference(String entity, String id) {
    }

    /** For the common case of an event with no reference and no extra details. */
    public static TimelineEvent of(
            String id, String type, TimelineCategory category, Instant occurredAt, String title,
            String description, String actor) {
        return new TimelineEvent(id, type, category, occurredAt, title, description, actor, null, null);
    }
}
