package io.zonky.test.db.provider;

import java.util.Objects;

public class DatabaseTemplate {

    private final String templateName;

    public DatabaseTemplate(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateName() {
        return templateName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatabaseTemplate that = (DatabaseTemplate) o;
        return Objects.equals(templateName, that.templateName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templateName);
    }
}
