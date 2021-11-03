package org.estf.gradle.rest;

import java.io.IOException;
import java.util.HashMap;

public class DocumentCreation extends BaseEndpoint{
    private final String documentPath;
    private final String index;
    private static final String endpointPath = "/{index}/_doc";

    public DocumentCreation(Instance instance, String index, String documentPath){
        super(instance, endpointPath);
        this.index = index;
        this.documentPath = documentPath;
    }

    @Override
    protected String getMessage() {
        return "to create document";
    }

    public void sendPostRequest() throws IOException {
        sendRequest(utilLib.getPostEntityForFile(getUrl(instance.esBaseUrl, new HashMap<>() {{
            put("{index}", index);
        }}), getBase64EncodedAuth(instance.username, instance.password), documentPath));
    }

}
