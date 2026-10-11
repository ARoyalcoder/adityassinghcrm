package com.pawanputra.bos.finance.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ageing: what is owed, sorted by how long past its due date it is. The first band is money that is
 * not due yet; the others are set by the configured upper ends (30, 60, 90 gives 1-30, 31-60, 61-90
 * and over 90 days).
 */
public final class Ageing {

    private Ageing() {
    }

    /** @param fromDays first day past due in the band; {@code null} for "not due". @param toDays last day; {@code null} for no upper end */
    public record Bucket(String label, Integer fromDays, Integer toDays) {
    }

    /** One invoice with money owing. */
    record Item(UUID customerId, String customerName, LocalDate dueDate, BigDecimal outstanding) {
    }

    /**
     * @param amounts           owed in each band, in the order of {@link Report#buckets}
     * @param oldestDaysOverdue how far past due the customer's oldest invoice is; 0 if nothing is overdue
     */
    public record Row(UUID customerId, String customerName, List<BigDecimal> amounts, BigDecimal total,
            long oldestDaysOverdue, int invoices) {
    }

    /** @param customers who owes, largest debt first */
    public record Report(LocalDate asOf, List<Bucket> buckets, List<BigDecimal> totals, BigDecimal total,
            BigDecimal overdue, List<Row> customers) {
    }

    static List<Bucket> buckets(List<Integer> upperEnds) {
        List<Bucket> buckets = new ArrayList<>();
        buckets.add(new Bucket("Not due", null, 0));
        int from = 1;
        for (int upperEnd : upperEnds) {
            buckets.add(new Bucket(from + "-" + upperEnd + " days", from, upperEnd));
            from = upperEnd + 1;
        }
        buckets.add(new Bucket("Over " + (from - 1) + " days", from, null));
        return buckets;
    }

    /** Days past the due date; 0 or less means not due yet (an invoice due today is not overdue). */
    static long daysOverdue(LocalDate dueDate, LocalDate asOf) {
        return ChronoUnit.DAYS.between(dueDate, asOf);
    }

    /** The index of the band an invoice that many days past due falls in; 0 is "not due". */
    static int bucketIndex(long daysOverdue, List<Integer> upperEnds) {
        if (daysOverdue <= 0) {
            return 0;
        }
        for (int index = 0; index < upperEnds.size(); index++) {
            if (daysOverdue <= upperEnds.get(index)) {
                return index + 1;
            }
        }
        return upperEnds.size() + 1;
    }

    static Report report(LocalDate asOf, List<Integer> upperEnds, List<Item> items) {
        List<Bucket> buckets = buckets(upperEnds);
        Map<UUID, Tally> byCustomer = new LinkedHashMap<>();
        BigDecimal[] totals = zeros(buckets.size());
        for (Item item : items) {
            long days = daysOverdue(item.dueDate(), asOf);
            int bucket = bucketIndex(days, upperEnds);
            Tally tally = byCustomer.computeIfAbsent(item.customerId(),
                    id -> new Tally(item.customerName(), zeros(buckets.size())));
            tally.amounts[bucket] = tally.amounts[bucket].add(item.outstanding());
            tally.oldest = Math.max(tally.oldest, days);
            tally.invoices++;
            totals[bucket] = totals[bucket].add(item.outstanding());
        }
        List<Row> rows = byCustomer.entrySet().stream()
                .map(entry -> new Row(entry.getKey(), entry.getValue().name, List.of(entry.getValue().amounts),
                        sum(entry.getValue().amounts, 0), entry.getValue().oldest, entry.getValue().invoices))
                .sorted(Comparator.comparing(Row::total).reversed().thenComparing(Row::customerName))
                .toList();
        return new Report(asOf, buckets, List.of(totals), sum(totals, 0), sum(totals, 1), rows);
    }

    private static final class Tally {
        private final String name;
        private final BigDecimal[] amounts;
        private long oldest;
        private int invoices;

        private Tally(String name, BigDecimal[] amounts) {
            this.name = name;
            this.amounts = amounts;
        }
    }

    private static BigDecimal[] zeros(int size) {
        BigDecimal[] amounts = new BigDecimal[size];
        java.util.Arrays.fill(amounts, Money.ZERO);
        return amounts;
    }

    private static BigDecimal sum(BigDecimal[] amounts, int from) {
        BigDecimal sum = Money.ZERO;
        for (int index = from; index < amounts.length; index++) {
            sum = sum.add(amounts[index]);
        }
        return sum;
    }
}
