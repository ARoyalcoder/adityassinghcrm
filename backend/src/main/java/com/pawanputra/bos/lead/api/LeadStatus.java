package com.pawanputra.bos.lead.api;

/** Where a lead stands. A closed lead (won or lost) is no longer rescored as time passes. */
public enum LeadStatus {
    OPEN,
    WON,
    LOST;

    public boolean isClosed() {
        return this != OPEN;
    }
}
