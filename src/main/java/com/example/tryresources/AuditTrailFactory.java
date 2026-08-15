package com.example.tryresources;

@FunctionalInterface
public interface AuditTrailFactory {
    AuditTrail open();
}
