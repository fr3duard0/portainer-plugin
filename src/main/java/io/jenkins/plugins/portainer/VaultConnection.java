package io.jenkins.plugins.portainer;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.util.ArrayList;
import java.util.List;

/**
 * Nested Vault connection on Stack / Secret. Freestyle: {@code f:dropdownDescriptorSelector}.
 * Pipeline symbols: {@code vaultNone}, {@code vaultInherit}, {@code vaultManual}.
 */
public abstract class VaultConnection implements Describable<VaultConnection> {

    public abstract String getMode();

    public String getVaultUrl() {
        return null;
    }

    public String getVaultAppRoleCredentialsId() {
        return null;
    }

    public String getVaultPath() {
        return null;
    }

    public String getVaultMount() {
        return null;
    }

    public String getVaultNamespace() {
        return null;
    }

    public String getVaultVersion() {
        return null;
    }

    public final boolean isNone() {
        return ConnectionMode.isNone(getMode());
    }

    final VaultFields toFields(hudson.EnvVars buildEnv) {
        return VaultFields.parse(
                getVaultPath(),
                getVaultMount(),
                getVaultVersion(),
                getVaultNamespace(),
                getVaultUrl(),
                buildEnv);
    }

    static List<Descriptor<VaultConnection>> descriptors(boolean includeNone) {
        List<Descriptor<VaultConnection>> out = new ArrayList<>();
        for (Descriptor<VaultConnection> d : Jenkins.get().getDescriptorList(VaultConnection.class)) {
            if (!includeNone && d instanceof VaultNone.DescriptorImpl) {
                continue;
            }
            out.add(d);
        }
        return out;
    }

    static FormValidation checkUrl(String value) {
        if (value == null || value.isBlank()) {
            return FormValidation.ok();
        }
        try {
            VaultUrl.normalizeBaseUrlSyntaxOnly(value);
            return FormValidation.ok();
        } catch (IllegalArgumentException e) {
            return FormValidation.error(e.getMessage());
        }
    }

    static FormValidation checkPath(String value) {
        if (value == null || value.isBlank()) {
            return FormValidation.ok();
        }
        try {
            VaultClient.normalizeSecretPath(value);
            return FormValidation.ok();
        } catch (IllegalArgumentException e) {
            return FormValidation.error(e.getMessage());
        }
    }

    static FormValidation checkMount(String value) {
        if (value == null || value.isBlank()) {
            return FormValidation.ok();
        }
        try {
            VaultClient.normalizeMount(value);
            return FormValidation.ok();
        } catch (IllegalArgumentException e) {
            return FormValidation.error(e.getMessage());
        }
    }

    static FormValidation checkVersion(String value) {
        if (value == null || value.isBlank()) {
            return FormValidation.ok();
        }
        try {
            VaultClient.parseVersion(value);
            return FormValidation.ok();
        } catch (IllegalArgumentException e) {
            return FormValidation.error(e.getMessage());
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Inherit / Manual KV fields. */
    public abstract static class Kv extends VaultConnection {
        private String vaultPath;
        private String vaultMount;
        private String vaultNamespace;
        private String vaultVersion;

        @Override
        public String getVaultPath() {
            return vaultPath;
        }

        @DataBoundSetter
        public void setVaultPath(String vaultPath) {
            this.vaultPath = blankToNull(vaultPath);
        }

        @Override
        public String getVaultMount() {
            return vaultMount;
        }

        @DataBoundSetter
        public void setVaultMount(String vaultMount) {
            this.vaultMount = blankToNull(vaultMount);
        }

        @Override
        public String getVaultNamespace() {
            return vaultNamespace;
        }

        @DataBoundSetter
        public void setVaultNamespace(String vaultNamespace) {
            this.vaultNamespace = blankToNull(vaultNamespace);
        }

        @Override
        public String getVaultVersion() {
            return vaultVersion;
        }

        @DataBoundSetter
        public void setVaultVersion(String vaultVersion) {
            this.vaultVersion = blankToNull(vaultVersion);
        }
    }

    /**
     * Form checks for Inherit / Manual KV fields. Stapler binds {@code doCheck*} from this type
     * on subclass descriptors.
     */
    public abstract static class KvDescriptor extends Descriptor<VaultConnection> {

        /** Class whose views include {@code VaultConnection/Kv/common.jelly}. */
        public Class<?> getKvViewClass() {
            return Kv.class;
        }

        @POST
        public FormValidation doCheckVaultPath(@QueryParameter String value, @AncestorInPath Item item) {
            PortainerConnections.checkConfigure(item);
            return checkPath(value);
        }

        @POST
        public FormValidation doCheckVaultMount(@QueryParameter String value, @AncestorInPath Item item) {
            PortainerConnections.checkConfigure(item);
            return checkMount(value);
        }

        @POST
        public FormValidation doCheckVaultVersion(@QueryParameter String value, @AncestorInPath Item item) {
            PortainerConnections.checkConfigure(item);
            return checkVersion(value);
        }
    }
}
