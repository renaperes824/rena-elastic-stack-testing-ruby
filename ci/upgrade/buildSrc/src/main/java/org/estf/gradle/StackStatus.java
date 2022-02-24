package org.estf.gradle;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.*;
import java.util.Properties;

/**
 * StackStatus
 *
 * @author  Liza Dayoub
 *
 */
public class StackStatus {

    private final RestApi api;
    private final String esBaseUrl;
    private final String kbnBaseUrl;

    public StackStatus(String deploymentId) throws IOException {
        InputStream input = new FileInputStream(DeploymentFile.getFilename(deploymentId));
        Properties prop = new Properties();
        prop.load(input);
        this.esBaseUrl = prop.getProperty("elasticsearch_url");
        this.kbnBaseUrl = prop.getProperty("kibana_url");
        this.api = new RestApi( prop.getProperty("es_username"), prop.getProperty("es_password"));
    }

    public void isKibanaHealthy() throws IOException, InterruptedException {
        String path = "/api/status";
        api.setMaxRetries(50);
        HttpResponse response = api.get(kbnBaseUrl + path);
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        System.out.println("*******SECTION: KIBANA INFO *******");
        System.out.println("\n" + json + "\n");
        JSONObject status_overall = json.getJSONObject("status").getJSONObject("overall");
        if (status_overall.has("state")) {
            String state = status_overall.getString("state");
            if (!state.equals("green") && ! state.equals("yellow")) {
                throw new Error("Kibana is not in green or yellow state!");
            }
        } else if (status_overall.has("level")) {
            String level = status_overall.getString("level");
            if (!level.equals("available")) {
                throw new Error("Kibana is not available!");
            }
        }
    }

    public void isElasticsearchHealthy() throws IOException, InterruptedException {
        String path = "/_cluster/health";
        api.setMaxRetries(10);
        HttpResponse response1 = api.get(esBaseUrl);
        HttpEntity entity1 = response1.getEntity();
        String content1 = EntityUtils.toString(entity1);
        JSONObject json1 = new JSONObject(content1);
        HttpResponse response2 = api.get(esBaseUrl + path);
        HttpEntity entity2 = response2.getEntity();
        String content2 = EntityUtils.toString(entity2);
        JSONObject json2 = new JSONObject(content2);
        System.out.println("******* SECTION: ELASTICSEARCH INFO *******");
        System.out.println("\n" + json1 + "\n");
        System.out.println("\n" + json2 + "\n");
        String status = json2.getString("status");
        if (!status.equals("green") && ! status.equals("yellow")) {
            throw new Error("Elasticsearch is not in green or yellow state!");
        }
    }
}