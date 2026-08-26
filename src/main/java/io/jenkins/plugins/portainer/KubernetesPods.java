package io.jenkins.plugins.portainer;

import com.fasterxml.jackson.databind.JsonNode;

/** Pod problem lines for K8s wait abort (one-shot hint). */
final class KubernetesPods {

    private KubernetesPods() {
    }

    static String problemLine(JsonNode pod) {
        if (pod == null || pod.isNull() || pod.isMissingNode()) {
            return "";
        }
        String name = firstNonBlank(text(pod.path("metadata"), "name"), "pod");
        JsonNode status = pod.path("status");
        String waiting = firstWaiting(status.path("initContainerStatuses"), name);
        if (!waiting.isBlank()) {
            return waiting;
        }
        waiting = firstWaiting(status.path("containerStatuses"), name);
        if (!waiting.isBlank()) {
            return waiting;
        }
        String condition = firstFalseCondition(status.path("conditions"), name);
        if (!condition.isBlank()) {
            return condition;
        }
        String phase = text(status, "phase");
        if ("Pending".equalsIgnoreCase(phase) || "Failed".equalsIgnoreCase(phase)) {
            return "pod " + name + ": " + phase;
        }
        return "";
    }

    static String joined(java.util.List<String> problems) {
        if (problems == null || problems.isEmpty()) {
            return "";
        }
        int limit = Math.min(3, problems.size());
        return String.join("; ", problems.subList(0, limit));
    }

    private static String firstWaiting(JsonNode statuses, String podName) {
        if (!statuses.isArray()) {
            return "";
        }
        for (JsonNode cs : statuses) {
            JsonNode waiting = cs.path("state").path("waiting");
            if (waiting.isMissingNode() || waiting.isNull() || !waiting.isObject()) {
                continue;
            }
            String reason = clean(text(waiting, "reason"));
            String message = clean(text(waiting, "message"));
            if (reason.isBlank() && message.isBlank()) {
                continue;
            }
            String container = firstNonBlank(text(cs, "name"), "container");
            StringBuilder sb = new StringBuilder("pod ").append(podName).append(" container ").append(container);
            if (!reason.isBlank()) {
                sb.append(": ").append(reason);
            }
            if (!message.isBlank()) {
                sb.append(": ").append(message);
            }
            return sb.toString();
        }
        return "";
    }

    private static String firstFalseCondition(JsonNode conditions, String podName) {
        if (!conditions.isArray()) {
            return "";
        }
        for (JsonNode condition : conditions) {
            if (!"False".equalsIgnoreCase(text(condition, "status"))) {
                continue;
            }
            String type = clean(text(condition, "type"));
            String reason = clean(text(condition, "reason"));
            String message = clean(text(condition, "message"));
            if (type.isBlank() && reason.isBlank() && message.isBlank()) {
                continue;
            }
            StringBuilder sb = new StringBuilder("pod ").append(podName);
            String detail = firstNonBlank(reason, type);
            if (!detail.isBlank()) {
                sb.append(": ").append(detail);
            }
            if (!message.isBlank()) {
                sb.append(": ").append(message);
            }
            return sb.toString();
        }
        return "";
    }

    private static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String oneLine = raw.replaceAll("\\s+", " ").trim();
        if (oneLine.length() > 200) {
            return oneLine.substring(0, 200) + "…";
        }
        return oneLine;
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

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }
}
