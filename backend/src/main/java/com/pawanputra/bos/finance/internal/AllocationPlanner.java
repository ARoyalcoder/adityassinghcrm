package com.pawanputra.bos.finance.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Decides which invoices a payment settles when the person recording it does not say. */
final class AllocationPlanner {

    private AllocationPlanner() {
    }

    /** An invoice that still has money owing. */
    record Open(UUID invoiceId, String invoiceNumber, LocalDate dueDate, BigDecimal outstanding) {
    }

    record Share(UUID invoiceId, BigDecimal amount) {
    }

    /**
     * Oldest debt first: the invoice due earliest is settled in full before the next one gets
     * anything. Whatever is left when the invoices run out stays unallocated.
     */
    static List<Share> oldestFirst(BigDecimal amount, List<Open> open) {
        List<Open> byAge = open.stream()
                .sorted(Comparator.comparing(Open::dueDate).thenComparing(Open::invoiceNumber))
                .toList();
        List<Share> shares = new ArrayList<>();
        BigDecimal left = amount;
        for (Open invoice : byAge) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal share = left.min(invoice.outstanding());
            if (share.signum() > 0) {
                shares.add(new Share(invoice.invoiceId(), share));
                left = left.subtract(share);
            }
        }
        return shares;
    }
}
