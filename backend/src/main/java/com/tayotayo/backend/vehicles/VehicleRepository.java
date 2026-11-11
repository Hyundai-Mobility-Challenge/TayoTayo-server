package com.tayotayo.backend.vehicles;

import java.util.List;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class VehicleRepository {
    private static final RowMapper<VehicleResponse> VEHICLE_ROW_MAPPER = (resultSet, rowNumber) ->
            new VehicleResponse(
                    resultSet.getLong("id"),
                    resultSet.getLong("org_id"),
                    resultSet.getString("name"),
                    resultSet.getString("plate_number"),
                    resultSet.getString("model"),
                    resultSet.getObject("seat_capacity", Integer.class),
                    resultSet.getString("status"));

    private final JdbcTemplate jdbcTemplate;

    public VehicleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public VehicleResponse create(long orgId, VehicleCreateRequest request) {
        String sql = """
                INSERT INTO vehicles (org_id, name, plate_number, model, seat_capacity, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, orgId);
            statement.setString(2, request.name().trim());
            setNullableString(statement, 3, request.plateNumber());
            setNullableString(statement, 4, request.model());
            if (request.seatCapacity() == null) {
                statement.setNull(5, Types.SMALLINT);
            } else {
                statement.setInt(5, request.seatCapacity());
            }
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("MySQL did not return the new vehicle ID.");
        }
        return new VehicleResponse(generatedId.longValue(), orgId, request.name().trim(),
                request.plateNumber(), request.model(), request.seatCapacity(), "ACTIVE");
    }

    public List<VehicleResponse> findPage(long orgId, Long afterId, int fetchSize) {
        String sql = """
                SELECT id, org_id, name, plate_number, model, seat_capacity, status
                FROM vehicles
                WHERE org_id = ?
                  AND deleted_at IS NULL
                """;

        if (afterId != null) {
            sql += " AND id > ?";
        }
        sql += " ORDER BY id ASC LIMIT ?";

        if (afterId == null) {
            return jdbcTemplate.query(sql, VEHICLE_ROW_MAPPER, orgId, fetchSize);
        }
        return jdbcTemplate.query(sql, VEHICLE_ROW_MAPPER, orgId, afterId, fetchSize);
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws java.sql.SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.trim());
        }
    }
}
