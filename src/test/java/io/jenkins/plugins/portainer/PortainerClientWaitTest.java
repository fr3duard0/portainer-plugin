package io.jenkins.plugins.portainer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import hudson.util.StreamTaskListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PortainerClientWaitTest {

    private static final String READY_DEPLOYMENT =
            "{\"metadata\":{\"name\":\"web\"},\"spec\":{\"replicas\":1},"
                    + "\"status\":{\"readyReplicas\":1}}";
    private static final String WAITING_DEPLOYMENT =
            "{\"metadata\":{\"name\":\"web\"},\"spec\":{\"replicas\":1},"
                    + "\"status\":{\"readyReplicas\":0}}";
    private static final String PODS_IMAGE_PULL =
            "{\"items\":[{\"metadata\":{\"name\":\"web-0\"},\"status\":{"
                    + "\"phase\":\"Pending\",\"containerStatuses\":[{\"name\":\"c\","
                    + "\"state\":{\"waiting\":{\"reason\":\"ImagePullBackOff\","
                    + "\"message\":\"registry.example/app:1\"}}}]}}]}";
    private static final String APPS_READY =
            "[{\"Name\":\"demo\",\"StackId\":21,\"StackName\":\"web\",\"Status\":\"Ready\","
                    + "\"ResourcePool\":\"apps\"}]";
    private static final String APPS_FAILED =
            "[{\"Name\":\"demo\",\"StackID\":21,\"Status\":\"Failed\",\"Namespace\":\"apps\"}]";
    private static final String APPS_WAITING =
            "[{\"Name\":\"demo\",\"StackId\":21,\"Status\":\"Progressing\",\"namespace\":\"apps\"}]";
    private static final String HELM_DEPLOYED =
            "[{\"Name\":\"nginx\",\"Namespace\":\"default\",\"Status\":\"deployed\"}]";
    private static final String HELM_FAILED =
            "[{\"Name\":\"nginx\",\"Namespace\":\"default\",\"Status\":\"failed\"}]";
    private static final String HELM_PENDING =
            "[{\"Name\":\"nginx\",\"Namespace\":\"default\",\"Status\":\"pending-install\"}]";
    private static final String ITEMS_EMPTY = "{\"items\":[]}";
    private static final String ITEMS_WAITING =
            "{\"items\":[{\"metadata\":{\"name\":\"nginx\"},\"spec\":{\"replicas\":1},"
                    + "\"status\":{\"readyReplicas\":0}}]}";
    private static final String DAEMONSET_READY =
            "{\"metadata\":{\"name\":\"agent\"},\"status\":{\"desiredNumberScheduled\":1,\"numberReady\":1}}";
    private static final String JOB_READY =
            "{\"metadata\":{\"name\":\"migrate\"},\"spec\":{\"completions\":1},\"status\":{\"succeeded\":1}}";

    private HttpServer server;
    private String base;
    private final AtomicInteger workloadCode = new AtomicInteger(200);
    private final AtomicReference<String> workloadBody = new AtomicReference<>(READY_DEPLOYMENT);
    private final AtomicInteger appsCode = new AtomicInteger(200);
    private final AtomicReference<String> appsBody = new AtomicReference<>("[]");
    private final AtomicInteger podsCode = new AtomicInteger(200);
    private final AtomicReference<String> podsBody = new AtomicReference<>(ITEMS_EMPTY);
    private final AtomicInteger helmListCode = new AtomicInteger(200);
    private final AtomicReference<String> helmListBody = new AtomicReference<>(HELM_DEPLOYED);
    private final AtomicInteger listWorkloadsCode = new AtomicInteger(200);
    private final AtomicReference<String> listWorkloadsBody = new AtomicReference<>(ITEMS_EMPTY);
    private final AtomicInteger workloadGets = new AtomicInteger();
    private final AtomicInteger podsGets = new AtomicInteger();

    @BeforeEach
    public void startServer() throws IOException {
        System.setProperty(ConnectionTester.ALLOW_LOOPBACK_FOR_TESTS_PROP, "true");
        workloadCode.set(200);
        workloadBody.set(READY_DEPLOYMENT);
        appsCode.set(200);
        appsBody.set("[]");
        podsCode.set(200);
        podsBody.set(ITEMS_EMPTY);
        helmListCode.set(200);
        helmListBody.set(HELM_DEPLOYED);
        listWorkloadsCode.set(200);
        listWorkloadsBody.set(ITEMS_EMPTY);
        workloadGets.set(0);
        podsGets.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::dispatch);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    public void stopServer() {
        System.clearProperty(ConnectionTester.ALLOW_LOOPBACK_FOR_TESTS_PROP);
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void waitUntilManifestWorkloadsReady_emptyIsNoop() throws Exception {
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            client.waitUntilManifestWorkloadsReady(base, "key", 1, List.of(), 1000L, 10L);
            client.waitUntilManifestWorkloadsReady(base, "key", 1, null, 1000L, 10L);
        }
        assertEquals(0, workloadGets.get());
        assertEquals(0, podsGets.get());
    }

    @Test
    public void waitUntilManifestWorkloadsReady_readyDeployment() throws Exception {
        List<ManifestWorkloads.Workload> workloads = ManifestWorkloads.parse(
                "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: web\n  namespace: apps\n");
        try (PortainerBuildLogger log = testLog();
                PortainerClient client = new PortainerClient(2000, 2000, log)) {
            client.waitUntilManifestWorkloadsReady(base, "key", 1, workloads, 1000L, 10L);
        }
        assertTrue(workloadGets.get() >= 1);
        assertEquals(0, podsGets.get());
    }

    @Test
    public void waitUntilManifestWorkloadsReady_timeoutIncludesPodsHint() {
        workloadBody.set(WAITING_DEPLOYMENT);
        podsBody.set(PODS_IMAGE_PULL);
        List<ManifestWorkloads.Workload> workloads = ManifestWorkloads.parse(
                "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: web\n  namespace: apps\n");
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilManifestWorkloadsReady(base, "key", 1, workloads, 40L, 10L));
            assertTrue(e.getMessage().contains("did not become ready"));
            assertTrue(e.getMessage().contains("Deployment apps/web"));
            assertTrue(e.getMessage().contains("ImagePullBackOff"));
        }
        assertTrue(podsGets.get() >= 1);
    }

    @Test
    public void waitUntilManifestWorkloadsReady_notFoundThenReady() throws Exception {
        workloadCode.set(404);
        List<ManifestWorkloads.Workload> workloads = ManifestWorkloads.parse(
                "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: web\n  namespace: apps\n");
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            client.waitUntilManifestWorkloadsReady(base, "key", 1, workloads, 2000L, 10L);
        }
        assertTrue(workloadGets.get() >= 2);
    }

    @Test
    public void waitUntilManifestWorkloadsReady_httpErrorPropagates() {
        workloadCode.set(500);
        List<ManifestWorkloads.Workload> workloads = ManifestWorkloads.parse(
                "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: web\n  namespace: apps\n");
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilManifestWorkloadsReady(base, "key", 1, workloads, 1000L, 10L));
            assertTrue(e.getMessage().contains("HTTP 500"));
        }
    }

    @Test
    public void hasLiveStackResources_andApplicationsReady() throws Exception {
        appsBody.set(APPS_READY);
        try (PortainerBuildLogger log = testLog();
                PortainerClient client = new PortainerClient(2000, 2000, log)) {
            assertTrue(client.hasLiveStackResources(base, "key", 1, 21, "web"));
            client.waitUntilStackApplicationsReady(base, "key", 1, 21, "web", 1000L, 10L);
        }
    }

    @Test
    public void hasLiveStackResources_matchesNameWhenIdUnknown() throws Exception {
        appsBody.set("[{\"Name\":\"web\",\"Status\":\"Ready\"}]");
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            assertTrue(client.hasLiveStackResources(base, "key", 1, -1, "web"));
            assertFalse(client.hasLiveStackResources(base, "key", 1, 99, "other"));
        }
    }

    @Test
    public void waitUntilStackApplicationsReady_failedIncludesPodsHint() {
        appsBody.set(APPS_FAILED);
        podsBody.set(PODS_IMAGE_PULL);
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilStackApplicationsReady(base, "key", 1, 21, "web", 1000L, 10L));
            assertTrue(e.getMessage().contains("failed readiness"));
            assertTrue(e.getMessage().contains("ImagePullBackOff"));
        }
    }

    @Test
    public void waitUntilStackApplicationsReady_timeoutWhileMissing() {
        appsBody.set("[]");
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilStackApplicationsReady(base, "key", 1, 21, "web", 40L, 10L));
            assertTrue(e.getMessage().contains("did not become ready"));
            assertTrue(e.getMessage().contains("last=missing"));
        }
    }

    @Test
    public void waitUntilStackApplicationsReady_timeoutWhileWaiting() {
        appsBody.set(APPS_WAITING);
        try (PortainerBuildLogger log = testLog();
                PortainerClient client = new PortainerClient(2000, 2000, log)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilStackApplicationsReady(base, "key", 1, 21, "web", 40L, 10L));
            assertTrue(e.getMessage().contains("did not become ready"));
            assertTrue(e.getMessage().contains("Progressing"));
        }
    }

    @Test
    public void waitUntilHelmReleaseReady_deployedWithEmptyWorkloads() throws Exception {
        helmListBody.set(HELM_DEPLOYED);
        listWorkloadsBody.set("{}");
        try (PortainerBuildLogger log = testLog();
                PortainerClient client = new PortainerClient(2000, 2000, log)) {
            client.waitUntilHelmReleaseReady(base, "key", 1, "nginx", "default", 1000L, 10L);
        }
    }

    @Test
    public void waitUntilHelmReleaseReady_blankNamespaceDefaults() throws Exception {
        helmListBody.set("[{\"Name\":\"nginx\",\"namespace\":\"default\",\"Status\":\"deployed\"}]");
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            client.waitUntilHelmReleaseReady(base, "key", 1, "nginx", "  ", 1000L, 10L);
        }
    }

    @Test
    public void waitUntilHelmReleaseReady_failedIncludesPodsHint() {
        helmListBody.set(HELM_FAILED);
        podsBody.set(PODS_IMAGE_PULL);
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilHelmReleaseReady(base, "key", 1, "nginx", "default", 1000L, 10L));
            assertTrue(e.getMessage().contains("Helm release failed"));
            assertTrue(e.getMessage().contains("ImagePullBackOff"));
        }
    }

    @Test
    public void waitUntilHelmReleaseReady_timeoutPending() {
        helmListBody.set(HELM_PENDING);
        try (PortainerBuildLogger log = testLog();
                PortainerClient client = new PortainerClient(2000, 2000, log)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilHelmReleaseReady(base, "key", 1, "nginx", "default", 40L, 10L));
            assertTrue(e.getMessage().contains("did not become ready"));
            assertTrue(e.getMessage().contains("pending-install"));
        }
    }

    @Test
    public void waitUntilHelmReleaseReady_deployedWorkloadNotReadyTimesOut() {
        helmListBody.set(HELM_DEPLOYED);
        listWorkloadsBody.set(ITEMS_WAITING);
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilHelmReleaseReady(base, "key", 1, "nginx", "default", 40L, 10L));
            assertTrue(e.getMessage().contains("workloads="));
            assertTrue(e.getMessage().contains("Deployment default/nginx"));
        }
    }

    @Test
    public void waitUntilHelmReleaseReady_forbiddenWorkloadListIsSkipped() throws Exception {
        helmListBody.set(HELM_DEPLOYED);
        listWorkloadsCode.set(403);
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            client.waitUntilHelmReleaseReady(base, "key", 1, "nginx", "default", 1000L, 10L);
        }
    }

    @Test
    public void waitUntilHelmReleaseReady_missingReleaseTimesOut() {
        helmListBody.set("[]");
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilHelmReleaseReady(base, "key", 1, "nginx", null, 40L, 10L));
            assertTrue(e.getMessage().contains("last=release=missing"));
        }
    }

    @Test
    public void waitUntilHelmReleaseReady_namespaceMismatchTimesOut() {
        helmListBody.set("[{\"Name\":\"nginx\",\"Namespace\":\"other\",\"Status\":\"deployed\"}]");
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilHelmReleaseReady(base, "key", 1, "nginx", "default", 40L, 10L));
            assertTrue(e.getMessage().contains("last=release=missing"));
        }
    }

    @Test
    public void waitUntilHelmReleaseReady_nonArrayListTimesOut() {
        helmListBody.set("{\"Name\":\"nginx\"}");
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilHelmReleaseReady(base, "key", 1, "nginx", "default", 40L, 10L));
            assertTrue(e.getMessage().contains("did not become ready"));
        }
    }

    @Test
    public void listApplications_nonArrayThrows() {
        appsBody.set("{\"Name\":\"demo\"}");
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class, () -> client.hasLiveStackResources(base, "key", 1, 21, "web"));
            assertTrue(e.getMessage().contains("did not return an array"));
        }
    }

    @Test
    public void podsHint_404IsEmpty() {
        workloadBody.set(WAITING_DEPLOYMENT);
        podsCode.set(404);
        List<ManifestWorkloads.Workload> workloads = ManifestWorkloads.parse(
                "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: web\n  namespace: apps\n");
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilManifestWorkloadsReady(base, "key", 1, workloads, 40L, 10L));
            assertTrue(e.getMessage().contains("did not become ready"));
            assertFalse(e.getMessage().contains(" — "));
        }
    }

    @Test
    public void waitUntilManifestWorkloadsReady_allKindsReady() throws Exception {
        List<ManifestWorkloads.Workload> workloads = ManifestWorkloads.parse(
                """
                kind: Deployment
                metadata:
                  name: web
                  namespace: apps
                ---
                kind: StatefulSet
                metadata:
                  name: db
                  namespace: apps
                ---
                kind: DaemonSet
                metadata:
                  name: agent
                  namespace: apps
                ---
                kind: Job
                metadata:
                  name: migrate
                  namespace: apps
                """);
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            client.waitUntilManifestWorkloadsReady(base, "key", 1, workloads, 1000L, 10L);
        }
        assertTrue(workloadGets.get() >= 4);
    }

    @Test
    public void waitUntilHelmReleaseReady_listErrorPropagates() {
        helmListBody.set(HELM_DEPLOYED);
        listWorkloadsCode.set(500);
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> client.waitUntilHelmReleaseReady(base, "key", 1, "nginx", "default", 1000L, 10L));
            assertTrue(e.getMessage().contains("HTTP 500"));
        }
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (path != null && path.matches("/api/kubernetes/\\d+/applications$") && "GET".equalsIgnoreCase(method)) {
            respond(exchange, appsCode.get(), appsBody.get());
            return;
        }
        if (path != null && path.contains("/kubernetes/api/v1/namespaces/") && path.endsWith("/pods")) {
            podsGets.incrementAndGet();
            respond(exchange, podsCode.get(), podsBody.get());
            return;
        }
        if (path != null && path.matches("/api/endpoints/\\d+/kubernetes/helm$") && "GET".equalsIgnoreCase(method)) {
            respond(exchange, helmListCode.get(), helmListBody.get());
            return;
        }
        if (path != null && isNamedWorkload(path) && "GET".equalsIgnoreCase(method)) {
            int n = workloadGets.incrementAndGet();
            if (path.contains("/daemonsets/")) {
                respond(exchange, 200, DAEMONSET_READY);
                return;
            }
            if (path.contains("/jobs/")) {
                respond(exchange, 200, JOB_READY);
                return;
            }
            int code = workloadCode.get();
            if (code == 404 && n > 1) {
                respond(exchange, 200, READY_DEPLOYMENT);
                return;
            }
            respond(exchange, code, workloadBody.get());
            return;
        }
        if (path != null && isWorkloadList(path) && "GET".equalsIgnoreCase(method)) {
            respond(exchange, listWorkloadsCode.get(), listWorkloadsBody.get());
            return;
        }
        respond(exchange, 404, "{\"message\":\"not found\"}");
    }

    private static boolean isNamedWorkload(String path) {
        return path.contains("/deployments/")
                || path.contains("/statefulsets/")
                || path.contains("/daemonsets/")
                || path.contains("/jobs/");
    }

    private static boolean isWorkloadList(String path) {
        return path.endsWith("/deployments")
                || path.endsWith("/statefulsets")
                || path.endsWith("/daemonsets")
                || path.endsWith("/jobs");
    }

    private static PortainerBuildLogger testLog() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return new PortainerBuildLogger(
                Logger.getLogger(PortainerClientWaitTest.class.getName()),
                new StreamTaskListener(out, StandardCharsets.UTF_8),
                true);
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
