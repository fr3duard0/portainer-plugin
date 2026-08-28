package io.jenkins.plugins.portainer;

import com.fasterxml.jackson.databind.JsonNode;

/** Readiness of a Kubernetes workload from the Portainer endpoint proxy. */
final class ManifestWorkloadStates {

    enum Progress {
        READY,
        WAITING
    }

    private ManifestWorkloadStates() {
    }

    static Progress classify(ManifestWorkloads.Kind kind, JsonNode resource) {
        if (resource == null || resource.isNull() || resource.isMissingNode()) {
            return Progress.WAITING;
        }
        return switch (kind) {
            case DEPLOYMENT, STATEFULSET -> replicasReady(resource);
            case DAEMONSET -> daemonSetReady(resource);
            case JOB -> jobReady(resource);
        };
    }

    static String displayState(ManifestWorkloads.Kind kind, JsonNode resource) {
        if (resource == null || resource.isNull() || resource.isMissingNode()) {
            return "missing";
        }
        return switch (kind) {
            case DEPLOYMENT, STATEFULSET ->
                    "ready=" + readyReplicas(resource) + "/" + desiredReplicas(resource);
            case DAEMONSET ->
                    "ready=" + intPath(resource, "status", "numberReady")
                            + "/"
                            + intPath(resource, "status", "desiredNumberScheduled");
            case JOB ->
                    "succeeded=" + intPath(resource, "status", "succeeded")
                            + "/"
                            + Math.max(1, intPath(resource, "spec", "completions"));
        };
    }

    private static Progress replicasReady(JsonNode resource) {
        int desired = desiredReplicas(resource);
        if (desired <= 0) {
            return Progress.READY;
        }
        return readyReplicas(resource) >= desired ? Progress.READY : Progress.WAITING;
    }

    private static Progress daemonSetReady(JsonNode resource) {
        int desired = intPath(resource, "status", "desiredNumberScheduled");
        if (desired <= 0) {
            return Progress.WAITING;
        }
        int ready = intPath(resource, "status", "numberReady");
        return ready >= desired ? Progress.READY : Progress.WAITING;
    }

    private static Progress jobReady(JsonNode resource) {
        int completions = Math.max(1, intPath(resource, "spec", "completions"));
        int succeeded = intPath(resource, "status", "succeeded");
        if (succeeded >= completions) {
            return Progress.READY;
        }
        return Progress.WAITING;
    }

    private static int desiredReplicas(JsonNode resource) {
        int spec = intPath(resource, "spec", "replicas");
        return spec < 0 ? 1 : spec;
    }

    private static int readyReplicas(JsonNode resource) {
        int ready = intPath(resource, "status", "readyReplicas");
        if (ready > 0) {
            return ready;
        }
        return intPath(resource, "status", "availableReplicas");
    }

    private static int intPath(JsonNode root, String a, String b) {
        JsonNode n = root.path(a).path(b);
        if (n.isMissingNode() || n.isNull() || !n.isNumber()) {
            return 0;
        }
        return n.asInt();
    }
}
