package io.zonky.test.db.provider;

import javax.sql.DataSource;
import java.util.Map;

public interface EmbeddedDatabase extends DataSource {

    String getUrl();

    String getServerName();

    String getDatabaseName();

    String getUser();

    String getPassword();

    int getPortNumber();

    Map<String, String> getAliases();

    void shutdown();

}
