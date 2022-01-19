package org.estf.gradle;

import co.elastic.cloud.api.client.generated.DeploymentsApi;
import co.elastic.cloud.api.model.generated.*;
import co.elastic.cloud.api.util.Waiter;
import com.bettercloud.vault.VaultException;
import io.swagger.client.ApiClient;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Optional;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * Creating cloud deployment
 *
 * @author  Liza Dayoub
 *
 */
public class CreateCloudDeployment extends DefaultTask {

    @Input
    public String stackVersion = "";

    @Input
    @Optional
    public String kibanaUserSettings = null;

    @Input
    @Optional
    public String esUserSettings = null;

    @Input
    @Optional
    public String esUserSettingsOverride = null;

    @Input
    @Optional
    public String kibanaUserSettingsOverride = null;

    @Input
    public boolean mlTesting = false;

    @Input
    public boolean ingestNodeTesting = false;

    @Input
    public boolean kbnReportsTesting = false;

    @Internal
    private String deploymentId = null;

    @Internal
    private String elasticsearchClusterId = null;

    @Internal
    private String kibanaClusterId = null;

    @Internal
    private String propertiesFile = null;

    @Internal
    private String esInstanceCfg = null;

    @Internal
    private String kbnInstanceCfg = null;

    @Internal
    private String mlInstanceCfg = null;

    @Internal
    private String ingestInstanceCfg = null;

    @Internal
    private String deploymentTemplate = null;

    @Internal
    private String dataRegion = null;

    @TaskAction
    public void run() throws IOException, VaultException {

        if (stackVersion == null) {
            throw new Error("Environment variable: ESTF_CLOUD_VERSION is required");
        }

        // Setup cluster client
        CloudApi cloudApi = new CloudApi();
        ApiClient apiClient = cloudApi.getApiClient();

        // Create cluster
        esInstanceCfg = "aws.highio.classic";
        kbnInstanceCfg = "aws.kibana.classic";
        mlInstanceCfg = "aws.ml.m5";
        ingestInstanceCfg = "aws.coordinating.m5";
        deploymentTemplate = "aws-compute-optimized-v2";
        dataRegion = cloudApi.getEnvRegion();
        if (dataRegion != null) {
            if (dataRegion.contains("gcp")) {
                esInstanceCfg = "gcp.highio.classic";
                kbnInstanceCfg = "gcp.kibana.classic";
                mlInstanceCfg = "gcp.ml.1";
                ingestInstanceCfg = "gcp.coordinating.1";
                deploymentTemplate = "gcp-storage-optimized";
            } else if (dataRegion.contains("azure")) {
                esInstanceCfg = "azure.master.e32sv3";
                kbnInstanceCfg = "azure.kibana.e32sv3";
                mlInstanceCfg = "azure.ml.d64sv3";
                ingestInstanceCfg = "azure.coordinating.d64sv3";
                deploymentTemplate = "azure-compute-optimized-v2";
            }
        }

        DeploymentsApi deploymentsApi = new DeploymentsApi(apiClient);
        DeploymentCreateResponse response = createDeployment(cloudApi, deploymentsApi);
        generatePropertiesFile(response);

    }

    public String getEsUserSettings() { return esUserSettings; }

    public String getEsUserSettingsOverride() { return esUserSettingsOverride; }

    public String getKibanaUserSettingsOverride() { return kibanaUserSettingsOverride; }

    public String getKibanaUserSettings() { return kibanaUserSettings; }

    public boolean isMlTesting() { return mlTesting; }

    public boolean isIngestNodeTesting() { return ingestNodeTesting; }

    public boolean isKbnReportsTesting() { return kbnReportsTesting; }

    public String getDeploymentId() {
        return deploymentId;
    }

    public String getElasticsearchClusterId() { return elasticsearchClusterId; }

    public String getKibanaClusterId() {
        return kibanaClusterId;
    }

    public String getPropertiesFile() {
        return propertiesFile;
    }

    public String getStackVersion() { return stackVersion; }

