package com.pawanputra.bos.quotation.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Clean architectural PDF generation service for Quotations.
 * Produces standard compliant PDF documents without external native dependencies.
 */
@Component
public class QuotationPdfRenderer {

    /**
     * Render the quotation version details into a standards-compliant PDF byte array.
     */
    public byte[] render(
            Quotation quotation,
            QuotationVersion version,
            String organizationName,
            String organizationAddress) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // Write standard PDF structure
            StringBuilder sb = new StringBuilder();
            sb.append("%PDF-1.4\n");
            sb.append("%\u00E2\u00E3\u00CF\u00D3\n"); // binary marker

            int obj1 = sb.length();
            sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

            int obj2 = sb.length();
            sb.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

            int obj3 = sb.length();
            sb.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n");

            int obj4 = sb.length();
            sb.append("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

            // Page content stream
            StringBuilder stream = new StringBuilder();
            stream.append("BT\n");
            stream.append("/F1 18 Tf\n");
            stream.append("50 740 Td\n");
            stream.append("(").append(escapePdfText(organizationName != null ? organizationName : "PAWAN PUTRA BUSINESS OS")).append(") Tj\n");

            stream.append("/F1 10 Tf\n");
            stream.append("0 -18 Td\n");
            stream.append("(").append(escapePdfText(organizationAddress != null ? organizationAddress : "Professional Services & Engineering")).append(") Tj\n");

            stream.append("/F1 16 Tf\n");
            stream.append("0 -36 Td\n");
            stream.append("(QUOTATION: ").append(escapePdfText(quotation.getQuotationNumber())).append(" - Version ").append(version.getVersionNumber()).append(") Tj\n");

            stream.append("/F1 10 Tf\n");
            stream.append("0 -20 Td\n");
            stream.append("(Title: ").append(escapePdfText(quotation.getTitle())).append(") Tj\n");

            if (quotation.getCustomerName() != null) {
                stream.append("0 -15 Td\n");
                stream.append("(Customer: ").append(escapePdfText(quotation.getCustomerName())).append(") Tj\n");
            } else if (quotation.getLeadName() != null) {
                stream.append("0 -15 Td\n");
                stream.append("(Lead: ").append(escapePdfText(quotation.getLeadName())).append(") Tj\n");
            }

            stream.append("0 -15 Td\n");
            stream.append("(Status: ").append(quotation.getStatus().name()).append(") Tj\n");

            if (version.getValidUntil() != null) {
                stream.append("0 -15 Td\n");
                stream.append("(Valid Until: ").append(version.getValidUntil().toString()).append(") Tj\n");
            }

            // Items table header
            stream.append("0 -30 Td\n");
            stream.append("/F1 11 Tf\n");
            stream.append("(ITEM DETAILS) Tj\n");
            stream.append("0 -15 Td\n");
            stream.append("/F1 9 Tf\n");
            stream.append("(---------------------------------------------------------------------------------------------------------------------) Tj\n");

            for (QuotationItem item : version.getItems()) {
                stream.append("0 -15 Td\n");
                String line = String.format("%d. %s  |  Qty: %s  |  Unit: INR %s  |  Subtotal: INR %s  |  Total: INR %s",
                        item.getItemOrder(),
                        item.getServiceName(),
                        item.getQuantity().toPlainString(),
                        item.getUnitPrice().toPlainString(),
                        item.getSubtotal().toPlainString(),
                        item.getTotalAmount().toPlainString());
                stream.append("(").append(escapePdfText(line)).append(") Tj\n");
            }

            stream.append("0 -15 Td\n");
            stream.append("(---------------------------------------------------------------------------------------------------------------------) Tj\n");

            // Totals
            stream.append("0 -25 Td\n");
            stream.append("/F1 10 Tf\n");
            stream.append("(Subtotal: INR ").append(version.getSubtotal().toPlainString()).append(") Tj\n");

            if (version.getDiscountAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                stream.append("0 -15 Td\n");
                stream.append("(Discount: INR ").append(version.getDiscountAmount().toPlainString())
                        .append(" \\(").append(version.getDiscountPercentage()).append("%\\)) Tj\n");
            }

            if (version.getTaxAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                stream.append("0 -15 Td\n");
                stream.append("(Tax/GST: INR ").append(version.getTaxAmount().toPlainString())
                        .append(" \\(").append(version.getTaxPercentage()).append("%\\)) Tj\n");
            }

            stream.append("0 -20 Td\n");
            stream.append("/F1 13 Tf\n");
            stream.append("(Final Amount: INR ").append(version.getFinalAmount().toPlainString()).append(") Tj\n");

            // Terms
            if (version.getTerms() != null && !version.getTerms().isBlank()) {
                stream.append("0 -30 Td\n");
                stream.append("/F1 10 Tf\n");
                stream.append("(TERMS & CONDITIONS:) Tj\n");
                stream.append("0 -14 Td\n");
                stream.append("/F1 8 Tf\n");
                stream.append("(").append(escapePdfText(version.getTerms())).append(") Tj\n");
            }

            stream.append("ET\n");

            byte[] streamBytes = stream.toString().getBytes(StandardCharsets.ISO_8859_1);
            int obj5 = sb.length();
            sb.append("5 0 obj\n<< /Length ").append(streamBytes.length).append(" >>\nstream\n");
            baos.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
            baos.write(streamBytes);

            StringBuilder trailer = new StringBuilder();
            trailer.append("\nendstream\nendobj\n");

            int xrefOffset = baos.size() + trailer.length();
            trailer.append("xref\n0 6\n");
            trailer.append("0000000000 65535 f \n");
            trailer.append(String.format("%010d 00000 n \n", obj1));
            trailer.append(String.format("%010d 00000 n \n", obj2));
            trailer.append(String.format("%010d 00000 n \n", obj3));
            trailer.append(String.format("%010d 00000 n \n", obj4));
            trailer.append(String.format("%010d 00000 n \n", obj5));
            trailer.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n");
            trailer.append(xrefOffset).append("\n%%EOF\n");

            baos.write(trailer.toString().getBytes(StandardCharsets.US_ASCII));
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate PDF document", e);
        }
    }

    private String escapePdfText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("\r", " ")
                   .replace("\n", " ");
    }
}
