package com.thirtydegreesray.openhub.mvp.model;

/** One template of GET /gitignore/templates/{name}: its name and the file text. */
public class GitignoreTemplate {

    private String name;
    private String source;

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }
}
