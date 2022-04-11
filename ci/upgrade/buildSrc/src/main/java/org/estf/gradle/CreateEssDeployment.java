package org.estf.gradle;

import co.elastic.cloud.api.client.generated.DeploymentsApi;
import co.elastic.cloud.api.model.generated.*;
import co.elastic.cloud.api.util.Waiter;
import com.bettercloud.vault.VaultException;
import io.swagger.client.ApiClient;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * CreateEssDeployment
 *
 * @author  Liza Dayoub
 *
 */
public class CreateEssDeployment extends DefaultTask {

    @Input
    public String stackVersion;

    @Input
    public String elasticsearchUserSettings;

    @Input
    public String kibanaUserSettings;

    @Input
    public boolean mlNode = false;

    @Input
    public boolean ingestNode = false;

    @Input
    public boolean apmNode = false;

    @Input
    public boolean enterpriseSearchNode = false;

    private String deploymentId;
    private String elasticsearchClusterId;
    private String kibanaClusterId;
    private String propertiesFile;

    private String dataRegion;
    private String esInstanceCfg;
    private String kbnInstanceCfg;
    private String mlInstanceCfg;
    private String ingestInstanceCfg;
    private String apmInstanceCfg;
    private String enterpriseSearchInstanceCfg;

    private String deploymentTemplate;

    private boolean isPre7_10 = false;