    public String getEsInstanceCfg() { return esInstanceCfg; }

    public String getKbnInstanceCfg() { return kbnInstanceCfg; }

    public String getMlInstanceCfg() { return mlInstanceCfg; }

    public String getIngestInstanceCfg() { return ingestInstanceCfg; }

    public String getDeploymentTemplate() { return deploymentTemplate; }

    public String getDataRegion() { return dataRegion; }

    private void generatePropertiesFile(DeploymentCreateResponse response) {
        String esUser = "";
        String esPassword = "";
        String region = "";

        List<DeploymentResource> deploymentResourceList =  response.getResources();
        for (DeploymentResource resource : deploymentResourceList) {
            String kind = resource.getKind();
            if (kind.equals("elasticsearch")) {
                ClusterCredentials clusterCredentials = resource.getCredentials();
                esUser = clusterCredentials.getUsername();
                esPassword = clusterCredentials.getPassword();
                region = resource.getRegion();
                elasticsearchClusterId = resource.getId();
            } else if (kind.equals("kibana")) {
                kibanaClusterId = resource.getId();
            }
        }

        String domain = "foundit.no";
        String port = "9243";
        String provider = "aws.staging";
        if (region.contains("gcp")) {
            provider = "gcp";
            region = region.replace("gcp-","");
        } else if (region.contains("azure")) {
            provider = "staging.azure";
            region = region.replace("azure-","");
        } else if (region.contains("aws-eu-central-1")) {
            provider = "aws";
            region = "eu-central-1";
        }

        String elasticsearch_url = String.format("https://%s.%s.%s.%s:%s", elasticsearchClusterId,
                region, provider, domain, port);
        String kibana_url = String.format("https://%s.%s.%s.%s:%s", kibanaClusterId,
                region, provider, domain, port);

        try {
            Properties properties = new Properties();
            properties.setProperty("deployment_id", deploymentId);
            properties.setProperty("elasticsearch_cluster_id", elasticsearchClusterId);
            properties.setProperty("es_username", esUser);
            properties.setProperty("es_password", esPassword);
            properties.setProperty("kibana_cluster_id", kibanaClusterId);
            properties.setProperty("elasticsearch_url", elasticsearch_url);
            properties.setProperty("kibana_url", kibana_url);
            propertiesFile = PropFile.getFilename(deploymentId);
            File file = new File(propertiesFile);
            FileOutputStream fileOut = new FileOutputStream(file);
            properties.store(fileOut, "Cloud Cluster Info");
            fileOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private TopologySize getTopologySize() {
        return getTopologySize(1024);
    }

    private TopologySize getTopologySize(int size) {
        return new TopologySize()
                .value(size)
                .resource(TopologySize.ResourceEnum.MEMORY);
    }

    private ElasticsearchPayload getElasticsearchPayload(CloudApi api) {

        List<ElasticsearchClusterTopologyElement.NodeRolesEnum> masterNodeRoleList = new ArrayList<>();
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.MASTER);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.DATA_HOT);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.DATA_CONTENT);

