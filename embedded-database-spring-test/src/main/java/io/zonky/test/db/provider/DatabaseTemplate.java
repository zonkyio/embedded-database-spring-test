package io.zonky.test.db.provider;

public class DatabaseTemplate {

    private final String templateName;

    public DatabaseTemplate(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateName() {
        return templateName;
    }
}
