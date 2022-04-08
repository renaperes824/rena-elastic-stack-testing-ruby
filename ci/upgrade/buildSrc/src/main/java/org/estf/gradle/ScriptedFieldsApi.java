package org.estf.gradle;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * UpgradeAssistantApi
 *
 * @author  Liza Mae Dayoub
 *
 */
public class ScriptedFieldsApi extends DefaultTask {

    @Input
    public String kbnBaseUrl;

    @Input
    public String username;

    @Input
    public String password;

    @TaskAction
    public void run() throws IOException, InterruptedException {
        RestApi api = new RestApi(username, password, null, null);
        updateScriptedField(api);
        System.out.println("Update scripted fields completed successfully");
    }

    public String getIndexPatternId(RestApi api, String name, String space) throws IOException, InterruptedException {
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
    }

    public boolean useRuntimeField(RestApi api, String path) {
        try {
            api.setMaxRetries(0);
            api.get(kbnBaseUrl + path);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public void updateScriptedField(RestApi api) throws IOException, InterruptedException {
        String field_path = "/scripted_field/hour_of_day";
        String json_body = "{\"field\": {\"script\": \"doc['timestamp'].value.getHour()\"}}";

        String runtime_field_path = "/runtime_field/hour_of_day";
        String runtime_json_body = "{\n" +
                "  \"runtimeField\": {\n" +
                "     \"type\": \"long\",\n" +
                "     \"script\": {\n" +
                "        \"source\": \"emit(doc[\\\"timestamp\\\"].value.getHour())\"\n" +
                "      }\n" +
                "  }\n" +
                "}";

        String[] ids = {this.getIndexPatternId(api, "kibana_sample_data_flights", ""),
                        this.getIndexPatternId(api, "kibana_sample_data_logs", ""),
                        this.getIndexPatternId(api, "kibana_sample_data_flights", "automation"),
                        this.getIndexPatternId(api, "kibana_sample_data_logs", "automation")};
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            String space = "";
            if (i >= 2) {
                space = "/s/automation";
            }
            String path = space + "/api/index_patterns/index_pattern/" + ids[i] + field_path;
            String runtime_path = space + "/api/index_patterns/index_pattern/" + ids[i] + runtime_field_path;
            if (useRuntimeField(api, runtime_path)) {
                path = runtime_path;
                json_body = runtime_json_body;
            }
            try {
                api.setMaxRetries(0);
                api.post(kbnBaseUrl + path, json_body, true);
            } catch (Exception ex) {
                errors.add(ex.toString());
            }
        }
        if (errors.size() > 0) {
            for (String error : errors) {
                System.out.println(error);
            }
            throw new IOException("Updating scripted/runtime fields failed!");
        }
    }
}
