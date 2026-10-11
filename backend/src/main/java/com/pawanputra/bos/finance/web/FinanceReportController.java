package com.pawanputra.bos.finance.web;

import com.pawanputra.bos.finance.internal.Ageing;
import com.pawanputra.bos.finance.internal.FinanceReportService;
import com.pawanputra.bos.finance.internal.FinanceReportService.Settings;
import com.pawanputra.bos.finance.internal.FinanceReportService.Summary;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What is owed and how old it is. Read from the invoices and payments as they stand right now. */
@RestController
@RequestMapping(ApiPaths.V1 + "/finance")
@Tag(name = "Finance reports")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class FinanceReportController {

    private final FinanceReportService reports;

    public FinanceReportController(FinanceReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/summary")
    @PreAuthorize(FinanceWeb.CAN_VIEW)
    @Operation(summary = "Outstanding and overdue amounts, what falls due in the next seven days, unallocated "
            + "payments, and what was invoiced and received this month. Requires FINANCE_VIEW.")
    public Summary summary() {
        return reports.summary(CurrentUser.require().organizationId());
    }

    @GetMapping("/ageing")
    @PreAuthorize(FinanceWeb.CAN_VIEW)
    @Operation(summary = "Ageing as of today: what each customer owes, by how long past its due date it is "
            + "(not due, then the configured bands). Requires FINANCE_VIEW.")
    public Ageing.Report ageing() {
        return reports.ageing(CurrentUser.require().organizationId());
    }

    @GetMapping("/settings")
    @PreAuthorize(FinanceWeb.CAN_VIEW)
    @Operation(summary = "The defaults a new invoice starts from (payment terms, tax rate, currency) and the date "
            + "the books take as today. Requires FINANCE_VIEW.")
    public Settings settings() {
        return reports.settings();
    }
}
