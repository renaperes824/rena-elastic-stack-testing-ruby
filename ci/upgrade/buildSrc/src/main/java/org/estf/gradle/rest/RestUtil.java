package org.estf.gradle.rest;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RestUtil {

    public HttpPost getPostEntityForFile(String url, String authHeader, String filePath) throws IOException {
        String jsonStr = new String(Files.readAllBytes(Paths.get(filePath)));
        return getPostEntityForString(url, authHeader, jsonStr);
    }

    public HttpPost getPostEntityForString(String url, String authHeader, String jsonStr) {
        HttpPost postRequest = new HttpPost(url);
        try {
            StringEntity entity = new StringEntity(jsonStr);
            entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            postRequest.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
            String headerName = "kbn-xsrf";
            String headerValue = "automation";
            postRequest.setHeader(headerName, headerValue);
            postRequest.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            postRequest.setEntity(entity);
        } catch (Exception ignored) {
        }

        return postRequest;
    }

    public HttpPut getPutEntityForFile(String url, String authHeader, String filePath) throws IOException {
        String jsonStr = new String(Files.readAllBytes(Paths.get(filePath)));
        return getPutEntityForString(url, authHeader, jsonStr);
    }


    public HttpPut getPutEntityForString(String url, String authHeader, String jsonStr) {
        HttpPut putRequest = new HttpPut(url);
        putRequest.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        try {
            StringEntity entity = new StringEntity(jsonStr);
            entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            putRequest.setEntity(entity);
        } catch (Exception ignored) {
        }

        return putRequest;
    }
}


