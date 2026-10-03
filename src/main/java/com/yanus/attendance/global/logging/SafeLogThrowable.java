package com.yanus.attendance.global.logging;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class SafeLogThrowable extends RuntimeException {
    private SafeLogThrowable(Throwable source, Throwable cause) {
        super(source.getClass().getName(), cause);
        setStackTrace(source.getStackTrace());
    }

    public static Throwable from(Throwable source) {
        return copy(source, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static Throwable copy(Throwable source, Set<Throwable> visited, int depth) {
        if (source == null || depth >= 8 || !visited.add(source)) {
            return null;
        }
        var safe = new SafeLogThrowable(source, copy(source.getCause(), visited, depth + 1));
        for (Throwable suppressed : source.getSuppressed()) {
            Throwable child = copy(suppressed, visited, depth + 1);
            if (child != null) {
                safe.addSuppressed(child);
            }
        }
        return safe;
    }
}
