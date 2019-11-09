package io.zonky.test.db.provider;

import javax.sql.DataSource;
import java.util.Map;

public interface EmbeddedDatabase extends DataSource {

    String getServerName();

    String getDatabaseName();

    String getUser();

    String getPassword();

    int getPortNumber();

    String getUrl();

    Map<String, String> getAliases();

}
