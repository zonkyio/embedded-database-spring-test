package io.zonky.test.db.provider;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

public interface EmbeddedDatabase extends DataSource {

    // TODO: use connection.getMetaData().getURL() instead
    String getUrl();

    String getServerName();

    String getDatabaseName();

    String getUser();

    String getPassword();

    int getPortNumber();

    Map<String, String> getAliases();

    void close() throws SQLException; // TODO: remove checked exception from the signature of the method

}
