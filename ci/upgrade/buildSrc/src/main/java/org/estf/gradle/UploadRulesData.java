package org.estf.gradle;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * UploadData
 *
 * @author Liza Dayoub
 *
 */
public class UploadRulesData extends DefaultTask {

    @Input
    public String esBaseUrl;

    @Input
    public String kbnBaseUrl;

    @Input
    public String username;

    @Input
    public String password;

    @Input
    public String version;

    @Input
    public String upgradeVersion;

    private boolean workaround = false;

    final private int MAX_RETRIES = 5;

    @TaskAction
    public void run() throws IOException, InterruptedException {
        ComparableVersion currentVersion = new ComparableVersion(version);
        ComparableVersion version7_13 = new ComparableVersion("7.13.0");
        if (currentVersion.compareTo(version7_13) >= 0) {
            createRule();
            System.out.println("Upload rules data completed successfully");
        }
        ComparableVersion version8_2 = new ComparableVersion("8.2.0");
        ComparableVersion version8_3 = new ComparableVersion("8.3.0");
        if (currentVersion.compareTo(version8_2) >= 0 &&
                currentVersion.compareTo(version8_3) < 0) {
            workaround = true;
            System.out.println("Apply workaround");
        }
    }

    public void createNonDefaultSpace(String name, String id) throws IOException, InterruptedException {
        String credentials = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        HttpResponse response;
        HttpGet getRequest = new HttpGet(kbnBaseUrl + "/api/spaces/space/" + id);
        getRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
        getRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        HttpClient client = HttpClientBuilder.create().build();
        response = client.execute(getRequest);
        StatusLine statusLine = response.getStatusLine();
        System.out.println(statusLine);
        if (statusLine.getStatusCode() == 200) {
            return;
        }
        for (int retries = 0;; retries++) {
            HttpPost postRequest = new HttpPost(kbnBaseUrl + "/api/spaces/space");
            postRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
            postRequest.setHeader("kbn-xsrf", "automation");
            postRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            String jsonStr = "{\"name\": \"" + name + "\", \"id\": \"" + id + "\"}";
            StringEntity entity = new StringEntity(jsonStr);
            entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            postRequest.setEntity(entity);
            client = HttpClientBuilder.create().build();
            response = client.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                break;
            }
            if (retries < MAX_RETRIES) {
                System.out.println("** Retrying create non-default space **");
                Thread.sleep(5000);
            } else {
                throw new IOException("Failed to create non-default space: " + id);
            }
        }
    }

    public void checkRuleStatus(String id, String path) throws IOException, InterruptedException {
        String credentials = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        for (int retries = 0;; retries++) {
            HttpGet getRequest = new HttpGet(kbnBaseUrl+ "/" + path + '/' + id);
            getRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
            getRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            System.out.println("Path: " + path + '/' + id);
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(getRequest);
            StatusLine statusLine = response.getStatusLine();
            System.out.println(statusLine);
            if (statusLine.getStatusCode() == 200) {
                HttpEntity responseEntity = response.getEntity();
                String content = EntityUtils.toString(responseEntity);
                JSONObject json = new JSONObject(content);
                String status = json.getJSONObject("execution_status").getString("status");
                System.out.println("Status is: " + status);
                if (Objects.equals(status, "ok")) {
                    break;
                }
            }
            if (retries < MAX_RETRIES) {
                System.out.println("** Retrying verify rule **");
                Thread.sleep(5000);
            } else {
                throw new IOException("Failed to verify rule: " + path + "/" + id);
            }
        }
    }

    public void createRule() throws IOException, InterruptedException {
        createNonDefaultSpace("Automation", "automation");
        String credentials = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        String payload1 = "{\"params\":{\"nodeType\":\"host\",\"criteria\":[{\"metric\":\"cpu\",\"comparator\":\">\",\"threshold\":[75],\"timeSize\":1,\"timeUnit\":\"m\",\"customMetric\":{\"type\":\"custom\",\"id\":\"alert-custom-metric\",\"field\":\"\",\"aggregation\":\"avg\"}}],\"sourceId\":\"default\"},\"consumer\":\"alerts\",\"schedule\":{\"interval\":\"1m\"},\"tags\":[],\"name\":\"UpgradeRule\",\"rule_type_id\":\"metrics.alert.inventory.threshold\",\"notify_when\":\"onActionGroupChange\",\"actions\":[]}";
        // TODO: Remove when corresponding test update is merged
        String payload2 = "{\"params\":{\"nodeType\":\"host\",\"criteria\":[{\"metric\":\"cpu\",\"comparator\":\">\",\"threshold\":[75],\"timeSize\":1,\"timeUnit\":\"m\",\"customMetric\":{\"type\":\"custom\",\"id\":\"alert-custom-metric\",\"field\":\"\",\"aggregation\":\"avg\"}}],\"sourceId\":\"default\"},\"consumer\":\"alerts\",\"schedule\":{\"interval\":\"1m\"},\"tags\":[],\"name\":\"Upgrade Rule\",\"rule_type_id\":\"metrics.alert.inventory.threshold\",\"notify_when\":\"onActionGroupChange\",\"actions\":[]}";
        List<String> payloads = new ArrayList<>(2);
        payloads.add(payload1);
        payloads.add(payload2);

        List<String> dataList = new ArrayList<>(2);
        dataList.add("api/alerting/rule");
        dataList.add("s/automation/api/alerting/rule");
        for (String s : dataList) {
            for (String jsonStr : payloads) {
                for (int retries = 0; ; retries++) {
                    HttpPost postRequest = new HttpPost(kbnBaseUrl + "/" + s);
                    postRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
                    postRequest.setHeader("kbn-xsrf", "automation");
                    postRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                    StringEntity entity = new StringEntity(jsonStr);
                    entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
                    postRequest.setEntity(entity);
                    HttpClient client = HttpClientBuilder.create().build();
                    HttpResponse response = client.execute(postRequest);
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (statusCode == 200) {
                        HttpEntity responseEntity = response.getEntity();
                        String content = EntityUtils.toString(responseEntity);
                        JSONObject json = new JSONObject(content);
                        String id = json.getString("id");
                        checkRuleStatus(id, s);
                        break;
                    }
                    if (retries < MAX_RETRIES) {
                        System.out.println("** Retrying create rule **");
                        Thread.sleep(5000);
                    } else {
                        throw new Error("Failed to create rule data: " + s);
                    }
                }
            }
        }
    }

    public boolean applyWorkaround() {
        return workaround;
    }

}
