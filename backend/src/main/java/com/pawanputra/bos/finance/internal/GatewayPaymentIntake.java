package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.finance.api.PaymentIntake;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The door for payment gateways. The gateway's own payment id is the idempotency key, in a scope
 * of the gateway's own, so the first report of a payment records it and every repeat finds it.
 * The unique index on {@code payments (organization_id, gateway, gateway_payment_id)} says the same
 * thing once more, in the one place nothing can get around.
 */
@Service
class GatewayPaymentIntake implements PaymentIntake {

    private final PaymentService payments;
    private final PaymentRepository paymentRows;
    private final Idempotency idempotency;

    GatewayPaymentIntake(PaymentService payments, PaymentRepository paymentRows, Idempotency idempotency) {
        this.payments = payments;
        this.paymentRows = paymentRows;
        this.idempotency = idempotency;
    }

    @Override
    @Transactional
    public Receipt record(GatewayPayment reported) {
        if (reported.gateway() == null || !reported.gateway().matches("[a-z][a-z0-9_-]{1,49}")) {
            throw new FieldValidationException("gateway", "must be the gateway's name in lower case");
        }
        if (reported.gatewayPaymentId() == null || reported.gatewayPaymentId().isBlank()
                || reported.gatewayPaymentId().length() > Idempotency.MAX_KEY_LENGTH) {
            throw new FieldValidationException("gatewayPaymentId", "is required");
        }
        if (reported.amount() == null || reported.amount().signum() <= 0) {
            throw new FieldValidationException("amount", "must be more than zero");
        }
        String scope = "gateway:" + reported.gateway();
        Optional<UUID> earlier = idempotency.claim(reported.organizationId(), scope, reported.gatewayPaymentId(),
                Idempotency.fingerprint(reported.customerId(), reported.amount()));
        if (earlier.isPresent()) {
            Payment recorded = paymentRows.findByIdAndOrganizationId(earlier.get(), reported.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment", earlier.get()));
            return new Receipt(recorded.getId(), recorded.getPaymentNumber(), true);
        }
        Payment payment = payments.recordFromGateway(reported);
        idempotency.complete(reported.organizationId(), scope, reported.gatewayPaymentId(), "Payment", payment.getId());
        return new Receipt(payment.getId(), payment.getPaymentNumber(), false);
    }
}
