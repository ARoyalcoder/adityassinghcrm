package com.pawanputra.bos.order.api;

public enum OrderStatus {
    CONFIRMED,
    IN_PROGRESS,
    PARTIALLY_DELIVERED,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        if (this == next) {
            return true;
        }
        return switch (this) {
            case CONFIRMED -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == PARTIALLY_DELIVERED || next == COMPLETED || next == CANCELLED;
            case PARTIALLY_DELIVERED -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false; // Terminal states
        };
    }
}
