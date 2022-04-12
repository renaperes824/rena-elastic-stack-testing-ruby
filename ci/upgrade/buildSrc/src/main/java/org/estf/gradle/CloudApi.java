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
 * CloudApi
 *
 * @author  Liza Dayoub
 *
 */
public class CloudApi {

    private String host = "cloud.elastic.co";
    final private ApiClient apiClient;
    final private String esRefId = "main-elasticsearch";
    final private String kbRefId = "main-kibana";
    final private String apmRefId = "main-apm";
    final private String ensRefId = "main-enterprise_search";
    final private int MAX_RETRIES = 3;

    CloudApi() throws VaultException, IOException {
        VaultCredentials credentials = new VaultCredentials();

        //TODO: Comment out until job updates are merged; infra#35595
        //String estf_host = System.getenv("ESTF_CLOUD_HOST");
        //if (estf_host != null) {
            host = estf_host;
        //}

        boolean cloudApiDebug = false;
        String getEnvCloudApiDebug = System.getenv("ESTF_CLOUD_API_DEBUG");
        if (getEnvCloudApiDebug != null) {
            cloudApiDebug = Boolean.parseBoolean(getEnvCloudApiDebug);
        }

        String url = getUrl();
        apiClient = new ApiClient();
        apiClient.setApiKey(credentials.getApiKey());
        apiClient.setApiKeyPrefix("ApiKey");
        apiClient.setBasePath(url);
        apiClient.setDebugging(cloudApiDebug);
        System.out.println("Setup API client completed successfully");
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

    public String getEnsRefId() {
        return ensRefId;
    }

    public String getApmRefId() {
        return apmRefId;
    }

    public boolean isElasticsearchClusterRunning(ElasticsearchClusterInfo elasticsearchClusterInfo) {
        return ElasticsearchClusterInfo.StatusEnum.STARTED.equals(elasticsearchClusterInfo.getStatus());
    }

    public boolean isKibanaClusterRunning(KibanaClusterInfo kibanaClusterInfo) {
        return KibanaClusterInfo.StatusEnum.STARTED.equals(kibanaClusterInfo.getStatus());
    }

    public boolean isApmRunning(ApmInfo apmInfo) {
        return ApmInfo.StatusEnum.STARTED.equals(apmInfo.getStatus());
    }

    public boolean isEnterpriseSearchRunning(EnterpriseSearchInfo enterpriseSearchInfo) {
        return EnterpriseSearchInfo.StatusEnum.STARTED.equals(enterpriseSearchInfo.getStatus());
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

    public ApmInfo getApmInfo(DeploymentsApi deploymentsApi, String deploymentId) {
        for (int retries = 0;; retries++) {
            try {
                ApmResourceInfo apmResourceInfo =  deploymentsApi.getDeploymentApmResourceInfo(
                        deploymentId,
                        this.apmRefId,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false);
                return apmResourceInfo.getInfo();
            } catch (Exception ex) {
                if (retries < MAX_RETRIES) {
                    System.out.println("** Retrying get apm info **");
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

    public EnterpriseSearchInfo getEnsInfo(DeploymentsApi deploymentsApi, String deploymentId) {
        for (int retries = 0;; retries++) {
            try {
                EnterpriseSearchResourceInfo ensResourceInfo =  deploymentsApi.getDeploymentEnterpriseSearchResourceInfo(
                                deploymentId,
                                this.ensRefId,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false);
                return ensResourceInfo.getInfo();
            } catch (Exception ex) {
                if (retries < MAX_RETRIES) {
                    System.out.println("** Retrying get enterprise search info **");
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

    public void waitForApm(DeploymentsApi deploymentsApi, String deploymentId) {
        Waiter.waitFor(() -> this.isApmRunning(this.getApmInfo(deploymentsApi, deploymentId)));
    }

    public void waitForEnterpriseSearch(DeploymentsApi deploymentsApi, String deploymentId) {
        Waiter.waitFor(() -> this.isEnterpriseSearchRunning(this.getEnsInfo(deploymentsApi, deploymentId)));
    }

    public String getEnvRegion() {
        String default_region = "gcp-us-west2";
        ArrayList<String> regions = new ArrayList<>();
        regions.add("us-east-1");
        regions.add("us-west-1");
        regions.add("eu-west-1");
        regions.add("ap-southeast-1");
        regions.add("ap-northeast-1");
        regions.add("sa-east-1");
        regions.add("ap-southeast-2");
        regions.add("aws-eu-central-1");
        regions.add("gcp-us-west2");
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
