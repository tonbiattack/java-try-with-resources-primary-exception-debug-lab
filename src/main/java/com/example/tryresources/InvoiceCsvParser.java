package com.example.tryresources;

public final class InvoiceCsvParser {
    public void validate(String csv) throws InvalidInvoiceException {
        String[] lines = csv.split("\\R", -1);
        if (lines.length != 2 || !"invoiceId,amount".equals(lines[0])) {
            throw new InvalidInvoiceException("請求CSVのヘッダーが不正です");
        }

        String[] columns = lines[1].split(",", -1);
        if (columns.length != 2 || columns[0].isBlank() || columns[1].isBlank()) {
            throw new InvalidInvoiceException("請求CSVのinvoiceIdまたはamountが不足しています");
        }
    }
}
