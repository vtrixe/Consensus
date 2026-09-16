package com.example.consensus.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public Tenant register(String name) {
        if (tenantRepository.existsByName(name)) {
            throw new IllegalArgumentException("Tenant already exists: " + name);
        }

        String schema = "tenant_" + name.toLowerCase().replaceAll("[^a-z0-9]", "_");
        String apiKey = UUID.randomUUID().toString();

        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
        log.info("Created schema={}", schema);

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration/tenant")
                .load()
                .migrate();
        log.info("Flyway migrated schema={}", schema);

        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setSchemaName(schema);
        tenant.setApiKey(apiKey);
        return tenantRepository.save(tenant);
    }
}
