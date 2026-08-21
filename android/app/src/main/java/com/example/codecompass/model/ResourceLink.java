package com.example.codecompass.model;

import java.net.URI;

public class ResourceLink {

    public final String title;
    public final String url;

    public ResourceLink(String title, String url) {
        this.title = title;
        this.url   = url;
    }

    public String getHostname() {
        try {
            String host = new URI(url).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }
}
