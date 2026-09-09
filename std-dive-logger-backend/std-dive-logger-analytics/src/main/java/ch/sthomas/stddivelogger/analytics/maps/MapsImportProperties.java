package ch.sthomas.stddivelogger.analytics.maps;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("maps.import")
public class MapsImportProperties {
    private boolean kubernetesEnabled;
    private String namespace = "default";
    private String sourceUrl = "https://download.geofabrik.de/europe/switzerland-latest.osm.pbf";
    private String sourceChecksum = "";
    private String databaseSecretName = "std-dive-logger-db-app";
    private String configPath = "/maps-import-config";

    public boolean isKubernetesEnabled() {
        return kubernetesEnabled;
    }

    public void setKubernetesEnabled(final boolean kubernetesEnabled) {
        this.kubernetesEnabled = kubernetesEnabled;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(final String namespace) {
        this.namespace = namespace;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(final String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceChecksum() {
        return sourceChecksum;
    }

    public void setSourceChecksum(final String sourceChecksum) {
        this.sourceChecksum = sourceChecksum;
    }

    public String getDatabaseSecretName() {
        return databaseSecretName;
    }

    public void setDatabaseSecretName(final String databaseSecretName) {
        this.databaseSecretName = databaseSecretName;
    }

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(final String configPath) {
        this.configPath = configPath;
    }
}
