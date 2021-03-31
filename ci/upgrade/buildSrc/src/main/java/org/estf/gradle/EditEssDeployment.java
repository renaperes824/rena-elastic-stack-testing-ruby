package org.estf.gradle;

import co.elastic.cloud.api.client.generated.DeploymentsApi;
import co.elastic.cloud.api.model.generated.*;
import co.elastic.cloud.api.util.Waiter;
import com.bettercloud.vault.VaultException;
import io.swagger.client.ApiClient;
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
    public Boolean disableSamlLogin = false;

    @TaskAction
    public void run() throws IOException, VaultException {

        if (deploymentId == null || deploymentId.trim().isEmpty()) {
            throw new Error(this.getClass().getSimpleName() + ": deploymentId is required input");
        }

        if ( (esUserSettings == null || esUserSettings.trim().isEmpty()) &&
                (kibanaUserSettings == null || kibanaUserSettings.trim().isEmpty()) &&
                ! disableSamlLogin) {
            throw new Error("Must specify settings");
        }

        CloudApi cloudApi = new CloudApi();
        ApiClient apiClient = cloudApi.getApiClient();
        DeploymentsApi deploymentsApi = new DeploymentsApi(apiClient);

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

        DeploymentUpdateResources deploymentUpdateResources = new DeploymentUpdateResources();

        if (disableSamlLogin) {
            String kbnId =  kbnResourceInfo.getId();
            esUserSettings = "xpack.security.authc.realms.saml.cloud-saml-kibana-" + kbnId + ".enabled: false";
        }

        if (esUserSettings != null) {
            esClusterPlan.getElasticsearch().setUserSettingsYaml(esUserSettings);
        }

        if (kibanaUserSettings != null) {
            kbnClusterPlan.getKibana().setUserSettingsYaml(kibanaUserSettings);
        }

        deploymentUpdateResources
                .addElasticsearchItem(new ElasticsearchPayload()
                    .region(esResourceInfo.getRegion())
                    .refId(esResourceInfo.getRefId())
                    .plan(esClusterPlan))
                .addKibanaItem(new KibanaPayload()
                    .elasticsearchClusterRefId(kbnResourceInfo.getElasticsearchClusterRefId())
                    .region(kbnResourceInfo.getRegion())
                    .refId(kbnResourceInfo.getRefId())
                    .plan(kbnClusterPlan));

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
    }

}
