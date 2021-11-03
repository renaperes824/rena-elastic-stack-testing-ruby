package org.estf.gradle.rest;

import java.io.IOException;
import java.util.HashMap;

public class FieldsLimitMappingIncrease extends BaseEndpoint {

    private final String body;
    private final String index;
    private static final String endpointPath = "/{index}/_settings";

    public FieldsLimitMappingIncrease(Instance instance, String index ) {
        super(instance, endpointPath);
        this.body = "{\"index.mapping.total_fields.limit\":2000}";
        this.index = index;
    }

    @Override
    protected String getMessage() {
        return "to create document";
    }

    public void sendPutRequest() throws IOException {
        String message = "increase the number of fields limit in mapping";
        sendRequest(utilLib.getPutEntityForString(getUrl(instance.esBaseUrl, new HashMap<>() {{
            put("{index}", index);
        }}), getBase64EncodedAuth(instance.username, instance.password), body));
    }
}
