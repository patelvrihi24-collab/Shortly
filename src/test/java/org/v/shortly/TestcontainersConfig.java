package org.v.shortly;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;     // 2.x package; IntelliJ will suggest the right one

@TestConfiguration(proxyBeanMethods = false)                  // beans that exist only in tests
public class TestcontainersConfig {

    @Bean
    @ServiceConnection                                        // Spring Boot reads host/port/user/password from the
    PostgreSQLContainer postgres() {                          // container and sets spring.datasource.* for us
        return new PostgreSQLContainer("postgres:16");        // same version as docker-compose
    }

    @Bean
    @ServiceConnection(name = "redis")                        // a generic container needs the service name spelled out
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:7").withExposedPorts(6379);
    }
}