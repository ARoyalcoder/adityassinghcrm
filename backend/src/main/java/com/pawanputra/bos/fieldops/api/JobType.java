package com.pawanputra.bos.fieldops.api;

/** What the technician goes to the site for. Both follow the same workflow. */
public enum JobType {
    /** Work to be done: an installation, a repair, a service call. */
    JOB,
    /** Going to look: a survey, an inspection, taking measurements. */
    SITE_VISIT
}
