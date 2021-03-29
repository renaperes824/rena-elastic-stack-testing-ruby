package org.estf.gradle;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * UploadData
 *
 * @author Liza Dayoub
 *
 */
public class UploadData extends DefaultTask {

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
        uploadBankAccountData();
        createBankIndexPatternAsDefault();
        if (majorVersion > 5) {
            loadSampleData();
        }
    }

    public void uploadBankAccountData() throws IOException, InterruptedException {
        String link = "https://download.elastic.co/demos/kibana/gettingstarted/accounts.zip";
        downloadFile(link, true);
        String credentials = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        for (int retries = 0;; retries++) {
            HttpPost postRequest = new HttpPost(esBaseUrl + "/bank/account/_bulk?pretty");
            postRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
            postRequest.setEntity(new FileEntity(new File("tmp/accounts.json"),
                                                ContentType.create("application/x-ndjson")));
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println(statusCode);
            if (statusCode == 200) {
                break;
            }
            if (retries < MAX_RETRIES) {
                System.out.println("** Retrying upload bank account data **");
                Thread.sleep(5000);
            } else {
                throw new IOException("Failed to upload bank account data!");
            }
        }
    }

    public void downloadFile(String link, boolean zipFile) throws IOException {
        try {
            String projectPath = System.getProperty("user.dir");
            URL url = new URL(link);
            String filename = Paths.get(new URI(link).getPath()).getFileName().toString();
            String fileDir = projectPath + "/tmp";
            String filepath = fileDir + "/" + filename;
            File dir = new File(fileDir);
            if (! dir.exists()) {
                if (! dir.mkdir() ) {
                    throw new SecurityException("Unable to mkdir: " + fileDir);
                }
            }
            ReadableByteChannel rbc = Channels.newChannel(url.openStream());
            FileOutputStream fOutStream = new FileOutputStream(filepath);
            fOutStream.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fOutStream.close();
            rbc.close();
            if (zipFile) {
                unzip(filepath, fileDir);
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI", e);
        }
    }

    public void unzip(String zipFilePath, String destinationDir) throws IOException {
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        while (entry != null) {
            String filePath = destinationDir + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                if (! dir.exists()) {
                    if (!dir.mkdir()) {
                        throw new SecurityException("Unable to mkdir: " + filePath);
                    }
                }
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }

    public void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[4096];
        int read;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

    public void createBankIndexPatternAsDefault() throws IOException, InterruptedException {
        String credentials = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        for (int retries = 0;; retries++) {
            HttpPost postRequest = new HttpPost(kbnBaseUrl + "/api/saved_objects/index-pattern");
            postRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
            postRequest.setHeader("kbn-xsrf", "automation");
            postRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            String jsonStr = "{\"attributes\": {\"title\": \"bank*\"}}";
            StringEntity entity = new StringEntity(jsonStr);
            entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            postRequest.setEntity(entity);
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println(statusCode);
            if (statusCode != 200) {
                if (retries < MAX_RETRIES) {
                    System.out.println("** Retrying create bank index pattern  **");
                    Thread.sleep(5000);
                    continue;
                } else {
                    throw new IOException("Failed to create bank index pattern!");
                }
            }
            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
            JSONObject json = new JSONObject(responseString);
            String id = json.getString("id");
            postRequest = new HttpPost(kbnBaseUrl + "/api/kibana/settings");
            postRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
            postRequest.setHeader("kbn-xsrf", "automation");
            postRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            jsonStr = "{\"changes\": {\"defaultIndex\": \"" + id +  "\"}}";
            entity = new StringEntity(jsonStr);
            entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            postRequest.setEntity(entity);
            client = HttpClientBuilder.create().build();
            response = client.execute(postRequest);
            statusCode = response.getStatusLine().getStatusCode();
            System.out.println(statusCode);
            if (statusCode == 200) {
                break;
            }
            if (retries < MAX_RETRIES) {
                System.out.println("** Retrying create bank index pattern as default **");
                Thread.sleep(5000);
            } else {
                throw new IOException("Failed to create bank index pattern as default!");
            }
        }
    }

    public void createNonDefaultSpace(String name, String id) throws IOException, InterruptedException {
        String credentials = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        for (int retries = 0;; retries++) {
            HttpPost postRequest = new HttpPost(kbnBaseUrl + "/api/spaces/space");
            postRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
            postRequest.setHeader("kbn-xsrf", "automation");
            postRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            String jsonStr = "{\"name\": \"" + name + "\", \"id\": \"" + id + "\"}";
            StringEntity entity = new StringEntity(jsonStr);
            entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            postRequest.setEntity(entity);
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println(statusCode);
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

    public void loadSampleData() throws IOException, InterruptedException {
        createNonDefaultSpace("Automation", "automation");
        List<String> dataList = new ArrayList<>(6);
        dataList.add("api/sample_data/ecommerce");
        dataList.add("api/sample_data/logs");
        dataList.add("api/sample_data/flights");
        dataList.add("s/automation/api/sample_data/ecommerce");
        dataList.add("s/automation/api/sample_data/logs");
        dataList.add("s/automation/api/sample_data/flights");
        String credentials = username + ":" + password;
        String basicAuthPayload = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        for (String s : dataList) {
            for (int retries = 0;; retries++) {
                HttpPost postRequest = new HttpPost(kbnBaseUrl + "/" + s);
                postRequest.setHeader(HttpHeaders.AUTHORIZATION, basicAuthPayload);
                postRequest.setHeader("kbn-xsrf", "automation");
                postRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                HttpClient client = HttpClientBuilder.create().build();
                HttpResponse response = client.execute(postRequest);
                int statusCode = response.getStatusLine().getStatusCode();
                System.out.println(statusCode);
                if (statusCode == 200) {
                    break;
                }
                if (retries < MAX_RETRIES) {
                    System.out.println("** Retrying load sample data **");
                    Thread.sleep(5000);
                } else {
                    throw new Error("Failed to load sample data: " + s);
                }
            }
        }
    }
}
