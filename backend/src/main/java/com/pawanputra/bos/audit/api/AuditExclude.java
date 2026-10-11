package com.pawanputra.bos.audit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Keeps a field of an {@link Audited} entity out of the audit log. Use it for:
 * <ul>
 *   <li>secrets, which must never be copied anywhere (password hashes, tokens);</li>
 *   <li>bookkeeping that changes constantly and means nothing to an auditor (last-login time,
 *       failed-attempt counters).</li>
 * </ul>
 * A change that touches only excluded fields is not recorded at all.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditExclude {
}
