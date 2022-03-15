package org.estf.gradle;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.IOException;
import java.util.Iterator;

/**
 *
 * UpgradeAssistantApi
 *
 * @author  Liza Mae Dayoub
 *
 */
public class UpgradeAssistantApi extends DefaultTask {

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

    private int majorVersion;

    final int REINDEX_TIMEOUT = 60000;
    final int CHECK_INTERVAL = 5000;

    @TaskAction
    public void run() throws IOException, InterruptedException {

        System.out.println("----------------------------");
        System.out.println("Elasticsearch URL: " + esBaseUrl);
        System.out.println("Kibana URL: " + kbnBaseUrl);
        System.out.println("Version: " + version);
        System.out.println("Upgrade Version: " + upgradeVersion);
        System.out.println("----------------------------");

        RestApi api = new RestApi(username, password, version, upgradeVersion);
        majorVersion = api.setMajorVersion();
        int majorUpgradeVersion = api.setMajorUpgradeVersion();
        if (majorVersion != majorUpgradeVersion) {
            System.out.println("Performing major upgrade...");
            if (majorVersion == 5) {
                setKibanaIndexReadOnly(api);
                createKibana6Index(api);
                reindexKibana6Index(api);
                aliasKibana6Index(api);
                runMigrationAssistant5(api);
            } else if (majorVersion == 6) {
                runMigrationAssistant6(api);
            } else if (majorVersion == 7) {
                if (!readyForUpgrade(api)) {
                    runMigrationAssistant7(api);
                    runUpgradeAssistantSystemIndicesMigration(api);
                    runEsDeprecations(api);
                    runKbnDeprecations(api);
                    if (!readyForUpgrade(api)) {
                        throw new IOException("System is not ready for upgrade!");
                    }
                }
            } else {
                throw new IOException("Major upgrade from 8.x not yet supported");
            }
        } else {
            System.out.println("Performing minor upgrade...");
        }
        System.out.println("Upgrade assistant completed successfully");
    }

