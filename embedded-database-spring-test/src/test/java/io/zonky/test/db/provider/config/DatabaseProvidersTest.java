package io.zonky.test.db.provider.config;

import io.zonky.test.db.context.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabaseProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static io.zonky.test.assertj.MockitoAssertions.mockWithName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@RunWith(SpringRunner.class)
@ContextConfiguration
public class DatabaseProvidersTest {

    @Configuration
    @Import(DatabaseProviders.class)
    static class Config {

        @Bean
        @Provider(type = "provider1", database = "database1")
        public DatabaseProvider provider1() {
            return mock(DatabaseProvider.class, "mockProvider1");
        }

        @Bean
        @Provider(type = "provider1", database = "database2")
        public DatabaseProvider provider2() {
            return mock(DatabaseProvider.class, "mockProvider2");
        }

        @Bean
        @Provider(type = "provider2", database = "database2")
        public DatabaseProvider provider3() {
            return mock(DatabaseProvider.class, "mockProvider3");
        }
    }

    @Autowired
    private DatabaseProviders databaseProviders;

    @Test
    public void testProviders() {
        assertThat(databaseProviders.getProvider(DatabaseDescriptor.of("database1", "provider1"))).is(mockWithName("mockProvider1"));
        assertThat(databaseProviders.getProvider(DatabaseDescriptor.of("database2", "provider1"))).is(mockWithName("mockProvider2"));
        assertThat(databaseProviders.getProvider(DatabaseDescriptor.of("database2", "provider2"))).is(mockWithName("mockProvider3"));
    }

    @Test
    public void missingProvider() {
        assertThatCode(() -> databaseProviders.getProvider(DatabaseDescriptor.of("database2", "provider3")))
                .isExactlyInstanceOf(MissingDatabaseProviderException.class)
                .hasMessage("Missing database provider for: DatabaseDescriptor{databaseName=database2, providerName=provider3}, available providers: [provider1, provider2]");
    }
}