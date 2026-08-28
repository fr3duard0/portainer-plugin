package io.jenkins.plugins.portainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ManifestWorkloadStatesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void classify_missingIsWaiting() {
        assertEquals(
                ManifestWorkloadStates.Progress.WAITING,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.DEPLOYMENT, null));
        assertEquals("missing", ManifestWorkloadStates.displayState(ManifestWorkloads.Kind.JOB, null));
        assertEquals(
                ManifestWorkloadStates.Progress.WAITING,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.STATEFULSET, MAPPER.missingNode()));
        assertEquals(
                "missing",
                ManifestWorkloadStates.displayState(ManifestWorkloads.Kind.DAEMONSET, MAPPER.nullNode()));
    }

    @Test
    public void deployment_readyViaReadyOrAvailableReplicas() throws Exception {
        JsonNode ready = MAPPER.readTree(
                "{\"spec\":{\"replicas\":2},\"status\":{\"readyReplicas\":2}}");
        assertEquals(
                ManifestWorkloadStates.Progress.READY,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.DEPLOYMENT, ready));
        assertEquals("ready=2/2", ManifestWorkloadStates.displayState(ManifestWorkloads.Kind.DEPLOYMENT, ready));

        JsonNode availableOnly = MAPPER.readTree(
                "{\"spec\":{\"replicas\":1},\"status\":{\"availableReplicas\":1}}");
        assertEquals(
                ManifestWorkloadStates.Progress.READY,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.DEPLOYMENT, availableOnly));

        JsonNode waiting = MAPPER.readTree(
                "{\"spec\":{\"replicas\":3},\"status\":{\"readyReplicas\":1}}");
        assertEquals(
                ManifestWorkloadStates.Progress.WAITING,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.DEPLOYMENT, waiting));
        assertEquals("ready=1/3", ManifestWorkloadStates.displayState(ManifestWorkloads.Kind.DEPLOYMENT, waiting));
    }

    @Test
    public void deployment_zeroDesiredIsReady_negativeSpecBecomesOne() throws Exception {
        JsonNode scaledToZero = MAPPER.readTree("{\"spec\":{\"replicas\":0},\"status\":{}}");
        assertEquals(
                ManifestWorkloadStates.Progress.READY,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.DEPLOYMENT, scaledToZero));

        JsonNode missingSpec = MAPPER.readTree("{}");
        assertEquals(
                ManifestWorkloadStates.Progress.READY,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.STATEFULSET, missingSpec));

        JsonNode negative = MAPPER.readTree("{\"spec\":{\"replicas\":-1},\"status\":{\"readyReplicas\":0}}");
        assertEquals(
                ManifestWorkloadStates.Progress.WAITING,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.DEPLOYMENT, negative));
        assertEquals("ready=0/1", ManifestWorkloadStates.displayState(ManifestWorkloads.Kind.DEPLOYMENT, negative));
    }

    @Test
    public void daemonSet_andJob() throws Exception {
        JsonNode dsWaitingDesiredZero = MAPPER.readTree("{\"status\":{\"desiredNumberScheduled\":0}}");
        assertEquals(
                ManifestWorkloadStates.Progress.WAITING,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.DAEMONSET, dsWaitingDesiredZero));

        JsonNode dsReady = MAPPER.readTree(
                "{\"status\":{\"desiredNumberScheduled\":2,\"numberReady\":2}}");
        assertEquals(
                ManifestWorkloadStates.Progress.READY,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.DAEMONSET, dsReady));
        assertEquals("ready=2/2", ManifestWorkloadStates.displayState(ManifestWorkloads.Kind.DAEMONSET, dsReady));

        JsonNode dsWaiting = MAPPER.readTree(
                "{\"status\":{\"desiredNumberScheduled\":2,\"numberReady\":1}}");
        assertEquals(
                ManifestWorkloadStates.Progress.WAITING,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.DAEMONSET, dsWaiting));

        JsonNode jobReady = MAPPER.readTree(
                "{\"spec\":{\"completions\":2},\"status\":{\"succeeded\":2}}");
        assertEquals(
                ManifestWorkloadStates.Progress.READY,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.JOB, jobReady));
        assertEquals("succeeded=2/2", ManifestWorkloadStates.displayState(ManifestWorkloads.Kind.JOB, jobReady));

        JsonNode jobWaiting = MAPPER.readTree("{\"status\":{\"succeeded\":0}}");
        assertEquals(
                ManifestWorkloadStates.Progress.WAITING,
                ManifestWorkloadStates.classify(ManifestWorkloads.Kind.JOB, jobWaiting));
        assertEquals("succeeded=0/1", ManifestWorkloadStates.displayState(ManifestWorkloads.Kind.JOB, jobWaiting));
    }
}
