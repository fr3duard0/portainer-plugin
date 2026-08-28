package io.jenkins.plugins.portainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class KubernetesWaitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @AfterEach
    public void clearPollInterval() {
        System.clearProperty(KubernetesWait.POLL_INTERVAL_MS_PROP);
    }

    @Test
    public void pollIntervalMs_blankInvalidAndFloor() {
        System.clearProperty(KubernetesWait.POLL_INTERVAL_MS_PROP);
        assertEquals(KubernetesWait.DEFAULT_POLL_INTERVAL_MS, KubernetesWait.pollIntervalMs());

        System.setProperty(KubernetesWait.POLL_INTERVAL_MS_PROP, "  ");
        assertEquals(KubernetesWait.DEFAULT_POLL_INTERVAL_MS, KubernetesWait.pollIntervalMs());

        System.setProperty(KubernetesWait.POLL_INTERVAL_MS_PROP, "nope");
        assertEquals(KubernetesWait.DEFAULT_POLL_INTERVAL_MS, KubernetesWait.pollIntervalMs());

        System.setProperty(KubernetesWait.POLL_INTERVAL_MS_PROP, "0");
        assertEquals(1L, KubernetesWait.pollIntervalMs());

        System.setProperty(KubernetesWait.POLL_INTERVAL_MS_PROP, " 50 ");
        assertEquals(50L, KubernetesWait.pollIntervalMs());
    }

    @Test
    public void parseTimeoutSeconds_defaultAndRejectsNonPositive() {
        assertEquals(KubernetesWait.DEFAULT_TIMEOUT_SECONDS, KubernetesWait.parseTimeoutSeconds(null));
        assertEquals(KubernetesWait.DEFAULT_TIMEOUT_SECONDS, KubernetesWait.parseTimeoutSeconds("  "));
        assertEquals(15, KubernetesWait.parseTimeoutSeconds("15"));
        assertThrows(IllegalArgumentException.class, () -> KubernetesWait.parseTimeoutSeconds("0"));
        assertThrows(IllegalArgumentException.class, () -> KubernetesWait.parseTimeoutSeconds("-1"));
        assertThrows(IllegalArgumentException.class, () -> KubernetesWait.parseTimeoutSeconds("abc"));
    }

    @Test
    public void classifyHelmRelease() throws Exception {
        assertEquals(KubernetesWait.Progress.WAITING, KubernetesWait.classifyHelmRelease(null));
        assertEquals("missing", KubernetesWait.displayHelmStatus(MAPPER.missingNode()));

        JsonNode blank = MAPPER.readTree("{}");
        assertEquals(KubernetesWait.Progress.READY, KubernetesWait.classifyHelmRelease(blank));
        assertEquals("present", KubernetesWait.displayHelmStatus(blank));

        JsonNode deployed = MAPPER.readTree("{\"Status\":\"deployed\"}");
        assertEquals(KubernetesWait.Progress.READY, KubernetesWait.classifyHelmRelease(deployed));
        assertEquals("deployed", KubernetesWait.displayHelmStatus(deployed));

        JsonNode superseded = MAPPER.readTree("{\"status\":\"superseded\"}");
        assertEquals(KubernetesWait.Progress.READY, KubernetesWait.classifyHelmRelease(superseded));

        JsonNode failed = MAPPER.readTree("{\"State\":\"failed\"}");
        assertEquals(KubernetesWait.Progress.FAILED, KubernetesWait.classifyHelmRelease(failed));
        JsonNode uninstalling = MAPPER.readTree("{\"status\":\"uninstalling\"}");
        assertEquals(KubernetesWait.Progress.FAILED, KubernetesWait.classifyHelmRelease(uninstalling));

        JsonNode pending = MAPPER.readTree("{\"status\":\"pending-install\"}");
        assertEquals(KubernetesWait.Progress.WAITING, KubernetesWait.classifyHelmRelease(pending));
        assertEquals("pending-install", KubernetesWait.displayHelmStatus(pending));
    }

    @Test
    public void classifyApplication() throws Exception {
        assertEquals(KubernetesWait.Progress.WAITING, KubernetesWait.classifyApplication(null));
        assertEquals("missing", KubernetesWait.displayApplicationStatus(MAPPER.nullNode()));

        JsonNode blank = MAPPER.readTree("{\"Name\":\"web\"}");
        assertEquals(KubernetesWait.Progress.READY, KubernetesWait.classifyApplication(blank));
        assertEquals("web", KubernetesWait.displayApplicationStatus(blank));

        JsonNode ready = MAPPER.readTree("{\"name\":\"demo\",\"Status\":\"Ready\"}");
        assertEquals(KubernetesWait.Progress.READY, KubernetesWait.classifyApplication(ready));
        assertEquals("demo=Ready", KubernetesWait.displayApplicationStatus(ready));

        JsonNode healthy = MAPPER.readTree("{\"Status\":\"healthy\"}");
        assertEquals(KubernetesWait.Progress.READY, KubernetesWait.classifyApplication(healthy));

        JsonNode failed = MAPPER.readTree("{\"Name\":\"web\",\"status\":\"Failed\"}");
        assertEquals(KubernetesWait.Progress.FAILED, KubernetesWait.classifyApplication(failed));
        JsonNode unhealthy = MAPPER.readTree("{\"Status\":\"unhealthy\"}");
        assertEquals(KubernetesWait.Progress.FAILED, KubernetesWait.classifyApplication(unhealthy));

        JsonNode errorish = MAPPER.readTree("{\"Status\":\"crash-error\"}");
        assertEquals(KubernetesWait.Progress.FAILED, KubernetesWait.classifyApplication(errorish));

        JsonNode progressing = MAPPER.readTree("{\"Status\":\"Progressing\"}");
        assertEquals(KubernetesWait.Progress.WAITING, KubernetesWait.classifyApplication(progressing));
    }
}
