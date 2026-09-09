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
    private String cgazAdm0Url =
            "https://github.com/wmgeolab/geoBoundaries/raw/main/releaseData/CGAZ/geoBoundariesCGAZ_ADM0.gpkg";
    private String cgazAdm1Url =
            "https://github.com/wmgeolab/geoBoundaries/raw/main/releaseData/CGAZ/geoBoundariesCGAZ_ADM1.gpkg";

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

    public String getCgazAdm0Url() {
        return cgazAdm0Url;
    }

    public void setCgazAdm0Url(final String cgazAdm0Url) {
        this.cgazAdm0Url = cgazAdm0Url;
    }

    public String getCgazAdm1Url() {
        return cgazAdm1Url;
    }

    public void setCgazAdm1Url(final String cgazAdm1Url) {
        this.cgazAdm1Url = cgazAdm1Url;
    }

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(final String configPath) {
        this.configPath = configPath;
    }
}
