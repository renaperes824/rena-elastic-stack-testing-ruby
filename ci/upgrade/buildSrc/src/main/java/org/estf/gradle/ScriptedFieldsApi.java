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
public class ScriptedFieldsApi extends DefaultTask {

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

    @TaskAction
    public void run() throws IOException, InterruptedException {
        RestApi api = new RestApi(username, password, version, upgradeVersion);
        updateScriptedField(api);
    }

    public String getIndexPatternId(RestApi api, String name, String space) {
        try {
            String path = "/api/saved_objects/_find?type=index-pattern&search_fields=title&search=" + name;
            if (!space.isEmpty()) {
                path = "/s/" + space + "/api/saved_objects/_find?type=index-pattern&search_fields=title&search=" + name;
            }
            HttpResponse response = api.get(kbnBaseUrl + path);
            HttpEntity entity = response.getEntity();
            String content = EntityUtils.toString(entity);
            JSONObject json = new JSONObject(content);
            JSONArray saved_objects = json.getJSONArray("saved_objects");
            JSONObject elem = saved_objects.getJSONObject(0);
            return elem.getString("id");
        } catch (Exception ignored) {}
        return "";
    }

    public void updateScriptedField(RestApi api) {
        try {
            String[] ids = {this.getIndexPatternId(api, "kibana_sample_data_flights", ""),
                            this.getIndexPatternId(api, "kibana_sample_data_logs", ""),
                            this.getIndexPatternId(api, "kibana_sample_data_flights", "automation"),
                            this.getIndexPatternId(api, "kibana_sample_data_logs", "automation")};
            for (int i = 0; i < ids.length; i++) {
                String path = "/api/index_patterns/index_pattern/" + ids[i] + "/scripted_field/hour_of_day";
                api.post(kbnBaseUrl + path,
                        "{\"field\": {\"script\": \"doc['timestamp'].value.getHour()\"}}",
                        true);
            }
        } catch (Exception ignored) {}
    }
}
