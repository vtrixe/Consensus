package com.example.consensus.tenant;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class TenantSchemaDataSource extends DelegatingDataSource {

    public TenantSchemaDataSource(DataSource delegate) {
        setTargetDataSource(delegate);
        afterPropertiesSet();
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = super.getConnection();
        applySchema(conn);
        return conn;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conn = super.getConnection(username, password);
        applySchema(conn);
        return conn;
    }

    private void applySchema(Connection conn) throws SQLException {
        String schema = TenantContext.get();
        String path = (schema != null) ? "\"" + schema + "\", public" : "public";
        try (var stmt = conn.createStatement()) {
            stmt.execute("SET search_path = " + path);
        }
    }
}
