package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.customer.internal.CustomerViews.Summary;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** The customer list as a CSV file that opens correctly in Excel and Google Sheets. */
final class CustomerCsv {

    private static final String[] HEADER = {
        "Customer no.", "Name", "Type", "Email", "Phone", "Status", "Source", "Assigned to", "Tags", "Created (UTC)"
    };
    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
    /** Tells Excel the file is UTF-8, so names in Hindi or with accents are not garbled. */
    private static final String BYTE_ORDER_MARK = "﻿";

    private CustomerCsv() {
    }

    static byte[] write(List<Summary> customers) {
        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK);
        row(csv, HEADER);
        for (Summary customer : customers) {
            row(csv,
                    customer.customerNumber(),
                    customer.displayName(),
                    customer.type().name(),
                    customer.email(),
                    customer.phone(),
                    customer.status().name(),
                    customer.source() == null ? null : customer.source().name(),
                    customer.assignedTo() == null ? null : customer.assignedTo().name(),
                    String.join("; ", customer.tags()),
                    CREATED.format(customer.createdAt()));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void row(StringBuilder csv, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(cell(cells[i]));
        }
        csv.append("\r\n");
    }

    static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value;
        // A cell starting with = + - @ is run as a formula by spreadsheets, and customer names are
        // typed by anyone. A leading apostrophe makes the spreadsheet treat it as plain text.
        if ("=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
