package com.example.tryresources;

public enum FailureCode {
    INVALID_INVOICE,
    AUDIT_CLOSE_FAILED,
    UNEXPECTED_FAILURE;

    public static FailureCode from(Throwable failure) {
        if (failure instanceof InvalidInvoiceException) {
            return INVALID_INVOICE;
        }
        if (failure instanceof AuditCloseException) {
            return AUDIT_CLOSE_FAILED;
        }
        return UNEXPECTED_FAILURE;
    }
}
