package io.jenkins.plugins.portainer;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Set;

/** Wait timeout / poll interval and Helm release / Portainer application readiness. */
final class KubernetesWait {

    static final int DEFAULT_TIMEOUT_SECONDS = 300;
    static final String POLL_INTERVAL_MS_PROP = "portainer.k8s.pollIntervalMs";
    static final long DEFAULT_POLL_INTERVAL_MS = 2000L;

    private static final Set<String> HELM_READY = Set.of("deployed", "superseded");
    private static final Set<String> HELM_FAILED = Set.of("failed", "uninstalling", "unknown");
    private static final Set<String> APP_READY = Set.of("ready", "running", "deployed", "healthy");
    private static final Set<String> APP_FAILED = Set.of("failed", "error", "unhealthy");

    enum Progress {
        READY,
        FAILED,
        WAITING
    }

    private KubernetesWait() {
    }

    static long pollIntervalMs() {
        String raw = System.getProperty(POLL_INTERVAL_MS_PROP);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_POLL_INTERVAL_MS;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value < 1L ? 1L : value;
        } catch (NumberFormatException e) {
            return DEFAULT_POLL_INTERVAL_MS;
        }
    }

    static int parseTimeoutSeconds(String configured) {
        String raw = configured == null || configured.isBlank()
                ? String.valueOf(DEFAULT_TIMEOUT_SECONDS)
                : configured.trim();
        int seconds;
        try {
            seconds = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Wait timeout must be a positive number of seconds.");
        }
        if (seconds <= 0) {
            throw new IllegalArgumentException("Wait timeout must be a positive number of seconds.");
        }
        return seconds;
    }

    static Progress classifyHelmRelease(JsonNode release) {
        if (release == null || release.isNull() || release.isMissingNode()) {
            return Progress.WAITING;
        }
        String status = statusOf(release).toLowerCase(Locale.ROOT);
        if (status.isBlank()) {
            return Progress.READY;
        }
        if (HELM_FAILED.contains(status)) {
            return Progress.FAILED;
        }
        if (HELM_READY.contains(status)) {
            return Progress.READY;
        }
        return Progress.WAITING;
    }

    static Progress classifyApplication(JsonNode app) {
        if (app == null || app.isNull() || app.isMissingNode()) {
            return Progress.WAITING;
        }
        String status = statusOf(app).toLowerCase(Locale.ROOT);
        if (status.isBlank()) {
            return Progress.READY;
        }
        if (APP_FAILED.contains(status) || status.contains("fail") || status.contains("error")) {
            return Progress.FAILED;
        }
        if (APP_READY.contains(status) || status.contains("ready")) {
            return Progress.READY;
        }
        return Progress.WAITING;
    }

    static String displayHelmStatus(JsonNode release) {
        if (release == null || release.isNull() || release.isMissingNode()) {
            return "missing";
        }
        String status = statusOf(release);
        return status.isBlank() ? "present" : status;
    }

    static String displayApplicationStatus(JsonNode app) {
        if (app == null || app.isNull() || app.isMissingNode()) {
            return "missing";
        }
        String status = statusOf(app);
        String name = firstNonBlank(text(app, "Name"), text(app, "name"), "app");
        return status.isBlank() ? name : name + "=" + status;
    }

    private static String statusOf(JsonNode node) {
        return firstNonBlank(text(node, "Status"), text(node, "status"), text(node, "State"), text(node, "state"));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return "";
        }
        String s = v.asText("");
        return s == null ? "" : s.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