        List<ElasticsearchClusterTopologyElement.NodeRolesEnum> ingestNodeRoleList = new ArrayList<>();
        ingestNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.INGEST);

        List<ElasticsearchClusterTopologyElement.NodeRolesEnum> mlNodeRoleList = new ArrayList<>();
        mlNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.ML);

        ElasticsearchClusterTopologyElement esTopology = new ElasticsearchClusterTopologyElement()
                .instanceConfigurationId(esInstanceCfg)
                .zoneCount(1)
                .size(getTopologySize(8192));

        ElasticsearchClusterTopologyElement ingestTopology = new ElasticsearchClusterTopologyElement()
                .instanceConfigurationId(ingestInstanceCfg)
                .zoneCount(1)
                .size(getTopologySize());

        ElasticsearchClusterTopologyElement mlTopology = new ElasticsearchClusterTopologyElement()
                .instanceConfigurationId(mlInstanceCfg)
                .zoneCount(1)
                .size(getTopologySize());

        esTopology.id("hot_content");
        esTopology.nodeRoles(masterNodeRoleList);
        ingestTopology.id("coordinating");
        ingestTopology.nodeRoles(ingestNodeRoleList);
        mlTopology.id("ml");
        mlTopology.nodeRoles(mlNodeRoleList);

        ElasticsearchConfiguration esCfg = new ElasticsearchConfiguration()
                .version(stackVersion);

        if (getEsUserSettings() != null) {
            esCfg.userSettingsYaml(esUserSettings);
        }

        if (getEsUserSettingsOverride() != null) {
            esCfg.setUserSettingsOverrideYaml(esUserSettingsOverride);
        }

        DeploymentTemplateReference templateRef = new DeploymentTemplateReference()
                .id(deploymentTemplate);

        ElasticsearchClusterPlan plan = new ElasticsearchClusterPlan()
                .elasticsearch(esCfg)
                .deploymentTemplate(templateRef);

        if (isMlTesting() && isIngestNodeTesting()) {
            plan.clusterTopology(Arrays.asList(esTopology, mlTopology, ingestTopology));
        } else if (isMlTesting()) {
            plan.clusterTopology(Arrays.asList(esTopology, mlTopology));
        } else if (isIngestNodeTesting()) {
            plan.clusterTopology(Arrays.asList(esTopology, ingestTopology));
        } else {
            plan.clusterTopology(Collections.singletonList(esTopology));
        }

        return new ElasticsearchPayload()
                .plan(plan)
                .region(dataRegion)
                .refId(api.getEsRefId());
    }

    private KibanaPayload getKibanaPayload(CloudApi api) {

        int kibanaZone;
        try {
            kibanaZone = Integer.parseInt(System.getenv("ESTF_CLOUD_KIBANA_ZONE"));
        } catch (NumberFormatException e) {
            kibanaZone = 1;
        }

        KibanaClusterTopologyElement kbnTopology = new KibanaClusterTopologyElement()
                .instanceConfigurationId(kbnInstanceCfg)
                .zoneCount(kibanaZone);

        if (isKbnReportsTesting()) {
            kbnTopology.size(getTopologySize(8192));
        } else {
            kbnTopology.size(getTopologySize());
        }

        KibanaConfiguration kbnCfg = new KibanaConfiguration()
                .version(stackVersion);

        if (getKibanaUserSettings() != null) {
            kbnCfg.userSettingsYaml(kibanaUserSettings);
        }

        if (getKibanaUserSettingsOverride() != null) {
            kbnCfg.setUserSettingsOverrideYaml(kibanaUserSettingsOverride);
        }

        KibanaClusterPlan kbnPlan = new KibanaClusterPlan()
                .kibana(kbnCfg)
                .clusterTopology(Collections.singletonList(kbnTopology));

        return new KibanaPayload()
                .elasticsearchClusterRefId(api.getEsRefId())
                .refId(api.getKbRefId())
                .plan(kbnPlan)
                .region(dataRegion);
    }

    private DeploymentCreateResponse createDeployment(CloudApi cloudApi, DeploymentsApi deploymentsApi) {

        DeploymentCreateResources deploymentCreateResources = new DeploymentCreateResources()
                .addElasticsearchItem(getElasticsearchPayload(cloudApi))
                .addKibanaItem(getKibanaPayload(cloudApi));

        DeploymentCreateResponse response = deploymentsApi.createDeployment(
                new DeploymentCreateRequest()
                        .name("ESTF_Deployment__" + UUID.randomUUID())
                        .resources(deploymentCreateResources),
                "estf_request_id_" + UUID.randomUUID(),
                false);

        deploymentId = response.getId();

        Waiter.setWait(Duration.ofMinutes(20));
        cloudApi.waitForElasticsearch(deploymentsApi, deploymentId);
        cloudApi.waitForKibana(deploymentsApi, deploymentId);

        return response;
    }
}
