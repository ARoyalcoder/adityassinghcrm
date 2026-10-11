package com.pawanputra.bos.identity.api;

import java.util.Set;

/**
 * Every permission the application knows, named {@code MODULE_ACTION}. Use these constants wherever a
 * permission is checked so a typo is a compile error:
 *
 * <pre>{@code @PreAuthorize("hasAuthority('" + Permissions.CUSTOMER_VIEW + "')")}</pre>
 *
 * <p>This list and the {@code permissions} table must match; {@code PermissionCatalogIT} fails if
 * they drift. To add a permission: add the constant here, add it to {@link #ALL}, and insert it
 * (plus its grant to {@code SUPER_ADMIN}) in a new Flyway migration.
 */
public final class Permissions {

    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_DELETE = "USER_DELETE";

    public static final String ROLE_VIEW = "ROLE_VIEW";
    public static final String ROLE_UPDATE = "ROLE_UPDATE";

    public static final String ORGANIZATION_VIEW = "ORGANIZATION_VIEW";
    public static final String ORGANIZATION_UPDATE = "ORGANIZATION_UPDATE";

    public static final String BRANCH_VIEW = "BRANCH_VIEW";
    public static final String BRANCH_CREATE = "BRANCH_CREATE";
    public static final String BRANCH_UPDATE = "BRANCH_UPDATE";
    public static final String BRANCH_DELETE = "BRANCH_DELETE";

    public static final String CATALOG_VIEW = "CATALOG_VIEW";
    public static final String CATALOG_CREATE = "CATALOG_CREATE";
    public static final String CATALOG_UPDATE = "CATALOG_UPDATE";
    public static final String CATALOG_DELETE = "CATALOG_DELETE";

    public static final String CUSTOMER_VIEW = "CUSTOMER_VIEW";
    public static final String CUSTOMER_CREATE = "CUSTOMER_CREATE";
    public static final String CUSTOMER_UPDATE = "CUSTOMER_UPDATE";
    public static final String CUSTOMER_DELETE = "CUSTOMER_DELETE";
    public static final String CUSTOMER_EXPORT = "CUSTOMER_EXPORT";

    public static final String LEAD_VIEW = "LEAD_VIEW";
    public static final String LEAD_CREATE = "LEAD_CREATE";
    public static final String LEAD_UPDATE = "LEAD_UPDATE";
    public static final String LEAD_DELETE = "LEAD_DELETE";
    /** Defining the qualification questions and what their answers are worth. */
    public static final String LEAD_CONFIGURE = "LEAD_CONFIGURE";

    public static final String PROJECT_VIEW = "PROJECT_VIEW";
    public static final String PROJECT_CREATE = "PROJECT_CREATE";
    public static final String PROJECT_UPDATE = "PROJECT_UPDATE";
    public static final String PROJECT_DELETE = "PROJECT_DELETE";
    /** Defining the project templates of the services. */
    public static final String PROJECT_CONFIGURE = "PROJECT_CONFIGURE";

    /** Carrying out the field jobs assigned to you (and seeing only those). */
    public static final String FIELD_JOB_WORK = "FIELD_JOB_WORK";
    public static final String FIELD_JOB_VIEW = "FIELD_JOB_VIEW";
    /** Creating, scheduling, assigning and cancelling field jobs. */
    public static final String FIELD_JOB_MANAGE = "FIELD_JOB_MANAGE";

    public static final String FINANCE_VIEW = "FINANCE_VIEW";
    /** Creating and issuing invoices, recording and allocating payments. */
    public static final String FINANCE_MANAGE = "FINANCE_MANAGE";
    /**
     * Everything that takes money back off the books: credit and debit notes, refunds, cancelling an
     * invoice, releasing an allocation, reversing a payment.
     */
    public static final String FINANCE_APPROVE = "FINANCE_APPROVE";
    /** Defining the payment methods. */
    public static final String FINANCE_CONFIGURE = "FINANCE_CONFIGURE";

    public static final String AUDIT_VIEW = "AUDIT_VIEW";

