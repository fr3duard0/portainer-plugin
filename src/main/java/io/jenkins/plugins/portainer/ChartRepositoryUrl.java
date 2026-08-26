package io.jenkins.plugins.portainer;

/**
 * Helm chart repository URL for Portainer body {@code repo}.
 * Schemes: {@code http}, {@code https}, {@code oci}. No userinfo. Host required.
 * Values Git / Manifest Git URLs stay on {@link GitRepositoryUrl}.
 */
final class ChartRepositoryUrl {

    private ChartRepositoryUrl() {
    }

    /**
     * @return trimmed URL (trailing slashes stripped)
     */
    static String normalize(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Chart repository URL is required (https://charts.example/helm or oci://registry.example/charts).");
        }
        String clean = stripTrailingSlashes(repositoryUrl.trim());
        boolean https = startsWithIgnoreCase(clean, "https://");
        boolean http = startsWithIgnoreCase(clean, "http://");
        boolean oci = startsWithIgnoreCase(clean, "oci://");
        if (!https && !http && !oci) {
            throw new IllegalArgumentException(
                    "Chart repository URL must start with http://, https://, or oci://.");
        }
        int schemeEnd = clean.indexOf("://");
        int authStart = schemeEnd + 3;
        int at = clean.indexOf('@', authStart);
        int pathStart = clean.indexOf('/', authStart);
        if (at >= 0 && (pathStart < 0 || at < pathStart)) {
            throw new IllegalArgumentException(
                    "Chart repository URL must not contain userinfo (user:pass@host).");
        }
        if (pathStart == authStart) {
            throw new IllegalArgumentException("Chart repository URL host is missing.");
        }
        try {
            ConnectionTester.assertHostAllowed(clean, ConnectionTester.DnsPolicy.DEFER_UNKNOWN_HOST);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Chart repository URL rejected: " + e.getMessage(), e);
        }
        return clean;
    }

    private static String stripTrailingSlashes(String url) {
        String u = url;
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
