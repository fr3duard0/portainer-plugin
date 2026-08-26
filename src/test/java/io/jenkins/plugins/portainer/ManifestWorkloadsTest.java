package io.jenkins.plugins.portainer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestWorkloadsTest {

    @Test
    void parse_skipsConfigMap_findsDeployment() {
        String yaml = """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: cfg
                  namespace: apps
                ---
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: web
                  namespace: apps
                spec:
                  replicas: 1
                """;
        List<ManifestWorkloads.Workload> workloads = ManifestWorkloads.parse(yaml);
        assertEquals(1, workloads.size());
        assertEquals(ManifestWorkloads.Kind.DEPLOYMENT, workloads.get(0).kind());
        assertEquals("apps", workloads.get(0).namespace());
        assertEquals("web", workloads.get(0).name());
    }

    @Test
    void parse_emptyKinds_returnsEmpty() {
        assertTrue(ManifestWorkloads.parse("apiVersion: v1\nkind: Secret\nmetadata:\n  name: s\n").isEmpty());
    }
}