    public static final String ACTIVITY_VIEW = "ACTIVITY_VIEW";
    public static final String ACTIVITY_CREATE = "ACTIVITY_CREATE";
    public static final String ACTIVITY_UPDATE = "ACTIVITY_UPDATE";
    public static final String ACTIVITY_DELETE = "ACTIVITY_DELETE";

    public static final String OPPORTUNITY_VIEW = "OPPORTUNITY_VIEW";
    public static final String OPPORTUNITY_CREATE = "OPPORTUNITY_CREATE";
    public static final String OPPORTUNITY_UPDATE = "OPPORTUNITY_UPDATE";
    public static final String OPPORTUNITY_DELETE = "OPPORTUNITY_DELETE";
    public static final String OPPORTUNITY_CONFIGURE = "OPPORTUNITY_CONFIGURE";

    public static final String QUOTATION_VIEW = "QUOTATION_VIEW";
    public static final String QUOTATION_CREATE = "QUOTATION_CREATE";
    public static final String QUOTATION_UPDATE = "QUOTATION_UPDATE";
    public static final String QUOTATION_APPROVE = "QUOTATION_APPROVE";
    public static final String QUOTATION_DELETE = "QUOTATION_DELETE";

    public static final String ORDER_VIEW = "ORDER_VIEW";
    public static final String ORDER_CREATE = "ORDER_CREATE";
    public static final String ORDER_UPDATE = "ORDER_UPDATE";
    public static final String ORDER_CANCEL = "ORDER_CANCEL";

    public static final String PROOF_OF_WORK_VIEW = "PROOF_OF_WORK_VIEW";
    public static final String PROOF_OF_WORK_CREATE = "PROOF_OF_WORK_CREATE";
    public static final String PROOF_OF_WORK_VERIFY = "PROOF_OF_WORK_VERIFY";
    public static final String PROOF_OF_WORK_DELETE = "PROOF_OF_WORK_DELETE";

    public static final Set<String> ALL = Set.of(
            USER_VIEW, USER_CREATE, USER_UPDATE, USER_DELETE,
            ROLE_VIEW, ROLE_UPDATE,
            ORGANIZATION_VIEW, ORGANIZATION_UPDATE,
            BRANCH_VIEW, BRANCH_CREATE, BRANCH_UPDATE, BRANCH_DELETE,
            CATALOG_VIEW, CATALOG_CREATE, CATALOG_UPDATE, CATALOG_DELETE,
            CUSTOMER_VIEW, CUSTOMER_CREATE, CUSTOMER_UPDATE, CUSTOMER_DELETE, CUSTOMER_EXPORT,
            LEAD_VIEW, LEAD_CREATE, LEAD_UPDATE, LEAD_DELETE, LEAD_CONFIGURE,
            PROJECT_VIEW, PROJECT_CREATE, PROJECT_UPDATE, PROJECT_DELETE, PROJECT_CONFIGURE,
            FIELD_JOB_WORK, FIELD_JOB_VIEW, FIELD_JOB_MANAGE,
            FINANCE_VIEW, FINANCE_MANAGE, FINANCE_APPROVE, FINANCE_CONFIGURE,
            AUDIT_VIEW,
            ACTIVITY_VIEW, ACTIVITY_CREATE, ACTIVITY_UPDATE, ACTIVITY_DELETE,
            OPPORTUNITY_VIEW, OPPORTUNITY_CREATE, OPPORTUNITY_UPDATE, OPPORTUNITY_DELETE, OPPORTUNITY_CONFIGURE,
            QUOTATION_VIEW, QUOTATION_CREATE, QUOTATION_UPDATE, QUOTATION_APPROVE, QUOTATION_DELETE,
            ORDER_VIEW, ORDER_CREATE, ORDER_UPDATE, ORDER_CANCEL,
            PROOF_OF_WORK_VIEW, PROOF_OF_WORK_CREATE, PROOF_OF_WORK_VERIFY, PROOF_OF_WORK_DELETE);

    private Permissions() {
    }
}
