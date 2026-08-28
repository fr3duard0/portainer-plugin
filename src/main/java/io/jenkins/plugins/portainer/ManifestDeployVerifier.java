package io.jenkins.plugins.portainer;

import java.io.IOException;
import java.util.List;

/**
 * Stale-stack gate before update, and post-apply poll until workloads or applications are Ready.
 */
final class ManifestDeployVerifier {

    private ManifestDeployVerifier() {
    }

    /** Before PUT: stack exists but no applications → abort (stale Portainer DB row). */
    static void requireNotStaleBeforeUpdate(
            PortainerClient client,
            ResolvedConnection connection,
            String apiKey,
            int endpoint,
            int stackId,
            String stackName,
            String whenMissing) throws IOException {
        if (stackId < 0 && (stackName == null || stackName.isBlank())) {
            return;
        }
        if (client.hasLiveStackResources(
                connection.baseUrl, apiKey, endpoint, stackId, stackName)) {
            return;
        }
        throw new IOException(whenMissing);
    }

    /**
     * After apply: YAML with workloads → poll k8s proxy; YAML without → no poll;
     * Git → poll Portainer applications for the stack.
     */
    static void waitAfterApply(
            PortainerClient client,
            ResolvedConnection connection,
            String apiKey,
            int endpoint,
            int stackId,
            String stackName,
            boolean yamlMode,
            String yamlContent,
            int waitTimeoutSeconds,
            PortainerBuildLogger log) throws IOException, InterruptedException {
        long timeoutMs = waitTimeoutSeconds * 1000L;
        long intervalMs = KubernetesWait.pollIntervalMs();
        if (yamlMode) {
            List<ManifestWorkloads.Workload> workloads = ManifestWorkloads.parse(yamlContent);
            if (workloads.isEmpty()) {
                if (log != null) {
                    log.debug("No Deployment/StatefulSet/DaemonSet/Job in YAML — skipping readiness wait");
                }
                return;
            }
            if (log != null) {
                log.info("Waiting for manifest workloads count=" + workloads.size()
                        + " timeoutSeconds=" + waitTimeoutSeconds);
            }
            client.waitUntilManifestWorkloadsReady(
                    connection.baseUrl, apiKey, endpoint, workloads, timeoutMs, intervalMs);
            return;
        }
        if (stackId < 0 && (stackName == null || stackName.isBlank())) {
            if (log != null) {
                log.debug("No stack id/name — skipping applications readiness wait");
            }
            return;
        }
        if (log != null) {
            log.info("Waiting for manifest applications timeoutSeconds=" + waitTimeoutSeconds);
        }
        client.waitUntilStackApplicationsReady(
                connection.baseUrl, apiKey, endpoint, stackId, stackName, timeoutMs, intervalMs);
    }
}