    @TaskAction
    public void run() throws IOException, VaultException, InterruptedException {
        if (stackVersion == null) {
            throw new Error(this.getClass().getSimpleName() + ": stackVersion is required input");
        }

        ComparableVersion currentVersion = new ComparableVersion(stackVersion);
        ComparableVersion version7_11 = new ComparableVersion("7.11.0");

        if (currentVersion.compareTo(version7_11) < 0) {
            isPre7_10 = true;
        }

        CloudApi cloudApi = new CloudApi();
        ApiClient apiClient = cloudApi.getApiClient();
        setInstanceConfiguration(cloudApi);
        DeploymentsApi deploymentsApi = new DeploymentsApi(apiClient);
        DeploymentCreateResponse response = createDeployment(cloudApi, deploymentsApi);
        generatePropertiesFile(response, deploymentsApi);
        StackStatus stackStatus = new StackStatus(deploymentId);
        stackStatus.isKibanaHealthy();
        stackStatus.isElasticsearchHealthy();
        System.out.println("Create deployment " + getDeploymentId() + " completed successfully");
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public String getElasticsearchClusterId() {
        return elasticsearchClusterId;
    }

    public String getKibanaClusterId() {
        return kibanaClusterId;
    }

    public String getPropertiesFile() {
        return propertiesFile;
    }

    private void setInstanceConfiguration(CloudApi cloudApi) {
        esInstanceCfg = "aws.data.highcpu.m5d";
        kbnInstanceCfg = "aws.kibana.r5d";
        mlInstanceCfg = "aws.ml.m5d";
        ingestInstanceCfg = "aws.coordinating.m5d";
        apmInstanceCfg = "aws.apm.r5d";
        enterpriseSearchInstanceCfg = "aws.enterprisesearch.m5d";
        deploymentTemplate = "aws-compute-optimized-v2";
        dataRegion = cloudApi.getEnvRegion();
        if (dataRegion != null) {
            if (dataRegion.contains("gcp")) {
                esInstanceCfg = "gcp.data.highcpu.1";
                kbnInstanceCfg = "gcp.kibana.1";
                mlInstanceCfg = "gcp.ml.1";
                ingestInstanceCfg = "gcp.coordinating.1";
                apmInstanceCfg = "gcp.apm.1";
                enterpriseSearchInstanceCfg = "gcp.enterprisesearch.1";
                deploymentTemplate = "gcp-compute-optimized-v2";
            } else if (dataRegion.contains("azure")) {
                esInstanceCfg = "azure.data.highcpu.d64sv3";
                kbnInstanceCfg = "azure.kibana.e32sv3";
                mlInstanceCfg = "azure.ml.d64sv3";
                ingestInstanceCfg = "azure.coordinating.d64sv3";
                apmInstanceCfg = "azure.apm.e32sv3";
                enterpriseSearchInstanceCfg = "azure.enterprisesearch.d64sv3";
                deploymentTemplate = "azure-compute-optimized-v2";
            }
        }
    }

    private void generatePropertiesFile(DeploymentCreateResponse response, DeploymentsApi deploymentsApi) {
        String esUser = null;
        String esPassword = null;
        String elasticsearch_url = null;
        String kibana_url = null;

        List<DeploymentResource> deploymentResourceList =  response.getResources();
        for (DeploymentResource resource : deploymentResourceList) {
            String kind = resource.getKind();
            if (kind.equals("elasticsearch")) {
                ClusterCredentials clusterCredentials = resource.getCredentials();
                esUser = clusterCredentials.getUsername();
                esPassword = clusterCredentials.getPassword();
                elasticsearchClusterId = resource.getId();
                ElasticsearchResourceInfo esResourceInfo = deploymentsApi.getDeploymentEsResourceInfo(deploymentId,
                        resource.getRefId(),
                        false,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        0,
                        false,
                        false);
                elasticsearch_url = esResourceInfo.getInfo().getMetadata().getServiceUrl();
            } else if (kind.equals("kibana")) {
                kibanaClusterId = resource.getId();
                KibanaResourceInfo kbnResourceInfo = deploymentsApi.getDeploymentKibResourceInfo(deploymentId,
                        resource.getRefId(),
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false);
                kibana_url = kbnResourceInfo.getInfo().getMetadata().getServiceUrl();
            }
        }

        try {
            Properties properties = new Properties();
            properties.setProperty("deployment_id", deploymentId);
            properties.setProperty("elasticsearch_cluster_id", elasticsearchClusterId);
            properties.setProperty("es_username", esUser);
            properties.setProperty("es_password", esPassword);
            properties.setProperty("kibana_cluster_id", kibanaClusterId);
            properties.setProperty("elasticsearch_url", elasticsearch_url);
            properties.setProperty("kibana_url", kibana_url);
            propertiesFile = DeploymentFile.getFilename(deploymentId);
            File file = new File(propertiesFile);
            FileOutputStream fileOut = new FileOutputStream(file);
            properties.store(fileOut, "Cloud Deployment Info");
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

        ElasticsearchNodeType esNodeType = new ElasticsearchNodeType().data(true).master(true);
        ElasticsearchNodeType ingestNodeType = new ElasticsearchNodeType().ingest(true);
        ElasticsearchNodeType mlNodeType = new ElasticsearchNodeType().ml(true);

        List<ElasticsearchClusterTopologyElement.NodeRolesEnum> masterNodeRoleList = new ArrayList<>();
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.MASTER);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.DATA_HOT);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.DATA_CONTENT);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.TRANSFORM);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.INGEST);
        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.REMOTE_CLUSTER_CLIENT);

        List<ElasticsearchClusterTopologyElement.NodeRolesEnum> ingestNodeRoleList = new ArrayList<>();
        ingestNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.INGEST);

        List<ElasticsearchClusterTopologyElement.NodeRolesEnum> mlNodeRoleList = new ArrayList<>();
        mlNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.ML);

        ElasticsearchClusterTopologyElement esTopology = new ElasticsearchClusterTopologyElement()
                .instanceConfigurationId(esInstanceCfg)
                .zoneCount(2)
                .size(getTopologySize(8192));

        ElasticsearchClusterTopologyElement ingestTopology = new ElasticsearchClusterTopologyElement()
                .instanceConfigurationId(ingestInstanceCfg)
                .zoneCount(1)
                .size(getTopologySize());

        ElasticsearchClusterTopologyElement mlTopology = new ElasticsearchClusterTopologyElement()
                .instanceConfigurationId(mlInstanceCfg)
                .zoneCount(1)
                .size(getTopologySize());

        if (isPre7_10) {
            esTopology.nodeType(esNodeType);
            ingestTopology.nodeType(ingestNodeType);
            mlTopology.nodeType(mlNodeType);
        } else {
            esTopology.id("hot_content");
            esTopology.nodeRoles(masterNodeRoleList);
            ingestTopology.id("coordinating");
            ingestTopology.nodeRoles(ingestNodeRoleList);
            mlTopology.id("ml");
            mlTopology.nodeRoles(mlNodeRoleList);
        }

        ElasticsearchConfiguration esCfg = new ElasticsearchConfiguration()
                .version(stackVersion);

        if (elasticsearchUserSettings != null) {
            esCfg.userSettingsYaml(elasticsearchUserSettings);
        }

        DeploymentTemplateReference templateRef = new DeploymentTemplateReference()
                .id(deploymentTemplate);

        ElasticsearchClusterPlan plan = new ElasticsearchClusterPlan()
                .elasticsearch(esCfg)
                .deploymentTemplate(templateRef);

        if (mlNode && ingestNode) {
            plan.clusterTopology(Arrays.asList(esTopology, mlTopology, ingestTopology));
        } else if (mlNode) {
            plan.clusterTopology(Arrays.asList(esTopology, mlTopology));
        } else if (ingestNode) {
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

        KibanaClusterTopologyElement kbnTopology = new KibanaClusterTopologyElement()
                .instanceConfigurationId(kbnInstanceCfg)
                .zoneCount(1)
                .size(getTopologySize(16384));

        KibanaConfiguration kbnCfg = new KibanaConfiguration()
                .version(stackVersion);

        if (kibanaUserSettings != null) {
            kbnCfg.userSettingsYaml(kibanaUserSettings);
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

    private ApmPayload getApmPayload(CloudApi api) {

        ApmTopologyElement apmTopology = new ApmTopologyElement()
                .instanceConfigurationId(apmInstanceCfg)
                .zoneCount(1)
                .size(getTopologySize(1024));

        ApmConfiguration apmCfg = new ApmConfiguration()
                .version(stackVersion);

        ApmPlan apmPlan = new ApmPlan()
                .apm(apmCfg)
                .clusterTopology(Collections.singletonList(apmTopology));

        return new ApmPayload()
                .elasticsearchClusterRefId(api.getEsRefId())
                .refId(api.getApmRefId())
                .plan(apmPlan)
                .region(dataRegion);
    }

    private EnterpriseSearchPayload getEnterpriseSearchPayload(CloudApi api) {

        EnterpriseSearchNodeTypes enterpriseSearchNodeTypes = new EnterpriseSearchNodeTypes()
                .appserver(true)
                .worker(true)
                .connector(true);

        EnterpriseSearchTopologyElement enterpriseSearchTopologyElementTopology = new EnterpriseSearchTopologyElement()
                .instanceConfigurationId(enterpriseSearchInstanceCfg)
                .nodeType(enterpriseSearchNodeTypes)
                .zoneCount(1)
                .size(getTopologySize(2048));

        EnterpriseSearchConfiguration ensCfg = new EnterpriseSearchConfiguration()
                .version(stackVersion);

        EnterpriseSearchPlan ensPlan = new EnterpriseSearchPlan()
                .enterpriseSearch(ensCfg)
                .clusterTopology(Collections.singletonList(enterpriseSearchTopologyElementTopology));

        return new EnterpriseSearchPayload()
                .elasticsearchClusterRefId(api.getEsRefId())
                .refId(api.getEnsRefId())
                .plan(ensPlan)
                .region(dataRegion);
    }

    private DeploymentCreateResponse createDeployment(CloudApi cloudApi, DeploymentsApi deploymentsApi) {

        DeploymentCreateResources deploymentCreateResources = new DeploymentCreateResources()
                .addElasticsearchItem(getElasticsearchPayload(cloudApi))
                .addKibanaItem(getKibanaPayload(cloudApi));

        if (apmNode) {
            deploymentCreateResources.addApmItem(getApmPayload(cloudApi));
        }

        if (enterpriseSearchNode) {
            deploymentCreateResources.addEnterpriseSearchItem(getEnterpriseSearchPayload(cloudApi));
        }

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

        if (apmNode) {
            cloudApi.waitForApm(deploymentsApi, deploymentId);
        }

        if (enterpriseSearchNode) {
            cloudApi.waitForEnterpriseSearch(deploymentsApi, deploymentId);
        }

        return response;
    }
}
