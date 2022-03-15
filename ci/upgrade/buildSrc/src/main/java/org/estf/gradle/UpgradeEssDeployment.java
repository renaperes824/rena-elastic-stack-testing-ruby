package org.estf.gradle;

import co.elastic.cloud.api.client.generated.DeploymentsApi;
import co.elastic.cloud.api.model.generated.*;
import co.elastic.cloud.api.util.Waiter;
import com.bettercloud.vault.VaultException;
import io.swagger.client.ApiClient;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Input;

import org.apache.maven.artifact.versioning.ComparableVersion;


/**
 * UpgradeEssDeployment
 *
 * @author  Liza Dayoub
 *
 */
public class UpgradeEssDeployment extends DefaultTask {

    @Input
    public  String deploymentId = null;

    @Input
    public String upgradeStackVersion = null;

    private boolean isPre7_10 = false;

    @TaskAction
    public void run() throws IOException, VaultException, InterruptedException {
        if (deploymentId == null || deploymentId.trim().isEmpty()) {
            throw new Error(this.getClass().getSimpleName() + ": deploymentId is required input");
        }

        if (upgradeStackVersion == null || upgradeStackVersion.trim().isEmpty()) {
            throw new Error(this.getClass().getSimpleName() + ": upgradeStackVersion is required input");
        }

        CloudApi cloudApi = new CloudApi();
        ApiClient apiClient = cloudApi.getApiClient();
        DeploymentsApi deploymentsApi = new DeploymentsApi(apiClient);

        ComparableVersion currentVersion = new ComparableVersion(getElasticsearchVersion(cloudApi, deploymentsApi));
        ComparableVersion version7_11 = new ComparableVersion("7.11.0");
        if (currentVersion.compareTo(version7_11) < 0) {
            isPre7_10 = true;
        }

        updateDeployment(cloudApi, deploymentsApi);
        StackStatus stackStatus = new StackStatus(deploymentId);
        stackStatus.isKibanaHealthy();
        stackStatus.isElasticsearchHealthy();
        System.out.println("Upgrade deployment "  + deploymentId + " to " + upgradeStackVersion + " completed successfully");
    }

