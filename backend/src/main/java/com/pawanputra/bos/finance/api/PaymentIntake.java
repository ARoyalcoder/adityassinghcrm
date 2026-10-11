package com.pawanputra.bos.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * How a payment gateway integration records money it has collected. No gateway is connected yet;
 * this is the door one will come in through.
 *
 * <p>A gateway reports the same payment more than once: webhooks are retried, and a webhook and a
 * redirect can both arrive. {@link #record} is safe to call every time. The first call for a given
 * {@code gateway} and {@code gatewayPaymentId} records the payment; every later call returns that
 * same payment and changes nothing.
 *
 * <pre>{@code
 * // In a future webhook controller, after verifying the gateway's signature:
 * Receipt receipt = paymentIntake.record(new GatewayPayment(
 *         organizationId, "razorpay", event.paymentId(), customerId, invoiceId,
 *         event.amount(), event.paidOn(), "UPI", event.paymentId()));
 * return ResponseEntity.ok().build();   // also for a duplicate: the gateway must stop retrying
 * }</pre>
 */
public interface PaymentIntake {

    /**
     * @param gateway          the gateway's name, lower-case: {@code "razorpay"}
     * @param gatewayPaymentId the gateway's own id for this payment; what makes a repeat a repeat
     * @param invoiceId        the invoice the customer was paying, if known. As much of the payment as
     *                         the invoice still needs is allocated to it; the rest stays unallocated
     * @param methodCode       the code of one of the organization's payment methods
     * @param reference        what to show as the payment's reference; may be {@code null}
     */
    record GatewayPayment(
            UUID organizationId, String gateway, String gatewayPaymentId, UUID customerId, UUID invoiceId,
            BigDecimal amount, LocalDate receivedOn, String methodCode, String reference) {
    }

    /** @param duplicate the gateway had already reported this payment; nothing was recorded this time */
    record Receipt(UUID paymentId, String paymentNumber, boolean duplicate) {
    }

    Receipt record(GatewayPayment payment);
}
