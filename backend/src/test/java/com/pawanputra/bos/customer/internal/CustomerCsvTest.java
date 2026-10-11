package com.pawanputra.bos.customer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawanputra.bos.customer.api.CustomerSource;
import com.pawanputra.bos.customer.api.CustomerStatus;
import com.pawanputra.bos.customer.api.CustomerType;
import com.pawanputra.bos.customer.internal.CustomerViews.Assignee;
import com.pawanputra.bos.customer.internal.CustomerViews.Summary;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerCsvTest {

    @Test
    void writesAHeaderAndOneLinePerCustomer() {
        Summary business = new Summary(UUID.randomUUID(), "CUS-001001", CustomerType.BUSINESS, "Acme Solar",
                "info@acme.example", "9876543210", CustomerStatus.ACTIVE, CustomerSource.REFERRAL,
                new Assignee(UUID.randomUUID(), "Asha Verma"), List.of("Builder", "VIP"),
                Instant.parse("2026-10-05T10:15:30Z"));
        Summary individual = new Summary(UUID.randomUUID(), "CUS-001002", CustomerType.INDIVIDUAL, "Ravi Kumar",
                null, null, CustomerStatus.PROSPECT, null, null, List.of(), Instant.parse("2026-10-06T00:00:00Z"));

        String csv = new String(CustomerCsv.write(List.of(business, individual)), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿"); // so Excel reads it as UTF-8
        assertThat(csv.substring(1).split("\r\n")).containsExactly(
                "Customer no.,Name,Type,Email,Phone,Status,Source,Assigned to,Tags,Created (UTC)",
                "CUS-001001,Acme Solar,BUSINESS,info@acme.example,9876543210,ACTIVE,REFERRAL,Asha Verma,Builder; VIP,2026-10-05 10:15",
                "CUS-001002,Ravi Kumar,INDIVIDUAL,,,PROSPECT,,,,2026-10-06 00:00");
    }

    @Test
    void quotesCellsThatContainCommasQuotesOrLineBreaks() {
        assertThat(CustomerCsv.cell("Verma, Gupta & Co")).isEqualTo("\"Verma, Gupta & Co\"");
        assertThat(CustomerCsv.cell("The \"Best\" Shop")).isEqualTo("\"The \"\"Best\"\" Shop\"");
        assertThat(CustomerCsv.cell("line one\nline two")).isEqualTo("\"line one\nline two\"");
        assertThat(CustomerCsv.cell("Plain name")).isEqualTo("Plain name");
        assertThat(CustomerCsv.cell(null)).isEmpty();
    }

    @Test
    void neutralisesCellsThatASpreadsheetWouldRunAsAFormula() {
        assertThat(CustomerCsv.cell("=HYPERLINK(\"http://evil.example\",\"click\")"))
                .startsWith("\"'=HYPERLINK");
        assertThat(CustomerCsv.cell("+91 98765 43210")).isEqualTo("'+91 98765 43210");
        assertThat(CustomerCsv.cell("-cmd")).isEqualTo("'-cmd");
        assertThat(CustomerCsv.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }
}
