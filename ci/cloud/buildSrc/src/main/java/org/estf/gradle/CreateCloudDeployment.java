package org.estf.gradle;

import co.elastic.cloud.api.client.generated.DeploymentsApi;
import co.elastic.cloud.api.model.generated.*;
import co.elastic.cloud.api.util.Waiter;
import com.bettercloud.vault.VaultException;
import io.swagger.client.ApiClient;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Optional;
import org.json.JSONObject;


import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
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
    public String remoteVersion = null;

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
    private String remoteDeploymentId = null;

    @Internal
    private String elasticsearchClusterId = null;

    @Internal
    private String remoteElasticsearchClusterId = null;

    @Internal
    private String kibanaClusterId = null;

    @Internal
    private String remoteKibanaClusterId = null;

    @Internal
    private String propertiesFile = null;

    @Internal
    private String remotePropertiesFile = null;

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

    @Internal
    private boolean remoteSetup = false;

    @TaskAction
    public void run() throws IOException, VaultException, URISyntaxException {

        if (stackVersion == null) {
            throw new Error("Environment variable: ESTF_CLOUD_VERSION is required");
        }

        // Setup cluster client
        CloudApi cloudApi = new CloudApi();
        ApiClient apiClient = cloudApi.getApiClient();

        // Create cluster
        esInstanceCfg = "aws.data.highcpu.m5d";
        kbnInstanceCfg = "aws.kibana.r5d";
        mlInstanceCfg = "aws.ml.m5d";
        ingestInstanceCfg = "aws.coordinating.m5d";
        deploymentTemplate = "aws-compute-optimized-v2";
        dataRegion = cloudApi.getEnvRegion();
        if (dataRegion != null) {
            if (dataRegion.contains("gcp")) {
                esInstanceCfg = "gcp.data.highcpu.1";
                kbnInstanceCfg = "gcp.kibana.1";
                mlInstanceCfg = "gcp.ml.1";
                ingestInstanceCfg = "gcp.coordinating.1";
                deploymentTemplate = "gcp-compute-optimized-v2";
            } else if (dataRegion.contains("azure")) {
                esInstanceCfg = "azure.data.highcpu.d64sv3";
                kbnInstanceCfg = "azure.kibana.e32sv3";
                mlInstanceCfg = "azure.ml.d64sv3";
                ingestInstanceCfg = "azure.coordinating.d64sv3";
                deploymentTemplate = "azure-compute-optimized-v2";
            }
        }

        DeploymentsApi deploymentsApi = new DeploymentsApi(apiClient);
        DeploymentCreateResponse response = createDeployment(cloudApi, deploymentsApi);
        generatePropertiesFile(response);
        if (remoteVersion != null) {
            remoteSetup = true;
            response = createDeployment(cloudApi, deploymentsApi);
            generatePropertiesFile(response);
            configureRemoteCluster();
        }
    }

    public String getEsUserSettings() { return esUserSettings; }

    public String getEsUserSettingsOverride() { return esUserSettingsOverride; }

    public String getKibanaUserSettingsOverride() { return kibanaUserSettingsOverride; }

    public String getKibanaUserSettings() { return kibanaUserSettings; }

    public boolean isMlTesting() { return mlTesting; }

    public boolean isIngestNodeTesting() { return ingestNodeTesting; }

    public boolean isKbnReportsTesting() { return kbnReportsTesting; }

    public boolean isRemoteSetup() { return remoteSetup; }

    public String getDeploymentId() { return deploymentId; }

    public String getElasticsearchClusterId() { return elasticsearchClusterId; }

    public String getKibanaClusterId() {
        return kibanaClusterId;
    }

    public String getPropertiesFile() {
        return propertiesFile;
    }

    public String getRemoteDeploymentId() {
        return remoteDeploymentId;
    }

    public String getRemoteElasticsearchClusterId() { return remoteElasticsearchClusterId; }

    public String getRemoteKibanaClusterId() {
        return remoteKibanaClusterId;
    }

    public String getRemotePropertiesFile() {
        return remotePropertiesFile;
    }

    public String getStackVersion() { return stackVersion; }

    public String getRemoteVersion() { return remoteVersion; }

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
        String esId = "";
        String kbnId = "";

        List<DeploymentResource> deploymentResourceList =  response.getResources();
        for (DeploymentResource resource : deploymentResourceList) {
            String kind = resource.getKind();
            if (kind.equals("elasticsearch")) {
                ClusterCredentials clusterCredentials = resource.getCredentials();
                esUser = clusterCredentials.getUsername();
                esPassword = clusterCredentials.getPassword();
                region = resource.getRegion();
                esId = resource.getId();
            } else if (kind.equals("kibana")) {
                kbnId = resource.getId();
            }
        }

        String id = deploymentId;
        if (isRemoteSetup()) {
            id = remoteDeploymentId;
            remoteElasticsearchClusterId = esId;
            remoteKibanaClusterId = kbnId;
        } else {
            elasticsearchClusterId = esId;
            kibanaClusterId = kbnId;
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

        String elasticsearch_url = String.format("https://%s.%s.%s.%s:%s", esId,
                region, provider, domain, port);
        String kibana_url = String.format("https://%s.%s.%s.%s:%s", kbnId,
                region, provider, domain, port);

        try {
            Properties properties = new Properties();
            properties.setProperty("deployment_id", id);
            properties.setProperty("elasticsearch_cluster_id", esId);
            properties.setProperty("es_username", esUser);
            properties.setProperty("es_password", esPassword);
            properties.setProperty("kibana_cluster_id", kbnId);
            properties.setProperty("elasticsearch_url", elasticsearch_url);
            properties.setProperty("kibana_url", kibana_url);
            String filename = PropFile.getFilename(id);
            if (isRemoteSetup()) {
                remotePropertiesFile = filename;
            } else {
               propertiesFile = filename;
            }
            File file = new File(filename);
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
        String deployVersion = stackVersion;
        if (isRemoteSetup()) {
            deployVersion = remoteVersion;
        }

        List<ElasticsearchClusterTopologyElement.NodeRolesEnum> masterNodeRoleList = new ArrayList<>();
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.MASTER);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.DATA_HOT);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.DATA_CONTENT);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.TRANSFORM);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.INGEST);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.REMOTE_CLUSTER_CLIENT);

        List<ElasticsearchClusterTopologyElement.NodeRolesEnum> ingestNodeRoleList = new ArrayList<>();
        ingestNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.INGEST);
        ingestNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.REMOTE_CLUSTER_CLIENT);

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
                .version(deployVersion);

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

        String deployVersion = stackVersion;
        if (isRemoteSetup()) {
            deployVersion = remoteVersion;
        }

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
            kbnTopology.size(getTopologySize(4096));
        }

        KibanaConfiguration kbnCfg = new KibanaConfiguration()
                .version(deployVersion);

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

        String id = response.getId();
        if (isRemoteSetup()) {
            remoteDeploymentId = id;
        } else {
            deploymentId = id;
        }

        Waiter.setWait(Duration.ofMinutes(20));
        cloudApi.waitForElasticsearch(deploymentsApi, id);
        cloudApi.waitForKibana(deploymentsApi, id);

        return response;
    }

    public void configureRemoteCluster()
            throws IOException, URISyntaxException {

        InputStream inDeployment = new FileInputStream(PropFile.getFilename(deploymentId));
        InputStream inRemoteDeployment = new FileInputStream(PropFile.getFilename(remoteDeploymentId));

        Properties propDeployment = new Properties();
        Properties remotePropDeployment = new Properties();

        propDeployment.load(inDeployment);
        remotePropDeployment.load(inRemoteDeployment);

        String username = propDeployment.getProperty("es_username");
        String password = propDeployment.getProperty("es_password");
        String kibanaUrl = propDeployment.getProperty("kibana_url");
        String remoteElasticsearchUrl = remotePropDeployment.getProperty("elasticsearch_url");

        String credentials = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());

        URI remoteElasticsearchUri = new URI(remoteElasticsearchUrl);
        String name = "ftr-remote";

        String serverName = remoteElasticsearchUri.getHost();
        String proxyAddress = remoteElasticsearchUri.getHost() + ":9400";

        String path = "/api/remote_clusters";
        String body = "{\"name\":\"" + name + "\"," +
                "\"skipUnavailable\":true," +
                "\"mode\":\"proxy\"," +
                "\"proxyAddress\":\"" +  proxyAddress + "\"," +
                "\"proxySocketConnections\":18," +
                "\"serverName\":\"" + serverName + "\"}";

        HttpPost postRequest = new HttpPost(kibanaUrl + path);
        postRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
        postRequest.setHeader("kbn-xsrf", "automation");
        postRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        StringEntity entity = new StringEntity(body);
        entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        postRequest.setEntity(entity);
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(postRequest);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            throw new IOException("FAILED! POST: " + response.getStatusLine() + " " + path);
        }
        String content = EntityUtils.toString(response.getEntity());
        JSONObject json = new JSONObject(content);
        boolean acknowledged = json.getBoolean("acknowledged");
        if (!acknowledged) {
            throw new IOException("Remote cluster failed!");
        }
    }
}
