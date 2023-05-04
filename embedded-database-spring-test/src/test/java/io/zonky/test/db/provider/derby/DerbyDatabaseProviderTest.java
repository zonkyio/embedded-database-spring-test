/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zonky.test.db.provider.derby;

import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.support.TestDatabasePreparer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class DerbyDatabaseProviderTest {

    @Test
    public void testGetDatabase() throws Exception {
        DerbyDatabaseProvider provider = new DerbyDatabaseProvider();

        DatabasePreparer preparer1 = TestDatabasePreparer.of(dataSource -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.update("create table prime_number (number int primary key not null)");
        });

        DatabasePreparer preparer2 = TestDatabasePreparer.of(dataSource -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.update("create table prime_number (id int primary key not null, number int not null)");
        });

        DataSource dataSource1 = provider.createDatabase(preparer1);
        DataSource dataSource2 = provider.createDatabase(preparer1);
        DataSource dataSource3 = provider.createDatabase(preparer2);

        assertThat(dataSource1).isNotNull().isExactlyInstanceOf(DerbyEmbeddedDatabase.class);
        assertThat(dataSource2).isNotNull().isExactlyInstanceOf(DerbyEmbeddedDatabase.class);
        assertThat(dataSource3).isNotNull().isExactlyInstanceOf(DerbyEmbeddedDatabase.class);

        JdbcTemplate jdbcTemplate1 = new JdbcTemplate(dataSource1);
        jdbcTemplate1.update("insert into prime_number (number) values (?)", 2);
        assertThat(jdbcTemplate1.queryForObject("select count(*) from prime_number", Integer.class)).isEqualTo(1);

        JdbcTemplate jdbcTemplate2 = new JdbcTemplate(dataSource2);
        jdbcTemplate2.update("insert into prime_number (number) values (?)", 3);
        assertThat(jdbcTemplate2.queryForObject("select count(*) from prime_number", Integer.class)).isEqualTo(1);

        JdbcTemplate jdbcTemplate3 = new JdbcTemplate(dataSource3);
        jdbcTemplate3.update("insert into prime_number (id, number) values (?, ?)", 1, 5);
        assertThat(jdbcTemplate3.queryForObject("select count(*) from prime_number", Integer.class)).isEqualTo(1);
    }

    @Test
    public void providersWithDefaultConfigurationShouldEquals() {
        DerbyDatabaseProvider provider1 = new DerbyDatabaseProvider();
        DerbyDatabaseProvider provider2 = new DerbyDatabaseProvider();

        assertThat(provider1).isEqualTo(provider2);
    }
}