/**
 * Creates an alert for Security Solution
 *
 *
 * @author  Gloria Hornero
 *
 */

package org.estf.gradle;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UploadSecuritySolutionData extends DefaultTask {

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

    final private int MAX_RETRIES = 5;

    @TaskAction
    public void run() throws IOException, InterruptedException {
        RestApi api = new RestApi(username, password, version, upgradeVersion);
        int majorVersion = api.setMajorVersion();
        if (majorVersion > 6) {
            createsSiemSignalsIndex();
            createsDetectionRule();
            createsDocumentIndex();
            createsDocumentToGenerateAlert();
        }
    }

    public void createsSiemSignalsIndex() throws IOException, InterruptedException {
        String creds = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(creds.getBytes());

        for (int retries = 0; ; retries++) {
            String jsonstr = "";
            StringEntity entity = new StringEntity(jsonstr);
            entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            HttpPost postRequest = new HttpPost(kbnBaseUrl + "/api/detection_engine/index");
            postRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
            postRequest.setHeader("kbn-xsrf", "automation");
            postRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            postRequest.setEntity(entity);
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println(statusCode);
            if (statusCode == 200) {
                break;
            }
            if (retries < MAX_RETRIES) {
                System.out.println("** Retrying to create siem signals index **");
                Thread.sleep(5000);
            } else {
                throw new IOException("Failed to create siem signals index");
            }
        }

    }

    public void createsDetectionRule() throws IOException, InterruptedException {
        String creds = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(creds.getBytes());

        for (int retries = 0; ; retries++) {
            String file = "buildSrc/src/main/resources/detectionRule.json";
            String jsonStr = new String(Files.readAllBytes(Paths.get(file)));
            StringEntity entity = new StringEntity(jsonStr);
            entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            HttpPost postRequest = new HttpPost(kbnBaseUrl + "/api/detection_engine/rules");
            postRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
            postRequest.setHeader("kbn-xsrf", "automation");
            postRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            postRequest.setEntity(entity);
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println(statusCode);
            if (statusCode == 200) {
                break;
            }
            if (retries < MAX_RETRIES) {
                System.out.println("** Retrying to create a detection rule **");
                Thread.sleep(5000);
            } else {
                throw new IOException("Failed to create a detection rule");
            }
        }
    }

    public void createsDocumentIndex() throws IOException, InterruptedException {
        String creds = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(creds.getBytes());
        for (int retries = 0; ; retries++) {
            HttpPut putRequest = new HttpPut(esBaseUrl + "/auditbeat-upgrade-1");
            putRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(putRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println(statusCode);
            if (statusCode == 200) {
                break;
            }
            if (retries < MAX_RETRIES) {
                System.out.println("** Retrying to create document index **");
                Thread.sleep(5000);
            } else {
                throw new IOException("Failed to create document index");
            }
        }
    }

    public void createsDocumentToGenerateAlert() throws IOException, InterruptedException {
        String creds = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(creds.getBytes());
        for (int retries = 0;; retries++) {
            String jsonstr = "{\"@timestamp\":\"2021-07-01T13:12:00\", \"host\":{\"name\":\"test\"}}";
            StringEntity entity = new StringEntity(jsonstr);
            entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            HttpPost postRequest = new HttpPost(esBaseUrl + "/auditbeat-upgrade-1/_doc");
            postRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
            postRequest.setHeader("kbn-xsrf", "automation");
            postRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            postRequest.setEntity(entity);
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println(statusCode);
            if (statusCode == 201) {
                break;
            }
            if (retries < MAX_RETRIES) {
                System.out.println("** Retrying to create document **");
                Thread.sleep(5000);
            } else {
                throw new IOException("Failed to create document");
            }
        }
    }

}
