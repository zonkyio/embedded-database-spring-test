package io.zonky.test.db.provider;

import com.google.common.collect.ImmutableMap;

import javax.sql.DataSource;
import java.util.Map;

public class DatabaseResult {

    private final DataSource dataSource;
    private final String databaseName;
    private final Map<String, String> aliases;

    public DatabaseResult(DataSource dataSource, String databaseName) {
        this(dataSource, databaseName, ImmutableMap.of());
    }

    public DatabaseResult(DataSource dataSource, String databaseName, Map<String, String> aliases) {
        this.dataSource = dataSource;
        this.databaseName = databaseName;
        this.aliases = ImmutableMap.copyOf(aliases);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }
}
