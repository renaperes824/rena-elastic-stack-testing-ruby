package org.estf.gradle.rest;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.HttpClient;
import org.awaitility.core.ConditionTimeoutException;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.Map;

import static org.awaitility.Awaitility.await;

public class BaseEndpoint {

    protected RestUtil utilLib;
    protected Instance instance;
    private final String endpointPath;
    private final HttpClient client;


    public BaseEndpoint(Instance instance, String endpointPath) {
        this.instance = instance;
        this.endpointPath = endpointPath;
        this.client = HttpClientBuilder.create().build();
        this.utilLib = new RestUtil();
    }

    protected String getMessage() {
        return "send the request";
    }

    protected void sendRequest(HttpEntityEnclosingRequestBase request) throws IOException {
        try {
             await().atMost(Duration.ofSeconds(25))
                    .pollInterval(Duration.ofSeconds(5))
                    .until(sendRequestCallable(request));
        } catch(ConditionTimeoutException e ) {
            throw new IOException("Failed " + getMessage());
        }
    }

    private Callable<Boolean> sendRequestCallable(HttpEntityEnclosingRequestBase request) {
        return () -> {
            HttpResponse response = client.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200 || statusCode == 201) {
                return true;
            } else {
                System.out.println("** Retrying " + getMessage() + " **");
                return false;
            }
        };
    }

    protected String getBase64EncodedAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    protected String getUrl(String baseUrl) {
        return  (baseUrl + endpointPath);
    }

    protected String getUrl(String baseUrl, Map<String, String> pathParams) {
        String url = this.getUrl(baseUrl);
        for (Map.Entry<String, String> entry: pathParams.entrySet()) {
            url = url.replace(entry.getKey(), entry.getValue());
        }
        return url;
    }
}