    private void updateDeployment(CloudApi cloudApi, DeploymentsApi deploymentsApi) {

        DeploymentGetResponse deploymentGetResponse = deploymentsApi.getDeployment(deploymentId,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                0,
                true,
                false);

        ElasticsearchResourceInfo esResourceInfo = deploymentsApi.getDeploymentEsResourceInfo(deploymentId,
                cloudApi.getEsRefId(),
                false,
                true,
                true,
                false,
                false,
                true,
                false,
                0,
                true,
                false);

        ElasticsearchClusterPlan esPlan;
        if (isPre7_10) {
            esPlan = esResourceInfo.getInfo().getPlanInfo().getCurrent().getPlan();

            for (int i=0; i < esPlan.getClusterTopology().size(); i++) {

                ElasticsearchSystemSettings elasticsearchSystemSettings = esPlan.getClusterTopology()
                        .get(i)
                        .getElasticsearch()
                        .getSystemSettings();

                ElasticsearchScriptTypeSettings typeSetting = new ElasticsearchScriptTypeSettings()
                        .enabled(true)
                        .sandboxMode(null);

                ElasticsearchScriptingUserSettings elasticsearchScriptingSettings = new ElasticsearchScriptingUserSettings()
                        .expressionsEnabled(null)
                        .file(null)
                        .inline(typeSetting)
                        .stored(typeSetting)
                        .mustacheEnabled(null)
                        .painlessEnabled(null);

                ElasticsearchConfiguration esCfg = esPlan.getElasticsearch()
                        .version(upgradeStackVersion)
                        .systemSettings(elasticsearchSystemSettings
                                .scripting(elasticsearchScriptingSettings)
                                .watcherTriggerEngine(null));

                esPlan.getClusterTopology().get(i).setElasticsearch(esCfg);
            }

        } else {
            esPlan = deploymentGetResponse
                        .getResources()
                        .getElasticsearch()
                        .get(0)
                        .getInfo()
                        .getPlanInfo()
                        .getCurrent()
                        .getPlan();

            for (int i=0; i < esPlan.getClusterTopology().size(); i++) {
                ElasticsearchClusterTopologyElement clusterTopologyElement = esPlan.getClusterTopology().get(i);
                ElasticsearchNodeType nodeType = clusterTopologyElement.getNodeType();
                if (nodeType != null) {
                    System.out.println("Migrate from nodeType");
                    if (nodeType.isMl()) {
                        List<ElasticsearchClusterTopologyElement.NodeRolesEnum> mlNodeRoleList = new ArrayList<>();
                        mlNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.ML);
                        clusterTopologyElement.id("ml");
                        clusterTopologyElement.nodeRoles(mlNodeRoleList);
                    } else if (nodeType.isIngest()) {
                        List<ElasticsearchClusterTopologyElement.NodeRolesEnum> ingestNodeRoleList = new ArrayList<>();
                        ingestNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.INGEST);
                        clusterTopologyElement.id("coordinating");
                        clusterTopologyElement.nodeRoles(ingestNodeRoleList);
                    } else if (nodeType.isMaster()) {
                        List<ElasticsearchClusterTopologyElement.NodeRolesEnum> masterNodeRoleList = new ArrayList<>();
                        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.MASTER);
                        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.DATA_HOT);
                        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.DATA_CONTENT);
                        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.TRANSFORM);
                        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.INGEST);
                        masterNodeRoleList.add(ElasticsearchClusterTopologyElement.NodeRolesEnum.REMOTE_CLUSTER_CLIENT);
                        clusterTopologyElement.id("hot_content");
                        clusterTopologyElement.nodeRoles(masterNodeRoleList);
                    }
                    clusterTopologyElement.setNodeType(null);
                }
                ElasticsearchConfiguration esCfg = esPlan.getElasticsearch().version(upgradeStackVersion);
                clusterTopologyElement.setElasticsearch(esCfg);
            }
        }

        KibanaResourceInfo kbnResourceInfo = deploymentsApi.getDeploymentKibResourceInfo(deploymentId,
                cloudApi.getKbRefId(),
                false,
                true,
                true,
                false,
                false,
                false,
                false);

        KibanaClusterPlan kbnPlan =  kbnResourceInfo.getInfo().getPlanInfo().getCurrent().getPlan();

        ApmResourceInfo apmResourceInfo = null;
        ApmPlan apmPlan = null;
        try {
            apmResourceInfo = deploymentsApi.getDeploymentApmResourceInfo(deploymentId,
                    cloudApi.getApmRefId(),
                    false,
                    true,
                    true,
                    false,
                    false,
                    false);

            apmPlan = apmResourceInfo.getInfo().getPlanInfo().getCurrent().getPlan();

        } catch (Exception ignored) {}

        EnterpriseSearchResourceInfo ensResourceInfo = null;
        EnterpriseSearchPlan ensPlan = null;
        try {
            ensResourceInfo = deploymentsApi.getDeploymentEnterpriseSearchResourceInfo(deploymentId,
                    cloudApi.getEnsRefId(),
                    false,
                    true,
                    true,
                    false,
                    false,
                    false);

            ensPlan = ensResourceInfo.getInfo().getPlanInfo().getCurrent().getPlan();

        } catch(Exception ignored) {}

        DeploymentUpdateResources deploymentUpdateResources = new DeploymentUpdateResources()
            .addElasticsearchItem(new ElasticsearchPayload()
                .region(esResourceInfo.getRegion())
                .refId(esResourceInfo.getRefId())
                .plan(esPlan))
            .addKibanaItem(new KibanaPayload()
                .elasticsearchClusterRefId(kbnResourceInfo.getElasticsearchClusterRefId())
                .region(kbnResourceInfo.getRegion())
                .refId(kbnResourceInfo.getRefId())
                .plan(kbnPlan));

        if (apmPlan != null) {
            deploymentUpdateResources.addApmItem(new ApmPayload()
                .elasticsearchClusterRefId(apmResourceInfo.getElasticsearchClusterRefId())
                .region(apmResourceInfo.getRegion())
                .refId(apmResourceInfo.getRefId())
                .plan(apmPlan));
        }

        if (ensPlan != null) {
            deploymentUpdateResources.addEnterpriseSearchItem(new EnterpriseSearchPayload()
                .elasticsearchClusterRefId(ensResourceInfo.getElasticsearchClusterRefId())
                .region(ensResourceInfo.getRegion())
                .refId(ensResourceInfo.getRefId())
                .plan(ensPlan));
        }

        Waiter.setWait(Duration.ofMinutes(20));

        // Upgrade Elasticsearch
        DeploymentUpdateRequest deploymentUpdateRequest = new DeploymentUpdateRequest()
            .name(deploymentGetResponse.getName())
            .pruneOrphans(true)
            .resources(deploymentUpdateResources);

        deploymentsApi.updateDeployment(deploymentId,
            deploymentUpdateRequest,
            false,
            true,
            false,
            null);

        cloudApi.waitForElasticsearch(deploymentsApi, deploymentId);
        cloudApi.waitForKibana(deploymentsApi, deploymentId);

        // Check Elasticsearch
        checkElasticsearch(cloudApi, deploymentsApi);

        // Upgrade Kibana
        deploymentsApi.upgradeDeploymentStatelessResource(deploymentId,
            "kibana",
            cloudApi.getKbRefId(),
            false);
        cloudApi.waitForKibana(deploymentsApi, deploymentId);

        // Check Kibana
        checkKibana(cloudApi, deploymentsApi);

        if (apmPlan != null) {
            // Upgrade APM
            deploymentsApi.upgradeDeploymentStatelessResource(deploymentId,
                "apm",
                cloudApi.getApmRefId(),
                false);
            cloudApi.waitForApm(deploymentsApi, deploymentId);

            // Check APM
            checkApm(cloudApi, deploymentsApi);
        }

        if (ensPlan != null) {
            // Upgrade Enterprise Search
            deploymentsApi.upgradeDeploymentStatelessResource(deploymentId,
                "enterprise_search",
                cloudApi.getEnsRefId(),
                false);
            cloudApi.waitForEnterpriseSearch(deploymentsApi, deploymentId);

            // Check Enterprise Search
            checkEnterpriseSearch(cloudApi, deploymentsApi);
        }
    }

    private String getElasticsearchVersion(CloudApi cloudApi, DeploymentsApi deploymentsApi) {
        return deploymentsApi.getDeploymentEsResourceInfo(
                    deploymentId,
                    cloudApi.getEsRefId(),
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    0,
                    false,
                    false)
                .getInfo()
                .getTopology()
                .getInstances()
                .get(0)
                .getServiceVersion();
    }

    private void checkElasticsearch(CloudApi cloudApi, DeploymentsApi deploymentsApi) {
        System.out.println("Checking Elasticsearch has been upgraded and is healthy");
        ElasticsearchResourceInfo esResourceInfo = deploymentsApi.getDeploymentEsResourceInfo(
                deploymentId,
                cloudApi.getEsRefId(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                false,
                false);

        boolean isEsHealthy = esResourceInfo.getInfo().isHealthy();

        List<ClusterInstanceInfo> esInstances = esResourceInfo.getInfo().getTopology().getInstances();

        Boolean[] versionMatches = new Boolean[esInstances.size()];

        for (int i=0; i < esInstances.size(); i++) {
            versionMatches[i] = true;
            String esServiceVersion = esInstances.get(i).getServiceVersion();
            if (!esServiceVersion.equals(upgradeStackVersion)) {
                versionMatches[i] = false;
            }
        }

        if (Arrays.asList(versionMatches).contains(false)) {
            throw new Error("Expected all Elasticsearch instances to match version " +
                            upgradeStackVersion + " got " + Arrays.toString(versionMatches));
        }

        if (!isEsHealthy) {
            throw new Error("Elasticsearch is not healthy");
        }
    }

    private void checkKibana(CloudApi cloudApi, DeploymentsApi deploymentsApi) {
        System.out.println("Checking Kibana has been upgraded and is healthy");
        KibanaResourceInfo kbnResourceInfo = deploymentsApi.getDeploymentKibResourceInfo(
                deploymentId,
                cloudApi.getKbRefId(),
                false,
                false,
                false,
                false,
                false,
                false,
                false);

        boolean isKbnHealthy = kbnResourceInfo.getInfo().isHealthy();

        List<ClusterInstanceInfo> kbnInstances = kbnResourceInfo.getInfo().getTopology().getInstances();

        Boolean[] versionMatches = new Boolean[kbnInstances.size()];

        for (int i=0; i < kbnInstances.size(); i++) {
            versionMatches[i] = true;
            String kbnServiceVersion = kbnInstances.get(i).getServiceVersion();
            if (!kbnServiceVersion.equals(upgradeStackVersion)) {
                versionMatches[i] = false;
            }
        }

        if (Arrays.asList(versionMatches).contains(false)) {
            throw new Error("Expected Kibana version instances to match " +
                            upgradeStackVersion + " got " + Arrays.toString(versionMatches));
        }

        if (!isKbnHealthy) {
            throw new Error("Kibana is not healthy");
        }
    }

    private void checkApm(CloudApi cloudApi, DeploymentsApi deploymentsApi) {
        System.out.println("Checking APM has been upgraded and is healthy");
        ApmResourceInfo apmResourceInfo = deploymentsApi.getDeploymentApmResourceInfo(
                deploymentId,
                cloudApi.getApmRefId(),
                false,
                false,
                false,
                false,
                false,
                false);

        boolean isApmHealthy = apmResourceInfo.getInfo().isHealthy();

        List<ClusterInstanceInfo> apmInstances = apmResourceInfo.getInfo().getTopology().getInstances();

        Boolean[] versionMatches = new Boolean[apmInstances.size()];

        for (int i=0; i < apmInstances.size(); i++) {
            versionMatches[i] = true;
            String apmServiceVersion = apmInstances.get(i).getServiceVersion();
            if (!apmServiceVersion.equals(upgradeStackVersion)) {
                versionMatches[i] = false;
            }
        }

        if (Arrays.asList(versionMatches).contains(false)) {
            throw new Error("Expected Apm version instances to match " +
                            upgradeStackVersion + " got " + Arrays.toString(versionMatches));
        }

        if (!isApmHealthy) {
            throw new Error("APM is not healthy");
        }
    }

    private void checkEnterpriseSearch(CloudApi cloudApi, DeploymentsApi deploymentsApi) {
        System.out.println("Checking EnterpriseSearch has been upgraded and is healthy");
        EnterpriseSearchResourceInfo ensResourceInfo = deploymentsApi.getDeploymentEnterpriseSearchResourceInfo(
                deploymentId,
                cloudApi.getEnsRefId(),
                false,
                false,
                false,
                false,
                false,
                false);

        boolean isEnsHealthy = ensResourceInfo.getInfo().isHealthy();

        List<ClusterInstanceInfo> ensInstances = ensResourceInfo.getInfo().getTopology().getInstances();

        Boolean[] versionMatches = new Boolean[ensInstances.size()];

        for (int i=0; i < ensInstances.size(); i++) {
            versionMatches[i] = true;
            String ensServiceVersion = ensInstances.get(i).getServiceVersion();
            if (!ensServiceVersion.equals(upgradeStackVersion)) {
                versionMatches[i] = false;
            }
        }

        if (Arrays.asList(versionMatches).contains(false)) {
            throw new Error("Expected Enterprise Search version instances to match " +
                             upgradeStackVersion + " got " + Arrays.toString(versionMatches));
        }

        if (!isEnsHealthy) {
            throw new Error("Enterprise Search is not healthy");
        }
    }
}
