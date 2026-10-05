package com.yanus.attendance.global.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IntegrationLogging {
    private static final Logger LOG = LoggerFactory.getLogger(IntegrationLogging.class);

    private IntegrationLogging() {
    }

    public static void completed(String integration, String operation, long started, boolean success) {
        LOG.atInfo().addKeyValue("event", "integration.completed")
                .addKeyValue("integration", integration).addKeyValue("operation", operation)
                .addKeyValue("outcome", success ? "success" : "failed")
                .addKeyValue("elapsedMs", (System.nanoTime() - started) / 1_000_000.0)
                .log("External operation completed");
    }
}
