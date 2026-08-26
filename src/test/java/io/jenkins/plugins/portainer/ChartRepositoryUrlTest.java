package io.jenkins.plugins.portainer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartRepositoryUrlTest {

    @Test
    void acceptsHttpHttpsOci() {
        assertEquals("https://charts.example/helm", ChartRepositoryUrl.normalize("https://charts.example/helm/"));
        assertEquals("http://charts.example/helm", ChartRepositoryUrl.normalize("http://charts.example/helm"));
        assertEquals("oci://registry.example/charts", ChartRepositoryUrl.normalize("oci://registry.example/charts/"));
    }

    @Test
    void rejectsUserinfoAndBadScheme() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChartRepositoryUrl.normalize("oci://user:pass@registry.example/charts"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ChartRepositoryUrl.normalize("git://charts.example/helm"));
        assertThrows(IllegalArgumentException.class, () -> ChartRepositoryUrl.normalize(""));
    }
}
