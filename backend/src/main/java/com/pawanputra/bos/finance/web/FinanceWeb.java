package com.pawanputra.bos.finance.web;

import com.pawanputra.bos.finance.internal.FinanceContext.Actor;
import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.security.CurrentUser;
import org.springframework.http.ResponseEntity;

/** What the finance controllers share. */
final class FinanceWeb {

    static final String CAN_VIEW = "hasAuthority('" + Permissions.FINANCE_VIEW + "')";
    static final String CAN_MANAGE = "hasAuthority('" + Permissions.FINANCE_MANAGE + "')";
    static final String CAN_APPROVE = "hasAuthority('" + Permissions.FINANCE_APPROVE + "')";
    static final String CAN_CONFIGURE = "hasAuthority('" + Permissions.FINANCE_CONFIGURE + "')";

    /**
     * Sent by a client that wants a request to take effect once however often it is delivered: a
     * value it makes up (a UUID) and sends again, unchanged, with every retry of that request.
     */
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /** Set to {@code true} on a response that repeats the result of an earlier request with the same key. */
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";
    static final String IDEMPOTENCY_NOTE = " Send an Idempotency-Key header to make a retry safe: a repeat with the "
            + "same key returns the first result (with Idempotent-Replayed: true) instead of recording again.";

    private static final int MAX_KEY_LENGTH = 200;

    private FinanceWeb() {
    }

    static Actor actor() {
        CurrentUser user = CurrentUser.require();
        return new Actor(user.id(), user.organizationId(), user.email(),
                user.hasPermission(Permissions.FINANCE_MANAGE), user.hasPermission(Permissions.FINANCE_APPROVE));
    }

    /** The key as sent, or {@code null} if the client sent none. */
    static String idempotencyKey(String header) {
        String key = trimToNull(header);
        if (key != null && key.length() > MAX_KEY_LENGTH) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST,
                    IDEMPOTENCY_KEY + " must be at most " + MAX_KEY_LENGTH + " characters");
        }
        return key;
    }

    static ResponseEntity.BodyBuilder replayed(ResponseEntity.BodyBuilder response, boolean replayed) {
        return replayed ? response.header(IDEMPOTENT_REPLAYED, "true") : response;
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
