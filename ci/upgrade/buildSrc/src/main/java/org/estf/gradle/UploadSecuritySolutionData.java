/**
 * Creates an alert for Security Solution
 *
 *
 * @author  Gloria Hornero
 *
 */

package org.estf.gradle;

import org.estf.gradle.rest.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.*;

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

    @TaskAction
    public void run() throws IOException, InterruptedException {
        RestApi api = new RestApi(username, password, version, upgradeVersion);
        Instance instance = new Instance(username, password, esBaseUrl, kbnBaseUrl);

        int majorVersion = api.setMajorVersion();
        if (majorVersion > 6) {
            createsSiemSignalsIndex(instance);
            createsDetectionRule(instance);
            createsAuditbeatIndex(instance);
            increasesNumberOfFieldsLimitForMapping(instance);
            createsAuditbeatMapping(instance);
            createsDocumentToGenerateAlert(instance);
        }
    }

    public void createsSiemSignalsIndex(Instance instance) throws IOException {
        SiemSignalsIndexCreation endpoint = new SiemSignalsIndexCreation(instance);
        endpoint.sendPostRequest();
    }

    public void createsDetectionRule(Instance instance) throws IOException, InterruptedException {
        DetectionRuleCreation endpoint = new DetectionRuleCreation(instance, "buildSrc/src/main/resources/detectionRule.json");
        endpoint.sendPostRequest();
    }

    public void createsAuditbeatIndex(Instance instance) throws IOException, InterruptedException {
        IndexCreation endpoint = new IndexCreation(instance, "/auditbeat-upgrade-1");
        endpoint.sendPutRequest();
    }

    public void increasesNumberOfFieldsLimitForMapping(Instance instance) throws IOException {
        FieldsLimitMappingIncrease endpoint = new FieldsLimitMappingIncrease(instance, "auditbeat-upgrade-1");
        endpoint.sendPutRequest();
    }

    public void createsAuditbeatMapping(Instance instance) throws IOException, InterruptedException {
        MappingCreation endpoint = new MappingCreation(instance, "auditbeat-upgrade-1", "buildSrc/src/main/resources/auditbeatMapping.json");
        endpoint.sendPutRequest();
    }


    public void createsDocumentToGenerateAlert(Instance instance) throws IOException, InterruptedException {
        DocumentCreation endpoint = new DocumentCreation(instance, "auditbeat-upgrade-1", "buildSrc/src/main/resources/auditbeatDoc.json");
        endpoint.sendPostRequest();
    }
}