    public void runMigrationAssistant5(RestApi api) throws IOException, InterruptedException {
        HttpResponse response;
        HttpEntity entity;
        String content;
        String path = "/_migration/assistance";
        String post_path = "/migration/upgrade/<index>";
        if (majorVersion < 7) {
            path = "/_xpack/migration/assistance";
            post_path = "/_xpack/migration/upgrade/<index>";
        }
        response = api.get(esBaseUrl + path);
        entity = response.getEntity();
        content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        System.out.println(json);
        JSONObject indices = json.getJSONObject("indices");
        Iterator<String> keys = indices.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject actionRequest = indices.getJSONObject(key);
            String action = actionRequest.get("action_required").toString();
            if (action.equals("upgrade")) {
                System.out.println(key);
                response = api.post(esBaseUrl + post_path.replace("<index>", key),
                                    "{\"wait_for_completion\": true}", false);
                entity = response.getEntity();
                content = EntityUtils.toString(entity);
                JSONObject json1 = new JSONObject(content);
                JSONArray failures = json1.getJSONArray("failures");
                if (failures.length() > 0) {
                    throw new IOException("Reindex Kibana index failed!");
                }
            }
        }
    }

    public void runMigrationAssistant6(RestApi api) throws IOException, InterruptedException {
        HttpResponse response;
        HttpEntity entity;
        String content;
        String path = "/_xpack/migration/assistance";
        response = api.get(esBaseUrl + path);
        entity = response.getEntity();
        content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        System.out.println(json);
        JSONObject indices = json.getJSONObject("indices");
        Iterator<String> keys = indices.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject actionRequest = indices.getJSONObject(key);
            String action = actionRequest.get("action_required").toString();
            if (action.equals("reindex")) {
                runUpgradeAssistantReindex(api, key);
            }
        }
    }

    public void runMigrationAssistant7(RestApi api) throws IOException, InterruptedException {
        HttpResponse response;
        String path = "/_migration/system_features";
        response = api.post(esBaseUrl + path, "", false);
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        boolean isAccepted = json.getBoolean("accepted");
        if (!isAccepted) {
            throw new IOException("Migration failed!");
        }
    }

    public void runEsDeprecations(RestApi api) throws IOException, InterruptedException {
        HttpResponse response;
        HttpEntity entity;
        String content;
        String path = "/_migration/deprecations";
        response = api.get(esBaseUrl + path);
        entity = response.getEntity();
        content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        System.out.println(json);
        JSONArray cluster_settings = json.getJSONArray("cluster_settings");
        JSONArray node_settings = json.getJSONArray("node_settings");
        JSONObject index_settings = json.getJSONObject("index_settings");
        if (! (cluster_settings.isEmpty() && node_settings.isEmpty()) ) {
            System.out.println("Cluster settings: " + cluster_settings);
            System.out.println("Node settings: " + node_settings.toString());
            throw new IOException("There are deprecations in cluster and/or node settings");
        }
        Iterator<String> keys = index_settings.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONArray index = index_settings.getJSONArray(key);
            System.out.println(index);
            for (int i = 0 ; i < index.length(); i++) {
                JSONObject index_elem = index.getJSONObject(i);
                String level = index_elem.getString("level");
                String details = index_elem.getString("details");
                if (level.equals("critical") && details.contains("Reindex")) {
                    System.out.println("Reindex: " + key);
                    runUpgradeAssistantReindex(api, key);
                    break;
                }
            }
        }
    }

    public void runKbnDeprecations(RestApi api) throws IOException, InterruptedException {
        boolean deprecationsExist = false;
        String path = "/api/deprecations/";
        HttpResponse response = api.get(kbnBaseUrl + path);
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        System.out.println("\n" + json + "\n");
        JSONArray deprecations = json.getJSONArray("deprecations");
        System.out.println(deprecations);
        for (int i = 0; i < deprecations.length(); i++) {
            JSONObject deprecations_elem = deprecations.getJSONObject(i);
            String level = deprecations_elem.getString("level");
            if (level.equals("critical")) {
                deprecationsExist = true;
                break;
            }
        }
        if (deprecationsExist) {
            throw new IOException("Kibana deprecations exist!");
        }
    }

    public Boolean readyForUpgrade(RestApi api) throws IOException, InterruptedException {
        String path = "/api/upgrade_assistant/status";
        HttpResponse response = api.get(kbnBaseUrl + path);
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        System.out.println("\n" + json + "\n");
        return json.getBoolean("readyForUpgrade");
    }

    public void runUpgradeAssistantReindex(RestApi api, String key) throws IOException, InterruptedException {
        String path = "/api/upgrade_assistant/reindex/<index>";
        HttpResponse response = api.post(kbnBaseUrl + path.replace("<index>", key),
                                         "", true);
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        System.out.println("\n" + json + "\n");
        checkUpgradeAssistantReindex(api, key);
    }

    public void runUpgradeAssistantSystemIndicesMigration(RestApi api) throws IOException, InterruptedException {
        String path = "/api/upgrade_assistant/system_indices_migration";
        api.post(kbnBaseUrl + path, "", true);
        checkUpgradeAssistantSystemIndicesMigration(api);
    }

    public void checkUpgradeAssistantSystemIndicesMigration(RestApi api) throws IOException, InterruptedException {
        String path = "/api/upgrade_assistant/system_indices_migration";
        long finish = System.currentTimeMillis() + 30000;
        HttpResponse response = api.get(kbnBaseUrl + path);
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        System.out.println("\n" + json + "\n");
        String migrationNeeded = json.getString("migration_status");
        while (! migrationNeeded.contains("NO_MIGRATION_NEEDED") && System.currentTimeMillis() < finish) {
            response = api.get(kbnBaseUrl + path);
            entity = response.getEntity();
            content = EntityUtils.toString(entity);
            json = new JSONObject(content);
            System.out.println("\n" + json + "\n");
            migrationNeeded = json.getString("migration_status");
            Thread.sleep(CHECK_INTERVAL);
        }
        if (!migrationNeeded.contains("NO_MIGRATION_NEEDED")) {
            throw new IOException("Migrating system indices failed!");
        }
    }

    public void checkUpgradeAssistantReindex(RestApi api, String key) throws IOException, InterruptedException {
        String path = "/api/upgrade_assistant/reindex/<index>";
        long finish = System.currentTimeMillis() + REINDEX_TIMEOUT;
        HttpResponse response = api.get(kbnBaseUrl + path.replace("<index>", key));
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        int status = json.getJSONObject("reindexOp").getInt("status");
        while (status == 0 && System.currentTimeMillis() < finish) {
            response = api.get(kbnBaseUrl + path.replace("<index>", key));
            entity = response.getEntity();
            content = EntityUtils.toString(entity);
            json = new JSONObject(content);
            status = json.getJSONObject("reindexOp").getInt("status");
            Thread.sleep(CHECK_INTERVAL);
        }
        if (status != 1) {
            throw new IOException("Reindex failed! Status is " + status);
        }
    }

    public void setKibanaIndexReadOnly(RestApi api) throws IOException, InterruptedException {
        String path = "/.kibana/_settings";
        String jsonStr = "{\"index.blocks.write\":true}";
        HttpResponse response = api.put(esBaseUrl + path, jsonStr, false);
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        boolean acknowledged = json.getBoolean("acknowledged");
        if (! acknowledged) {
            throw new IOException("Settings block writes failed!");
        }
    }

    public void createKibana6Index(RestApi api) throws IOException, InterruptedException {
        String path = "/.kibana-6";
        String file = "buildSrc/src/main/resources/mappings6.json";
        String jsonStr = new String(Files.readAllBytes(Paths.get(file)));
        HttpResponse response = api.put(esBaseUrl + path, jsonStr, false);
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        boolean acknowledged = json.getBoolean("acknowledged");
        boolean shards_acknowledged = json.getBoolean("shards_acknowledged");
        if (! acknowledged) {
            throw new IOException("Create Kibana index failed!");
        }
        if (! shards_acknowledged) {
            throw new IOException("Create Kibana index shards failed!");
        }
    }

    public void reindexKibana6Index(RestApi api) throws IOException, InterruptedException {
        String path = "/_reindex?wait_for_completion=true";
        String file = "buildSrc/src/main/resources/reindex6.json";
        String jsonStr = new String(Files.readAllBytes(Paths.get(file)));
        HttpResponse response = api.post(esBaseUrl + path, jsonStr, false);
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        JSONArray failures = json.getJSONArray("failures");
        if (failures.length() > 0) {
            throw new IOException("Reindex Kibana index failed!");
        }
    }

    public void aliasKibana6Index(RestApi api) throws IOException, InterruptedException {
        String path = "/_aliases";
        String file = "buildSrc/src/main/resources/alias6.json";
        String jsonStr = new String(Files.readAllBytes(Paths.get(file)));
        HttpResponse response = api.post(esBaseUrl + path, jsonStr, false);
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONObject json = new JSONObject(content);
        boolean acknowledged = json.getBoolean("acknowledged");
        if (! acknowledged) {
            throw new IOException("Settings block writes failed!");
        }
    }
}
