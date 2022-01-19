package org.estf.gradle;

import co.elastic.cloud.api.client.generated.DeploymentsApi;
import co.elastic.cloud.api.model.generated.*;
import co.elastic.cloud.api.util.Waiter;
import com.bettercloud.vault.VaultException;
import io.swagger.client.ApiClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

/**
 * Cloud API
 *
 * @author  Liza Dayoub
 *
 */
public class CloudApi {

    private String host = "public-api.staging.foundit.no";
    final private ApiClient apiClient;
    final private String esRefId = "main-elasticsearch";
    final private String kbRefId = "main-kibana";
    final private int MAX_RETRIES = 3;

    CloudApi() throws VaultException, IOException {
        VaultCredentials credentials = new VaultCredentials();

        String estf_host = System.getenv("ESTF_CLOUD_HOST");
        if (estf_host != null) {
            host = estf_host;
        }
        String url = getUrl();

        System.out.println("Debug: Setting up API client");
        apiClient = new ApiClient();
        apiClient.setApiKey(credentials.getApiKey());
        apiClient.setApiKeyPrefix("ApiKey");
        apiClient.setBasePath(url);
        apiClient.setDebugging(true);
        System.out.println("Debug: API URL: " + url);
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public String getEsRefId() {
        return esRefId;
    }

    public String getKbRefId() {
        return kbRefId;
    }

    public boolean isElasticsearchClusterRunning(ElasticsearchClusterInfo elasticsearchClusterInfo) {
        return ElasticsearchClusterInfo.StatusEnum.STARTED.equals(elasticsearchClusterInfo.getStatus());
    }

    public boolean isKibanaClusterRunning(KibanaClusterInfo kibanaClusterInfo) {
        return KibanaClusterInfo.StatusEnum.STARTED.equals(kibanaClusterInfo.getStatus());
    }

    public ElasticsearchClusterInfo getEsClusterInfo(DeploymentsApi deploymentsApi, String deploymentId) {
        for (int retries = 0;; retries++) {
            try {
                ElasticsearchResourceInfo esResourceInfo = deploymentsApi.getDeploymentEsResourceInfo(deploymentId,
                        this.esRefId,
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
                return esResourceInfo.getInfo();
            } catch (Exception ex) {
                if (retries < MAX_RETRIES) {
                    System.out.println("** Retrying get elasticsearch cluster info **");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    throw new Error(ex);
                }
            }
        }
    }

    public KibanaClusterInfo getKbnClusterInfo(DeploymentsApi deploymentsApi, String deploymentId) {
        for (int retries = 0;; retries++) {
            try {
                KibanaResourceInfo kbnResourceInfo =  deploymentsApi.getDeploymentKibResourceInfo(
                        deploymentId,
                        this.kbRefId,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false);
                return kbnResourceInfo.getInfo();
            } catch (Exception ex) {
                if (retries < MAX_RETRIES) {
                    System.out.println("** Retrying get kibana cluster info **");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    throw new Error(ex);
                }
            }
        }
    }

    public void waitForElasticsearch(DeploymentsApi deploymentsApi, String deploymentId) {
        Waiter.waitFor(() -> this.isElasticsearchClusterRunning(this.getEsClusterInfo(deploymentsApi, deploymentId)));
    }

    public void waitForKibana(DeploymentsApi deploymentsApi, String deploymentId) {
        Waiter.waitFor(() -> this.isKibanaClusterRunning(this.getKbnClusterInfo(deploymentsApi, deploymentId)));
    }

    public String getEnvRegion() {
        String default_region = "us-east-1";
        ArrayList<String> regions = new ArrayList<>();
        regions.add("us-east-1");
        regions.add("us-west-1");
        regions.add("eu-west-1");
        regions.add("ap-southeast-1");
        regions.add("ap-northeast-1");
        regions.add("sa-east-1");
        regions.add("ap-southeast-2");
        regions.add("aws-eu-central-1");
        regions.add("gcp-us-central1");
        regions.add("gcp-europe-west-1");
        regions.add("azure-eastus2");

        String data_region = System.getenv("ESTF_CLOUD_REGION");
        if (data_region == null) {
            return default_region;
        }

        if (regions.contains(data_region)) {
            return data_region;
        }
        return default_region;
    }

    private String getHost() {
        try {
            if (host.contains("http")) {
                URL url = new URL(host);
                return url.getHost();
            }
        } catch (MalformedURLException e) {
            throw new Error(e.toString());
        }
        return host;
    }

    private String getUrl() {
        return "https://" + getHost() + "/api/v1";
    }
}
