package com.pawanputra.bos.lead.api;

/** Whether a lead is being worked: how long it has gone without activity. */
public enum LeadHealth {
    /** Touched recently enough. */
    GOOD,
    /** Nothing has happened for a while; someone should follow up. */
    NEEDS_ATTENTION,
    /** Nothing has happened for so long that the lead is going cold. */
    AT_RISK
}
