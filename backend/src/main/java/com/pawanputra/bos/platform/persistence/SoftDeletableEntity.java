package com.pawanputra.bos.platform.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;

/**
 * Base class for data that must stay in the database after a user "deletes" it, because other
 * records or history still refer to it. Tables must additionally declare {@code deleted_at}.
 *
 * <p>Every concrete entity must also carry {@code @SQLRestriction(SoftDeletableEntity.NOT_DELETED)}
 * so deleted rows disappear from all queries and associations. Delete by calling
 * {@link #markDeleted(Instant)}; never call {@code repository.delete(...)} on these entities.
 */
@MappedSuperclass
public abstract class SoftDeletableEntity extends AuditableEntity {

    public static final String NOT_DELETED = "deleted_at is null";

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Idempotent: the first deletion time is kept. */
    public void markDeleted(Instant when) {
        if (deletedAt == null) {
            deletedAt = when;
        }
    }
}
