package org.estf.gradle;

import co.elastic.cloud.api.client.generated.DeploymentsApi;
import com.bettercloud.vault.VaultException;
import io.swagger.client.ApiClient;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Input;

import java.io.*;

/**
 * Shutdown cloud deployment
 *
 * @author: Liza Dayoub
 *
 */
public class ShutdownCloudDeployment extends DefaultTask {

    @Input
    public String deploymentId = "";

    @TaskAction
    public void run() throws IOException, VaultException {
        if (deploymentId == null || deploymentId.trim().isEmpty()) {
            throw new Error(this.getClass().getSimpleName() + ": deploymentId is required input");
        }

        CloudApi cloudApi = new CloudApi();
        ApiClient apiClient = cloudApi.getApiClient();
        DeploymentsApi deploymentsApi = new DeploymentsApi(apiClient);
        deploymentsApi.shutdownDeployment(deploymentId,true,true);

        String filename = PropFile.getFilename(deploymentId);
        File f = new File(filename);
        if (f.exists()) {
            if (!f.delete()) {
                System.err.println("Warning unable to delete file: " + filename);
            }
        }
    }

    public String getDeploymentId() { return deploymentId; }
}
