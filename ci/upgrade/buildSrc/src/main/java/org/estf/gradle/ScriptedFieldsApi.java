package org.estf.gradle;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONArray;
import org.json.JSONObject;

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
    public String finalVersion;

    @TaskAction
    public void run() {
        RestApi api = new RestApi(username, password, null, null);
        updateScriptedField(api);
        System.out.println("Update scripted fields completed successfully");
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
        } catch (Exception ignored) {
            System.out.println(ignored);
        }
        return "";
    }

    public void updateScriptedField(RestApi api) {
        ComparableVersion currentVersion = new ComparableVersion(finalVersion);
        ComparableVersion version7_17 = new ComparableVersion("7.17.0");
        String field_path = "/scripted_field/hour_of_day";
        String json_body = "{\"field\": {\"script\": \"doc['timestamp'].value.getHour()\"}}";
        if (currentVersion.compareTo(version7_17) >= 0) {
            field_path = "/runtime_field/hour_of_day";
            json_body = "{\n" +
                    "  \"runtimeField\": {\n" +
                    "     \"script\": {\n" +
                    "        \"source\": \"emit(doc[\\\"timestamp\\\"].value.getHour())\"\n" +
                    "      }\n" +
                    "  }\n" +
                    "}";
        }
        try {
            String[] ids = {this.getIndexPatternId(api, "kibana_sample_data_flights", ""),
                            this.getIndexPatternId(api, "kibana_sample_data_logs", ""),
                            this.getIndexPatternId(api, "kibana_sample_data_flights", "automation"),
                            this.getIndexPatternId(api, "kibana_sample_data_logs", "automation")};
            for (int i = 0; i < ids.length; i++) {
                String path = "/api/index_patterns/index_pattern/" + ids[i] + field_path;
                if (i>=2) {
                    path = "/s/automation/api/index_patterns/index_pattern/" + ids[i] + field_path;
                }
                api.post(kbnBaseUrl + path,
                        json_body,
                        true);
            }
        } catch (Exception ignored) {
            System.out.println(ignored);
        }
    }
}
