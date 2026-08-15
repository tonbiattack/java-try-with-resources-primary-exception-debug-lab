package com.example.tryresources;

import java.util.List;

public record ImportResult(
        boolean accepted,
        FailureCode failureCode,
        Class<? extends Throwable> primaryFailureType,
        List<Class<? extends Throwable>> suppressedFailureTypes
) {
    public static ImportResult successful() {
        return new ImportResult(true, null, null, List.of());
    }

    public static ImportResult rejected(Throwable failure) {
        return new ImportResult(
                false,
                FailureCode.from(failure),
                failure.getClass(),
                List.of(failure.getSuppressed()).stream()
                        .map(Throwable::getClass)
                        .toList()
        );
    }
}
