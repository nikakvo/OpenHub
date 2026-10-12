package com.thirtydegreesray.openhub.mvp.model;

/**
 * Answer of POST /repos/{owner}/{repo}/releases/generate-notes: a suggested release
 * title and the Markdown notes GitHub builds from the merged pull requests.
 */
public class GeneratedNotes {

    private String name;
    private String body;

    public String getName() {
        return name;
    }

    public String getBody() {
        return body;
    }
}
