package org.estf.gradle;

import co.elastic.cloud.api.client.generated.DeploymentsApi;
import co.elastic.cloud.api.model.generated.*;
import co.elastic.cloud.api.util.Waiter;
import com.bettercloud.vault.VaultException;
import io.swagger.client.ApiClient;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.time.Duration;

/**
 * EditEssDeployment
 *
 * @author  Liza Dayoub
 *
 */
public class EditEssDeployment extends DefaultTask {

    @Input
    public  String deploymentId = null;

    @Input
    public String esUserSettings = null;

    @Input
    public String kibanaUserSettings = null;

    @Input
    public boolean disableSamlLogin = false;

    @Input
    public boolean disableCspStrict = false;

    @TaskAction
    public void run() throws IOException, VaultException {

        if (deploymentId == null || deploymentId.trim().isEmpty()) {
            throw new Error(this.getClass().getSimpleName() + ": deploymentId is required input");
        }

        if ((esUserSettings == null || esUserSettings.trim().isEmpty()) &&
                (kibanaUserSettings == null || kibanaUserSettings.trim().isEmpty()) &&
                !disableSamlLogin) {
            throw new Error("Must specify settings");
        }

        CloudApi cloudApi = new CloudApi();
        ApiClient apiClient = cloudApi.getApiClient();
        DeploymentsApi deploymentsApi = new DeploymentsApi(apiClient);
        editDeployment(deploymentsApi, cloudApi);
        System.out.println("Edit deployment " + deploymentId + " completed successfully");
    }

    public void editDeployment(DeploymentsApi deploymentsApi, CloudApi cloudApi) {
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
        ElasticsearchClusterPlan esClusterPlan =  esResourceInfo.getInfo().getPlanInfo().getCurrent().getPlan();

        String esVersion = esClusterPlan.getElasticsearch().getVersion();
        ComparableVersion currentVersion = new ComparableVersion(esVersion);
        ComparableVersion version7_11 = new ComparableVersion("7.11.0");
        ComparableVersion version8_0 = new ComparableVersion("8.0.0");
        boolean isPre7_10 = false;
        boolean is8_x = false;
        if (currentVersion.compareTo(version7_11) < 0) {
            isPre7_10 = true;
        }
        if (currentVersion.compareTo(version8_0) >= 0) {
            is8_x = true;
        }

        ElasticsearchClusterPlan esPlan;
        if (isPre7_10) {
            esPlan = esResourceInfo.getInfo().getPlanInfo().getCurrent().getPlan();
        } else {
            esPlan = deploymentGetResponse
                    .getResources()
                    .getElasticsearch()
                    .get(0)
                    .getInfo()
                    .getPlanInfo()
                    .getCurrent()
                    .getPlan();
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
        KibanaClusterPlan kbnClusterPlan =  kbnResourceInfo.getInfo().getPlanInfo().getCurrent().getPlan();

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

        DeploymentUpdateResources deploymentUpdateResources = new DeploymentUpdateResources();


        if (disableSamlLogin) {
            esUserSettings = "xpack.security.authc.realms.saml.cloud-saml-kibana.enabled: false";
            if (! is8_x && ensResourceInfo != null) {
                esUserSettings = esUserSettings +
                        "\nxpack.security.authc.realms.saml.cloud-saml-enterprise_search.enabled: false";
            }
            if (is8_x) {
                esUserSettings = esUserSettings + "\nxpack.security.authc.realms.saml.cloud-saml-kibana.order: 100";
            }
        }

        if (disableCspStrict) {
            if (is8_x) {
                kibanaUserSettings = "csp.strict: false";
            }
        }

        if (esUserSettings != null) {
            esPlan.getElasticsearch().setUserSettingsYaml(esUserSettings);
        }

        if (kibanaUserSettings != null) {
            kbnClusterPlan.getKibana().setUserSettingsYaml(kibanaUserSettings);
        }

        deploymentUpdateResources
                .addElasticsearchItem(new ElasticsearchPayload()
                    .region(esResourceInfo.getRegion())
                    .refId(esResourceInfo.getRefId())
                    .plan(esPlan))
                .addKibanaItem(new KibanaPayload()
                    .elasticsearchClusterRefId(kbnResourceInfo.getElasticsearchClusterRefId())
                    .region(kbnResourceInfo.getRegion())
                    .refId(kbnResourceInfo.getRefId())
                    .plan(kbnClusterPlan));

        if (ensPlan != null) {
            deploymentUpdateResources.addEnterpriseSearchItem(new EnterpriseSearchPayload()
                    .elasticsearchClusterRefId(ensResourceInfo.getElasticsearchClusterRefId())
                    .region(ensResourceInfo.getRegion())
                    .refId(ensResourceInfo.getRefId())
                    .plan(ensPlan));
        }

        DeploymentUpdateRequest deploymentUpdateRequest = new DeploymentUpdateRequest()
                .name(deploymentGetResponse.getName())
                .pruneOrphans(true)
                .resources(deploymentUpdateResources);

        deploymentsApi.updateDeployment(deploymentId,
                deploymentUpdateRequest,
                false,
                false,
                false,
                null);

        Waiter.setWait(Duration.ofMinutes(20));
        cloudApi.waitForElasticsearch(deploymentsApi, deploymentId);
        cloudApi.waitForKibana(deploymentsApi, deploymentId);
        if (ensPlan != null) {
            cloudApi.waitForEnterpriseSearch(deploymentsApi, deploymentId);
        }
    }
}
