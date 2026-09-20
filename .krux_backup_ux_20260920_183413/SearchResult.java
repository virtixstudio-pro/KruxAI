package com.virtixstudio.kruxai.models;

import java.net.URI;

public class SearchResult {
    private String title;
    private String snippet;
    private String url;
    private String domain;

    public SearchResult() {}

    public SearchResult(String title, String snippet, String url) {
        this.title = title;
        this.snippet = snippet;
        this.url = url;
        this.domain = extractDomain(url);
    }

    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String domain = uri.getHost();
            return domain != null && domain.startsWith("www.") ? domain.substring(4) : domain;
        } catch (Exception e) {
            return "web";
        }
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public String getUrl() { return url; }
    public void setUrl(String url) { 
        this.url = url;
        this.domain = extractDomain(url);
    }

    public String getDomain() { return domain; }
}
