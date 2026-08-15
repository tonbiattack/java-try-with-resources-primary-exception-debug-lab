package com.example.tryresources;

public interface AuditTrail extends AutoCloseable {
    void record(String event);

    boolean isClosed();

    @Override
    void close() throws AuditCloseException;
}
