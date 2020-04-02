package io.zonky.test.db.provider;

import javax.sql.DataSource;
import java.util.Map;

public interface EmbeddedDatabase extends DataSource {

    String getUrl();

    String getUsername();

    String getPassword();

    Map<String, String> getAliases();

    void close();

}
