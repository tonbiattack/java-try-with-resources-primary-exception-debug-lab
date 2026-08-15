package com.example.tryresources;

import java.util.logging.Logger;

public final class InvoiceImportService {
    private static final Logger LOGGER = Logger.getLogger(InvoiceImportService.class.getName());

    private final InvoiceCsvParser parser;
    private final AuditTrailFactory auditTrailFactory;

    public InvoiceImportService(InvoiceCsvParser parser, AuditTrailFactory auditTrailFactory) {
        this.parser = parser;
        this.auditTrailFactory = auditTrailFactory;
    }

    public ImportResult importCsv(String csv) {
        try (AuditTrail auditTrail = auditTrailFactory.open()) {
            auditTrail.record("IMPORT_STARTED");
            parser.validate(csv);
            auditTrail.record("IMPORT_ACCEPTED");
            return ImportResult.successful();
        } catch (Exception failure) {
            LOGGER.warning(() -> "取込失敗を返します: primary="
                    + failure.getClass().getSimpleName()
                    + ", suppressed=" + failure.getSuppressed().length);
            return ImportResult.rejected(failure);
        }
    }
}
