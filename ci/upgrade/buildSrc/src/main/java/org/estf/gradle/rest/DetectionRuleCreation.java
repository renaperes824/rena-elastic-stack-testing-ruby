package org.estf.gradle.rest;

import java.io.IOException;

public class DetectionRuleCreation extends BaseEndpoint {
    private final String rulePath;
    private static final String endpointPath = "/api/detection_engine/rules";

    public DetectionRuleCreation(Instance instance, String rulePath) {
        super(instance, endpointPath);
        this.rulePath = rulePath;
    }

    @Override
    protected String getMessage() {
        return "to create detection rule";
    }

    public void sendPostRequest() throws IOException {
        sendRequest(utilLib.getPostEntityForFile(getUrl(instance.kbnBaseUrl), getBase64EncodedAuth(instance.username, instance.password), rulePath));
    }
}
