package org.estf.gradle.rest;

import java.io.File;

public class Instance {

    protected String username;
    protected String password;
    protected String esBaseUrl;
    protected String kbnBaseUrl;

    public Instance(String username, String password, String esBaseUrl, String kbnBaseUrl){
        this.username = username;
        this.password = password;
        this.esBaseUrl = esBaseUrl;
        this.kbnBaseUrl = kbnBaseUrl;
    }
}
