package com.pawanputra.bos.audit.api;

/**
 * Implemented by an {@link Audited} entity that is a part of something bigger, so that its changes
 * show up in the bigger thing's activity timeline. A customer's contact implements it to say "file
 * my activity under my customer": adding a contact then appears in the customer's history.
 *
 * <p>Only the timeline is redirected. The audit log still records the event against the entity
 * itself (the contact).
 */
public interface ActivityOwner {

    /** The {@code entity} name of the owner, as in its {@link Audited} annotation: {@code "Customer"}. */
    String activityOwnerType();

    /** The owner's id. */
    Object activityOwnerId();
}
