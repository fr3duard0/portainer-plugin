package io.jenkins.plugins.portainer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertEquals("Deployment apps/web", workloads.get(0).display());
    }

    @Test
    void parse_kindsNamespacesAndSkips() {
        List<ManifestWorkloads.Workload> workloads = ManifestWorkloads.parse(
                """
                kind: StatefulSet
                metadata:
                  name: db
                  namespace: data
                ---
                kind: DaemonSet
                metadata:
                  name: agent
                ---
                kind: Job
                metadata:
                  name: migrate
                ---
                kind: Deployment
                metadata:
                  name: ""
                ---
                - not-a-map
                ---
                kind: Service
                metadata:
                  name: svc
                ---
                kind: Deployment
                metadata: not-a-map
                """);
        assertEquals(3, workloads.size());
        assertEquals(ManifestWorkloads.Kind.STATEFULSET, workloads.get(0).kind());
        assertEquals("data", workloads.get(0).namespace());
        assertEquals(ManifestWorkloads.Kind.DAEMONSET, workloads.get(1).kind());
        assertEquals(ManifestWorkloads.DEFAULT_NAMESPACE, workloads.get(1).namespace());
        assertEquals(ManifestWorkloads.Kind.JOB, workloads.get(2).kind());
        assertTrue(ManifestWorkloads.parse(null).isEmpty());
        assertTrue(ManifestWorkloads.parse("  ").isEmpty());
        assertTrue(ManifestWorkloads.parse("apiVersion: v1\nkind: Secret\nmetadata:\n  name: s\n").isEmpty());
        assertTrue(ManifestWorkloads.namespaces(null).isEmpty());
        assertTrue(ManifestWorkloads.namespaces(List.of()).isEmpty());
        assertTrue(ManifestWorkloads.namespaces(workloads).contains("data"));
        assertEquals(ManifestWorkloads.Kind.DEPLOYMENT, ManifestWorkloads.Kind.of("deployment"));
        assertNull(ManifestWorkloads.Kind.of(" "));
        assertNull(ManifestWorkloads.Kind.of("Secret"));
        assertEquals("apps/v1", ManifestWorkloads.Kind.DEPLOYMENT.apiGroupVersion());
        assertEquals("deployments", ManifestWorkloads.Kind.DEPLOYMENT.resource());
    }

    @Test
    void parse_invalidYamlThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> ManifestWorkloads.parse("foo: [unterminated"));
        assertTrue(ex.getMessage().contains("Cannot parse manifest YAML"));
    }
}
