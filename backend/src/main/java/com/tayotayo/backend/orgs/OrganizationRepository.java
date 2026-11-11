package com.tayotayo.backend.orgs;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OrganizationResponse create(OrganizationCreateRequest request, String orgType, String timezone) {
        String sql = """
                INSERT INTO organizations (name, org_type, phone, address, latitude, longitude, timezone, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.name().trim());
            statement.setString(2, orgType);
            setNullableString(statement, 3, request.phone());
            setNullableString(statement, 4, request.address());
            setNullableDouble(statement, 5, request.latitude());
            setNullableDouble(statement, 6, request.longitude());
            statement.setString(7, timezone);
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("MySQL did not return the new organization ID.");
        }
        return new OrganizationResponse(generatedId.longValue(), request.name().trim(), orgType,
                request.phone(), request.address(), request.latitude(), request.longitude(), timezone, "ACTIVE");
    }

    public boolean existsActive(long orgId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM organizations WHERE id = ? AND deleted_at IS NULL AND status = 'ACTIVE')",
                Boolean.class,
                orgId);
        return Boolean.TRUE.equals(exists);
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws java.sql.SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.trim());
        }
    }

    private void setNullableDouble(PreparedStatement statement, int index, Double value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.DOUBLE);
        } else {
            statement.setDouble(index, value);
        }
    }
}
