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
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManifestDeployVerifierTest {

    private static final String DEPLOYMENT_YAML =
            "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: web\n  namespace: apps\n";
    private static final String CONFIGMAP_YAML =
            "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: cfg\n";

    private HttpServer server;
    private String base;
    private boolean applicationsEmpty;
    private boolean deploymentReady;

    @BeforeEach
    public void startServer() throws IOException {
        System.setProperty(ConnectionTester.ALLOW_LOOPBACK_FOR_TESTS_PROP, "true");
        applicationsEmpty = false;
        deploymentReady = true;
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
    public void requireNotStaleBeforeUpdate_skipsWhenNoStackIdentity() throws Exception {
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            ManifestDeployVerifier.requireNotStaleBeforeUpdate(
                    client, connection(), "key", 1, -1, "  ", "stale");
        }
    }

    @Test
    public void requireNotStaleBeforeUpdate_abortsWhenNoLiveApps() {
        applicationsEmpty = true;
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            IOException e = assertThrows(
                    IOException.class,
                    () -> ManifestDeployVerifier.requireNotStaleBeforeUpdate(
                            client, connection(), "key", 1, 21, "web", "stale stack"));
            assertTrue(e.getMessage().contains("stale stack"));
        }
    }

    @Test
    public void requireNotStaleBeforeUpdate_okWhenLiveAppsExist() throws Exception {
        try (PortainerClient client = new PortainerClient(2000, 2000)) {
            ManifestDeployVerifier.requireNotStaleBeforeUpdate(
                    client, connection(), "key", 1, 21, "web", "stale stack");
        }
    }

    @Test
    public void waitAfterApply_yamlWithoutWorkloadsSkips() throws Exception {
        try (PortainerClient client = new PortainerClient(2000, 2000);
                PortainerBuildLogger log = testLog()) {
            ManifestDeployVerifier.waitAfterApply(
                    client, connection(), "key", 1, 21, "web", true, CONFIGMAP_YAML, 5, log);
            ManifestDeployVerifier.waitAfterApply(
                    client, connection(), "key", 1, 21, "web", true, CONFIGMAP_YAML, 5, null);
        }
    }

    @Test
    public void waitAfterApply_yamlWorkloadsPollUntilReady() throws Exception {
        try (PortainerClient client = new PortainerClient(2000, 2000);
                PortainerBuildLogger log = testLog()) {
            ManifestDeployVerifier.waitAfterApply(
                    client, connection(), "key", 1, 21, "web", true, DEPLOYMENT_YAML, 5, log);
        }
    }

    @Test
    public void waitAfterApply_gitSkipsWhenNoStackIdentity() throws Exception {
        try (PortainerClient client = new PortainerClient(2000, 2000);
                PortainerBuildLogger log = testLog()) {
            ManifestDeployVerifier.waitAfterApply(
                    client, connection(), "key", 1, -1, "  ", false, null, 5, log);
            ManifestDeployVerifier.waitAfterApply(
                    client, connection(), "key", 1, -1, "", false, null, 5, null);
        }
    }

    @Test
    public void waitAfterApply_gitPollsApplications() throws Exception {
        try (PortainerClient client = new PortainerClient(2000, 2000);
                PortainerBuildLogger log = testLog()) {
            ManifestDeployVerifier.waitAfterApply(
                    client, connection(), "key", 1, 21, "web", false, null, 5, log);
        }
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (path != null && path.matches("/api/kubernetes/\\d+/applications$") && "GET".equalsIgnoreCase(method)) {
            if (applicationsEmpty) {
                respond(exchange, 200, "[]");
            } else {
                respond(
                        exchange,
                        200,
                        "[{\"Name\":\"demo\",\"StackId\":21,\"StackName\":\"web\",\"Status\":\"Ready\"}]");
            }
            return;
        }
        if (path != null && path.contains("/deployments/") && "GET".equalsIgnoreCase(method)) {
            if (deploymentReady) {
                respond(
                        exchange,
                        200,
                        "{\"metadata\":{\"name\":\"web\"},\"spec\":{\"replicas\":1},"
                                + "\"status\":{\"readyReplicas\":1}}");
            } else {
                respond(
                        exchange,
                        200,
                        "{\"metadata\":{\"name\":\"web\"},\"spec\":{\"replicas\":1},"
                                + "\"status\":{\"readyReplicas\":0}}");
            }
            return;
        }
        respond(exchange, 404, "{\"message\":\"not found\"}");
    }

    private ResolvedConnection connection() {
        return new ResolvedConnection("lab", base, "cred", 2000, 2000);
    }

    private static PortainerBuildLogger testLog() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return new PortainerBuildLogger(
                Logger.getLogger(ManifestDeployVerifierTest.class.getName()),
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
