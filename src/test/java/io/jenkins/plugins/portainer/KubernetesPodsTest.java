package io.jenkins.plugins.portainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KubernetesPodsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void problemLine_initWaitingTakesPrecedence() throws Exception {
        JsonNode init = MAPPER.readTree(
                "{\"metadata\":{\"name\":\"p\"},\"status\":{\"initContainerStatuses\":[{"
                        + "\"name\":\"init\",\"state\":{\"waiting\":{\"reason\":\"CrashLoopBackOff\"}}}],"
                        + "\"containerStatuses\":[{\"name\":\"app\",\"state\":{\"waiting\":{"
                        + "\"reason\":\"ImagePullBackOff\"}}}]}}");
        assertTrue(KubernetesPods.problemLine(init).contains("container init"));
        assertTrue(KubernetesPods.problemLine(init).contains("CrashLoopBackOff"));
    }

    @Test
    public void problemLine_containerWaitingReasonAndMessage() throws Exception {
        JsonNode pod = MAPPER.readTree(
                "{\"metadata\":{\"name\":\"web-0\"},\"status\":{\"containerStatuses\":[{"
                        + "\"name\":\"c\",\"state\":{\"waiting\":{"
                        + "\"reason\":\"ImagePullBackOff\","
                        + "\"message\":\"Back-off pulling image registry.example/app:1\"}}}]}}");
        String line = KubernetesPods.problemLine(pod);
        assertTrue(line.contains("pod web-0 container c"));
        assertTrue(line.contains("ImagePullBackOff"));
        assertTrue(line.contains("registry.example/app:1"));
    }

    @Test
    public void problemLine_waitingMessageOnly_andBlankWaitingSkipped() throws Exception {
        JsonNode messageOnly = MAPPER.readTree(
                "{\"metadata\":{\"name\":\"p\"},\"status\":{\"containerStatuses\":[{"
                        + "\"name\":\"c\",\"state\":{\"waiting\":{\"message\":\"pulling\"}}}]}}");
        assertTrue(KubernetesPods.problemLine(messageOnly).contains("pulling"));

        JsonNode blankWaiting = MAPPER.readTree(
                "{\"metadata\":{\"name\":\"p\"},\"status\":{\"containerStatuses\":[{"
                        + "\"state\":{\"waiting\":{}}}]}}");
        assertEquals("", KubernetesPods.problemLine(blankWaiting));
    }

    @Test
    public void problemLine_falseConditionAndPhases() throws Exception {
        JsonNode unschedulable = MAPPER.readTree(
                "{\"metadata\":{\"name\":\"p\"},\"status\":{\"conditions\":[{"
                        + "\"type\":\"PodScheduled\",\"status\":\"False\","
                        + "\"reason\":\"Unschedulable\",\"message\":\"0/1 nodes\"}]}}");
        assertTrue(KubernetesPods.problemLine(unschedulable).contains("Unschedulable"));
        assertTrue(KubernetesPods.problemLine(unschedulable).contains("0/1 nodes"));

        JsonNode typeOnly = MAPPER.readTree(
                "{\"metadata\":{\"name\":\"p\"},\"status\":{\"conditions\":[{"
                        + "\"type\":\"Ready\",\"status\":\"False\"}]}}");
        assertTrue(KubernetesPods.problemLine(typeOnly).contains("Ready"));

        JsonNode blankFalse = MAPPER.readTree(
                "{\"metadata\":{\"name\":\"p\"},\"status\":{\"conditions\":[{\"status\":\"False\"}]}}");
        assertEquals("", KubernetesPods.problemLine(blankFalse));

        JsonNode failed = MAPPER.readTree(
                "{\"metadata\":{\"name\":\"p\"},\"status\":{\"phase\":\"Failed\"}}");
        assertEquals("pod p: Failed", KubernetesPods.problemLine(failed));

        JsonNode pending = MAPPER.readTree(
                "{\"metadata\":{},\"status\":{\"phase\":\"Pending\"}}");
        assertEquals("pod pod: Pending", KubernetesPods.problemLine(pending));
    }

    @Test
    public void problemLine_runningAndNullAreEmpty() throws Exception {
        JsonNode running = MAPPER.readTree(
                "{\"metadata\":{\"name\":\"ok\"},\"status\":{\"phase\":\"Running\","
                        + "\"containerStatuses\":[{\"name\":\"c\",\"state\":{\"running\":{}}}]}}");
        assertEquals("", KubernetesPods.problemLine(running));
        assertEquals("", KubernetesPods.problemLine(null));
        assertEquals("", KubernetesPods.problemLine(MAPPER.missingNode()));
        assertEquals("", KubernetesPods.problemLine(MAPPER.nullNode()));
    }

    @Test
    public void joined_limitsToThree_andCleanTruncates() throws Exception {
        assertEquals("", KubernetesPods.joined(null));
        assertEquals("", KubernetesPods.joined(List.of()));
        assertEquals("a; b; c", KubernetesPods.joined(List.of("a", "b", "c", "d")));

        JsonNode longMsg = MAPPER.readTree(
                "{\"metadata\":{\"name\":\"p\"},\"status\":{\"containerStatuses\":[{"
                        + "\"name\":\"c\",\"state\":{\"waiting\":{\"reason\":\"Err\","
                        + "\"message\":\""
                        + "x".repeat(250)
                        + "\"}}}]}}");
        String line = KubernetesPods.problemLine(longMsg);
        assertTrue(line.endsWith("…"));
        assertTrue(line.length() < 280);
    }
}
