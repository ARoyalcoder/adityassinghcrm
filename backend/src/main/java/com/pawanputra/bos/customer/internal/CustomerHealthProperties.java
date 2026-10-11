package com.pawanputra.bos.customer.internal;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The thresholds of the built-in customer health rules. They are configuration, not code, because
 * "how long without contact is too long" is a business decision that will be tuned.
 *
 * @param recentContactDays contact within this many days counts in the customer's favour
 * @param staleContactDays  no contact for more than this many days needs attention
 */
@Validated
@ConfigurationProperties("app.customers.health")
public record CustomerHealthProperties(@Min(1) int recentContactDays, @Min(1) int staleContactDays) {
}
