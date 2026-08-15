package com.example.tryresources;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InvoiceImportServiceTest {
    public static void main(String[] args) {
        InvoiceImportServiceTest test = new InvoiceImportServiceTest();
        test.壊れた請求CSVでは本体例外を主失敗として返しclose例外をsuppressedに保持する();
        System.out.println("ALL TESTS PASSED");
    }

    void 壊れた請求CSVでは本体例外を主失敗として返しclose例外をsuppressedに保持する() {
        FailingAuditTrail auditTrail = new FailingAuditTrail();
        InvoiceImportService service = new InvoiceImportService(
                new InvoiceCsvParser(),
                () -> auditTrail
        );

        ImportResult actual = service.importCsv("invoiceId,amount\nINV-001,");

        assertEquals(FailureCode.INVALID_INVOICE, actual.failureCode());
        assertEquals(InvalidInvoiceException.class, actual.primaryFailureType());
        assertEquals(List.of(AuditCloseException.class), actual.suppressedFailureTypes());
        assertTrue(auditTrail.isClosed(), "監査ログは例外時にもcloseされるべきです");
        assertEquals(List.of("IMPORT_STARTED"), auditTrail.events());
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected: " + expected + " but was: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FailingAuditTrail implements AuditTrail {
        private final List<String> events = new ArrayList<>();
        private boolean closed;

        @Override
        public void record(String event) {
            events.add(event);
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() throws AuditCloseException {
            closed = true;
            throw new AuditCloseException("監査ログ出力先の切断に失敗しました");
        }

        List<String> events() {
            return List.copyOf(events);
        }
    }
}
