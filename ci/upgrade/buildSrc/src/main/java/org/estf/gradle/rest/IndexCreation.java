package org.estf.gradle.rest;

import java.io.IOException;

public class IndexCreation extends BaseEndpoint {

    public IndexCreation(Instance instance, String index) {
        super(instance, index);
    }

    @Override
    protected String getMessage() {
        return "to create index";
    }

    public void sendPutRequest() throws IOException {
        sendRequest(utilLib.getPutEntityForString(getUrl(instance.esBaseUrl), getBase64EncodedAuth(instance.username, instance.password), ""));
    }


}
