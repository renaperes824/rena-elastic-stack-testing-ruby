package org.estf.gradle.rest;

import java.io.IOException;
import java.util.HashMap;

public class MappingCreation extends BaseEndpoint{
    private final String mappingPath;
    private final String index;
    private static final String endpointPath = "/{index}/_mapping";

    public MappingCreation(Instance instance, String index, String mappingPath){
        super(instance, endpointPath);
        this.index = index;
        this.mappingPath = mappingPath;
    }

    @Override
    protected String getMessage() {
        return "to create mapping";
    }

    public void sendPutRequest() throws IOException {
        sendRequest(utilLib.getPutEntityForFile(getUrl(instance.esBaseUrl, new HashMap<>() {{
            put("{index}", index);
        }}), getBase64EncodedAuth(instance.username, instance.password), mappingPath));
    }

}
