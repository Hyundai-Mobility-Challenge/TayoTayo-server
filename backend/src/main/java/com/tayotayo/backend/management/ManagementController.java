package com.tayotayo.backend.management;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** CRUD endpoints for the first organization setup flow: children, stops and routes. */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}")
public class ManagementController {
    private final JdbcTemplate jdbc;

    public ManagementController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/children")
    public Map<String, Object> children(@PathVariable("orgId") long orgId,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return page("children", orgId, limit, cursor);
    }

    @PostMapping("/children")
    public Map<String, Object> createChild(@PathVariable("orgId") long orgId,
            @RequestBody Map<String, Object> body) {
        String name = requiredText(body, "name", 50);
        String className = optionalText(body, "className", 50);
        jdbc.update("INSERT INTO children (org_id, name, class_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                orgId, name, className);
        return one("SELECT id, org_id AS orgId, name, class_name AS className, status FROM children WHERE id = LAST_INSERT_ID()");
    }

    @GetMapping("/children/{childId}")
    public Map<String, Object> child(@PathVariable("orgId") long orgId, @PathVariable("childId") long childId) {
        return findOne("SELECT id, org_id AS orgId, name, class_name AS className, status FROM children WHERE org_id = ? AND id = ? AND deleted_at IS NULL",
                orgId, childId);
    }

    @PatchMapping("/children/{childId}")
    public Map<String, Object> updateChild(@PathVariable("orgId") long orgId,
            @PathVariable("childId") long childId, @RequestBody Map<String, Object> body) {
        requireExists("SELECT COUNT(*) FROM children WHERE org_id = ? AND id = ? AND deleted_at IS NULL", orgId, childId);
        if (body.containsKey("name")) {
            jdbc.update("UPDATE children SET name = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE org_id = ? AND id = ?",
                    requiredText(body, "name", 50), orgId, childId);
        }
        if (body.containsKey("className")) {
            jdbc.update("UPDATE children SET class_name = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE org_id = ? AND id = ?",
                    optionalText(body, "className", 50), orgId, childId);
        }
        if (body.containsKey("status")) {
            String status = requiredText(body, "status", 20).toUpperCase();
            if (!List.of("ACTIVE", "INACTIVE", "GRADUATED").contains(status)) badRequest("status 값이 올바르지 않습니다.");
            jdbc.update("UPDATE children SET status = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE org_id = ? AND id = ?", status, orgId, childId);
        }
        return child(orgId, childId);
    }

    @GetMapping("/stops")
    public Map<String, Object> stops(@PathVariable("orgId") long orgId,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return page("stops", orgId, limit, cursor);
    }

    @PostMapping("/stops")
    public Map<String, Object> createStop(@PathVariable("orgId") long orgId,
            @RequestBody Map<String, Object> body) {
        String name = requiredText(body, "name", 100);
        String address = optionalText(body, "address", 255);
        double lat = requiredNumber(body, "latitude", -90, 90);
        double lng = requiredNumber(body, "longitude", -180, 180);
        int radius = body.containsKey("arrivalRadiusM") ? (int) requiredNumber(body, "arrivalRadiusM", 1, 32767) : 50;
        String memo = optionalText(body, "memo", 255);
        jdbc.update("INSERT INTO stops (org_id, name, address, latitude, longitude, arrival_radius_m, memo) VALUES (?, ?, ?, ?, ?, ?, ?)",
                orgId, name, address, lat, lng, radius, memo);
        return one("SELECT id, org_id AS orgId, name, address, latitude, longitude, arrival_radius_m AS arrivalRadiusM, memo, is_active AS isActive FROM stops WHERE id = LAST_INSERT_ID()");
    }

    @PatchMapping("/stops/{stopId}")
    public Map<String, Object> updateStop(@PathVariable("orgId") long orgId, @PathVariable("stopId") long stopId,
            @RequestBody Map<String, Object> body) {
        requireExists("SELECT COUNT(*) FROM stops WHERE org_id = ? AND id = ? AND deleted_at IS NULL", orgId, stopId);
        if (body.containsKey("name")) jdbc.update("UPDATE stops SET name = ? WHERE org_id = ? AND id = ?", requiredText(body, "name", 100), orgId, stopId);
        if (body.containsKey("address")) jdbc.update("UPDATE stops SET address = ? WHERE org_id = ? AND id = ?", optionalText(body, "address", 255), orgId, stopId);
        if (body.containsKey("latitude")) jdbc.update("UPDATE stops SET latitude = ? WHERE org_id = ? AND id = ?", requiredNumber(body, "latitude", -90, 90), orgId, stopId);
        if (body.containsKey("longitude")) jdbc.update("UPDATE stops SET longitude = ? WHERE org_id = ? AND id = ?", requiredNumber(body, "longitude", -180, 180), orgId, stopId);
        if (body.containsKey("memo")) jdbc.update("UPDATE stops SET memo = ? WHERE org_id = ? AND id = ?", optionalText(body, "memo", 255), orgId, stopId);
        if (body.containsKey("isActive")) jdbc.update("UPDATE stops SET is_active = ? WHERE org_id = ? AND id = ?", Boolean.TRUE.equals(body.get("isActive")), orgId, stopId);
        jdbc.update("UPDATE stops SET updated_at = CURRENT_TIMESTAMP(3) WHERE org_id = ? AND id = ?", orgId, stopId);
        return findOne("SELECT id, org_id AS orgId, name, address, latitude, longitude, arrival_radius_m AS arrivalRadiusM, memo, is_active AS isActive FROM stops WHERE org_id = ? AND id = ? AND deleted_at IS NULL", orgId, stopId);
    }

    @GetMapping("/routes")
    public Map<String, Object> routes(@PathVariable("orgId") long orgId,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return page("routes", orgId, limit, cursor);
    }

    @PostMapping("/routes")
    public Map<String, Object> createRoute(@PathVariable("orgId") long orgId,
            @RequestBody Map<String, Object> body) {
        String name = requiredText(body, "name", 100);
        long vehicleId = requiredId(body, "vehicleId");
        requireExists("SELECT COUNT(*) FROM vehicles WHERE org_id = ? AND id = ? AND deleted_at IS NULL", orgId, vehicleId);
        String direction = body.get("direction") == null ? "DROP_OFF" : requiredText(body, "direction", 10).toUpperCase();
        if (!List.of("DROP_OFF", "PICK_UP").contains(direction)) badRequest("direction은 DROP_OFF 또는 PICK_UP이어야 합니다.");
        Object departure = body.get("departureTime");
        jdbc.update("INSERT INTO routes (org_id, vehicle_id, name, direction, departure_time, is_active) VALUES (?, ?, ?, ?, ?, 1)",
                orgId, vehicleId, name, direction, departure);
        return one("SELECT id, org_id AS orgId, vehicle_id AS vehicleId, name, direction, departure_time AS departureTime, is_active AS isActive FROM routes WHERE id = LAST_INSERT_ID()");
    }

    @GetMapping("/routes/{routeId}")
    public Map<String, Object> route(@PathVariable("orgId") long orgId, @PathVariable("routeId") long routeId) {
        return findOne("SELECT id, org_id AS orgId, vehicle_id AS vehicleId, name, direction, departure_time AS departureTime, is_active AS isActive FROM routes WHERE org_id = ? AND id = ? AND deleted_at IS NULL", orgId, routeId);
    }

    @PatchMapping("/routes/{routeId}")
    public Map<String, Object> updateRoute(@PathVariable("orgId") long orgId, @PathVariable("routeId") long routeId,
            @RequestBody Map<String, Object> body) {
        requireExists("SELECT COUNT(*) FROM routes WHERE org_id = ? AND id = ? AND deleted_at IS NULL", orgId, routeId);
        if (body.containsKey("name")) jdbc.update("UPDATE routes SET name = ? WHERE org_id = ? AND id = ?", requiredText(body, "name", 100), orgId, routeId);
        if (body.containsKey("direction")) {
            String direction = requiredText(body, "direction", 10).toUpperCase();
            if (!List.of("DROP_OFF", "PICK_UP").contains(direction)) badRequest("direction은 DROP_OFF 또는 PICK_UP이어야 합니다.");
            jdbc.update("UPDATE routes SET direction = ? WHERE org_id = ? AND id = ?", direction, orgId, routeId);
        }
        if (body.containsKey("vehicleId")) {
            long vehicleId = requiredId(body, "vehicleId");
            requireExists("SELECT COUNT(*) FROM vehicles WHERE org_id = ? AND id = ? AND deleted_at IS NULL", orgId, vehicleId);
            jdbc.update("UPDATE routes SET vehicle_id = ? WHERE org_id = ? AND id = ?", vehicleId, orgId, routeId);
        }
        if (body.containsKey("departureTime")) jdbc.update("UPDATE routes SET departure_time = ? WHERE org_id = ? AND id = ?", body.get("departureTime"), orgId, routeId);
        if (body.containsKey("isActive")) jdbc.update("UPDATE routes SET is_active = ? WHERE org_id = ? AND id = ?", Boolean.TRUE.equals(body.get("isActive")), orgId, routeId);
        jdbc.update("UPDATE routes SET updated_at = CURRENT_TIMESTAMP(3) WHERE org_id = ? AND id = ?", orgId, routeId);
        return route(orgId, routeId);
    }

    @PutMapping("/routes/{routeId}/stops")
    @Transactional
    public Map<String, Object> setRouteStops(@PathVariable("orgId") long orgId, @PathVariable("routeId") long routeId,
            @RequestBody Map<String, Object> body) {
        requireExists("SELECT COUNT(*) FROM routes WHERE org_id = ? AND id = ? AND deleted_at IS NULL", orgId, routeId);
        Object rawStops = body.get("stops");
        if (!(rawStops instanceof List<?>)) badRequest("stops 배열이 필요합니다.");
        List<?> stops = (List<?>) rawStops;
        jdbc.update("DELETE FROM route_stops WHERE org_id = ? AND route_id = ?", orgId, routeId);
        int seq = 1;
        for (Object value : stops) {
            if (!(value instanceof Map<?, ?>)) badRequest("stops 항목은 객체여야 합니다.");
            Map<?, ?> stop = (Map<?, ?>) value;
            Object idValue = stop.get("stopId");
            long stopId = parsePositiveLong(idValue, "stopId");
            requireExists("SELECT COUNT(*) FROM stops WHERE org_id = ? AND id = ? AND deleted_at IS NULL", orgId, stopId);
            int order = stop.get("sequence") == null ? seq : (int) parsePositiveLong(stop.get("sequence"), "sequence");
            jdbc.update("INSERT INTO route_stops (org_id, route_id, stop_id, default_seq) VALUES (?, ?, ?, ?)", orgId, routeId, stopId, order);
            seq++;
        }
        return Map.of("routeId", routeId, "stops", jdbc.queryForList("SELECT rs.stop_id AS stopId, s.name, rs.default_seq AS sequence FROM route_stops rs JOIN stops s ON s.id = rs.stop_id WHERE rs.org_id = ? AND rs.route_id = ? ORDER BY rs.default_seq", orgId, routeId));
    }

    private Map<String, Object> page(String table, long orgId, int limit, String cursor) {
        if (orgId < 1 || limit < 1 || limit > 100) badRequest("orgId 또는 limit 값이 올바르지 않습니다.");
        long after = 0;
        if (cursor != null && !cursor.isBlank()) after = parsePositiveLong(cursor, "cursor");
        String sql = switch (table) {
            case "children" -> "SELECT id, org_id AS orgId, name, class_name AS className, status FROM children WHERE org_id = ? AND deleted_at IS NULL AND id > ? ORDER BY id LIMIT ?";
            case "stops" -> "SELECT id, org_id AS orgId, name, address, latitude, longitude, arrival_radius_m AS arrivalRadiusM, memo, is_active AS isActive FROM stops WHERE org_id = ? AND deleted_at IS NULL AND id > ? ORDER BY id LIMIT ?";
            case "routes" -> "SELECT id, org_id AS orgId, vehicle_id AS vehicleId, name, direction, departure_time AS departureTime, is_active AS isActive FROM routes WHERE org_id = ? AND deleted_at IS NULL AND id > ? ORDER BY id LIMIT ?";
            default -> throw new IllegalArgumentException("Unsupported table");
        };
        List<Map<String, Object>> rows = jdbc.queryForList(sql, orgId, after, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<Map<String, Object>> items = rows.subList(0, Math.min(limit, rows.size()));
        String next = hasMore ? String.valueOf(items.get(items.size() - 1).get("id")) : null;
        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("items", items);
        response.put("nextCursor", next);
        return response;
    }

    private Map<String, Object> one(String sql, Object... args) { return jdbc.queryForMap(sql, args); }
    private Map<String, Object> findOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다.");
        return rows.get(0);
    }
    private void requireExists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다.");
    }
    private String requiredText(Map<String, Object> body, String key, int max) {
        Object value = body.get(key);
        if (!(value instanceof String text) || text.isBlank() || text.trim().length() > max) badRequest(key + " 값이 없거나 길이가 올바르지 않습니다.");
        return ((String) value).trim();
    }
    private String optionalText(Map<String, Object> body, String key, int max) {
        Object value = body.get(key);
        if (value == null) return null;
        if (!(value instanceof String text) || text.trim().length() > max) badRequest(key + " 길이가 올바르지 않습니다.");
        return ((String) value).trim();
    }
    private long requiredId(Map<String, Object> body, String key) { return parsePositiveLong(body.get(key), key); }
    private long parsePositiveLong(Object value, String key) {
        try {
            long n = Long.parseLong(String.valueOf(value));
            if (n > 0) return n;
        } catch (RuntimeException ignored) { }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + "은 양의 정수여야 합니다.");
    }
    private double requiredNumber(Map<String, Object> body, String key, double min, double max) {
        Object value = body.get(key);
        if (!(value instanceof Number number)) badRequest(key + "은 숫자여야 합니다.");
        double result = ((Number) value).doubleValue();
        if (result < min || result > max) badRequest(key + " 범위가 올바르지 않습니다.");
        return result;
    }
    private void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
