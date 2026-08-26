package io.jenkins.plugins.portainer;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PortainerConnectionsTruncateTest {

    @Test
    void truncateMessage_keepsHeadAndTail() {
        String head = "HTTP 500 - helm install failed: ";
        String mid = "x".repeat(5000);
        String tail = "Error: UPGRADE FAILED: timed out waiting for the condition";
        IOException ex = new IOException(head + mid + tail);
        String truncated = PortainerConnections.truncateMessage(ex);
        assertTrue(truncated.length() <= PortainerClient.MAX_ERROR_DETAIL_CHARS);
        assertTrue(truncated.startsWith(head.substring(0, Math.min(40, head.length()))));
        assertTrue(truncated.contains("…"));
        assertTrue(truncated.endsWith(tail.substring(Math.max(0, tail.length() - 40))));
        assertTrue(truncated.contains("UPGRADE FAILED") || truncated.contains("timed out"));
    }
}
