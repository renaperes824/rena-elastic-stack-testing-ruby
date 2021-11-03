package org.estf.gradle.rest;

import java.io.IOException;

public class SiemSignalsIndexCreation extends BaseEndpoint {
    private static final String body = "";
    private static final String endpointPath = "/api/detection_engine/index";

    public SiemSignalsIndexCreation(Instance instance) {
        super(instance, endpointPath);
    }

    @Override
    protected String getMessage() {
        return "to create the siem signal index";
    }

    public void sendPostRequest() throws IOException {
        sendRequest(utilLib.getPostEntityForString(getUrl(instance.kbnBaseUrl), getBase64EncodedAuth(instance.username, instance.password), body));
    }
}
