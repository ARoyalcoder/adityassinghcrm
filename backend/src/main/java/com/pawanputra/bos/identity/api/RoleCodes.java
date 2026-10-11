package com.pawanputra.bos.identity.api;

import java.util.Set;

/**
 * Codes of the system roles seeded by migration. Prefer permission checks; check a role directly
 * only when the rule really is about the role itself.
 */
public final class RoleCodes {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String DIRECTOR = "DIRECTOR";
    public static final String SALES_MANAGER = "SALES_MANAGER";
    public static final String SALES_EXECUTIVE = "SALES_EXECUTIVE";
    public static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    public static final String DESIGNER = "DESIGNER";
    public static final String TECHNICIAN = "TECHNICIAN";
    public static final String FINANCE = "FINANCE";
    public static final String HR = "HR";
    public static final String SUPPORT_AGENT = "SUPPORT_AGENT";
    public static final String VENDOR = "VENDOR";
    public static final String PARTNER = "PARTNER";

    public static final Set<String> ALL = Set.of(
            SUPER_ADMIN, ADMIN, DIRECTOR, SALES_MANAGER, SALES_EXECUTIVE, PROJECT_MANAGER, DESIGNER,
            TECHNICIAN, FINANCE, HR, SUPPORT_AGENT, VENDOR, PARTNER);

    private RoleCodes() {
    }
}
