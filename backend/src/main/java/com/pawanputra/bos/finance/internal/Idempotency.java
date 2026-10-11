package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ConflictException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Makes a request take effect once, however many times it arrives. Used for anything that moves
 * money: a double click, a retry after a timeout or a gateway re-sending a webhook must not record
 * a second payment.
 *
 * <p>Inside one transaction the caller does this:
 *
 * <pre>{@code
 * Optional<UUID> earlier = idempotency.claim(organizationId, "payments.record", key, fingerprint);
 * if (earlier.isPresent()) {
 *     return load(earlier.get());          // a repeat: answer with what the first request created
 * }
 * Payment payment = ...;                   // the first time: do the work
 * idempotency.complete(organizationId, "payments.record", key, "Payment", payment.getId());
 * }</pre>
 *
 * <p>The key row and the work commit or roll back together. If a second request with the same key
 * arrives while the first is still running, its {@code claim} waits on the row until the first
 * finishes: then it sees the result (the first committed) or claims the key itself (the first
 * failed). A {@code null} key means the caller did not ask for this protection; both methods then
 * do nothing.
 */
@Component
class Idempotency {

    static final int MAX_KEY_LENGTH = 200;

    private final JdbcTemplate jdbc;

    Idempotency(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param scope       what kind of request this is; a key means something only within its scope
     * @param fingerprint of what is being asked for, from {@link #fingerprint}
     * @return the id of what the first request with this key created; empty if this is the first
     * @throws BusinessRuleException if the key was used before for a different request
     */
    Optional<UUID> claim(UUID organizationId, String scope, String key, String fingerprint) {
        if (key == null) {
            return Optional.empty();
        }
        int claimed = jdbc.update("""
                INSERT INTO finance_idempotency_keys (organization_id, scope, idempotency_key, request_hash)
                VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, organizationId, scope, key, fingerprint);
        if (claimed == 1) {
            return Optional.empty();
        }
        return jdbc.query(
                "SELECT request_hash, resource_id FROM finance_idempotency_keys "
                        + "WHERE organization_id = ? AND scope = ? AND idempotency_key = ?",
                row -> {
                    if (!row.next() || row.getObject(2, UUID.class) == null) {
                        throw new ConflictException(
                                "A request with this Idempotency-Key is still being processed. Try again in a moment.");
                    }
                    if (!row.getString(1).equals(fingerprint)) {
                        throw new BusinessRuleException("This Idempotency-Key was already used for a different "
                                + "request. Use a new key for a new request.");
                    }
                    return Optional.of(row.getObject(2, UUID.class));
                }, organizationId, scope, key);
    }

    /** Records what the request created, so a repeat can be answered with it. */
    void complete(UUID organizationId, String scope, String key, String resourceType, UUID resourceId) {
        if (key == null) {
            return;
        }
        jdbc.update("UPDATE finance_idempotency_keys SET resource_type = ?, resource_id = ? "
                + "WHERE organization_id = ? AND scope = ? AND idempotency_key = ?",
                resourceType, resourceId, organizationId, scope, key);
    }

    /**
     * SHA-256 over the parts of a request that make it what it is. Amounts are compared by value,
     * so 100 and 100.00 are the same request.
     */
    static String fingerprint(Object... parts) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : parts) {
            canonical.append(part instanceof BigDecimal amount ? amount.stripTrailingZeros().toPlainString() : part)
                    .append('\u001f');
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
