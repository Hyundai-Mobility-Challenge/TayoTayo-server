package com.tayotayo.backend.guardian;

import com.tayotayo.backend.auth.AccessTokenStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/guardian")
public class GuardianTripController {
    private final JdbcTemplate jdbc;private final AccessTokenStore tokens;
    public GuardianTripController(JdbcTemplate jdbc,AccessTokenStore tokens){this.jdbc=jdbc;this.tokens=tokens;}

    @GetMapping("/children/{childId}/today")
    public Map<String,Object> today(@PathVariable("childId")long child,@RequestHeader(name="Authorization",required=false)String auth,@RequestParam("direction")String direction){long user=tokens.requireUser(auth);guardian(child,user);String dir=direction(direction);List<Map<String,Object>> rows=jdbc.queryForList("SELECT t.id AS tripId,t.direction,t.service_date AS serviceDate,t.status AS tripStatus,tc.id AS tripChildId,tc.status AS childStatus,tc.boarding_expected AS boardingExpected,tc.boarded_at AS boardedAt,tc.handed_over_at AS handedOverAt,tc.dropped_off_at AS droppedOffAt,ts.stop_name AS stopName,ts.eta_remaining_sec AS etaRemainingSec,ts.eta_remaining_m AS etaRemainingM,t.last_latitude AS latitude,t.last_longitude AS longitude,t.last_location_at AS locationUpdatedAt FROM trip_children tc JOIN trips t ON t.id=tc.trip_id JOIN trip_stops ts ON ts.id=tc.trip_stop_id WHERE tc.child_id=? AND t.service_date=CURRENT_DATE AND t.direction=? ORDER BY t.id DESC LIMIT 1",child,dir);if(rows.isEmpty())return Map.of("childId",child,"direction",dir,"status","NO_TRIP_TODAY");return rows.get(0);}

    @GetMapping("/children/{childId}/trips")
    public Map<String,Object> history(@PathVariable("childId")long child,@RequestHeader(name="Authorization",required=false)String auth,@RequestParam(name="limit",defaultValue="20")int limit,@RequestParam(name="cursor",required=false)String cursor,@RequestParam(name="direction",required=false)String direction){long user=tokens.requireUser(auth);guardian(child,user);if(limit<1||limit>100)bad("limit은 1~100이어야 합니다.");long after=parse(cursor);String sql="SELECT t.id,t.direction,t.service_date AS serviceDate,t.status AS tripStatus,tc.status AS childStatus,tc.boarding_expected AS boardingExpected,tc.boarded_at AS boardedAt,tc.dropped_off_at AS droppedOffAt FROM trip_children tc JOIN trips t ON t.id=tc.trip_id WHERE tc.child_id=? AND t.id<?";List<Map<String,Object>> rows;if(direction==null)rows=jdbc.queryForList(sql+" ORDER BY t.id DESC LIMIT ?",child,after==0?Long.MAX_VALUE:after,limit+1);else rows=jdbc.queryForList(sql+" AND t.direction=? ORDER BY t.id DESC LIMIT ?",child,after==0?Long.MAX_VALUE:after,direction(direction),limit+1);boolean more=rows.size()>limit;List<Map<String,Object>>items=rows.subList(0,Math.min(limit,rows.size()));Map<String,Object>out=new LinkedHashMap<>();out.put("items",items);out.put("nextCursor",more?String.valueOf(items.get(items.size()-1).get("id")):null);return out;}

    @GetMapping("/trips/{tripId}/children/{childId}")
    public Map<String,Object> detail(@PathVariable("tripId")long trip,@PathVariable("childId")long child,@RequestHeader(name="Authorization",required=false)String auth){long user=tokens.requireUser(auth);guardian(child,user);List<Map<String,Object>> rows=jdbc.queryForList("SELECT t.id AS tripId,t.direction,t.service_date AS serviceDate,t.status AS tripStatus,tc.id AS tripChildId,tc.child_id AS childId,tc.child_name AS childName,tc.status AS childStatus,tc.boarding_expected AS boardingExpected,tc.not_boarded_reason AS notBoardedReason,tc.boarded_at AS boardedAt,tc.handed_over_at AS handedOverAt,tc.handover_to_type AS handoverToType,tc.handover_person_name AS handoverPersonName,tc.dropped_off_at AS droppedOffAt,tc.no_show_at AS noShowAt,ts.stop_name AS stopName,ts.eta_remaining_sec AS etaRemainingSec,ts.eta_remaining_m AS etaRemainingM,t.last_latitude AS latitude,t.last_longitude AS longitude,t.last_location_at AS locationUpdatedAt FROM trip_children tc JOIN trips t ON t.id=tc.trip_id JOIN trip_stops ts ON ts.id=tc.trip_stop_id WHERE t.id=? AND tc.child_id=?",trip,child);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"운행 정보를 찾을 수 없습니다.");return rows.get(0);}

    private void guardian(long child,long user){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM child_guardians WHERE child_id=? AND user_id=? AND status='ACTIVE'",Integer.class,child,user);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"아이의 연결 보호자만 조회할 수 있습니다.");}
    private static String direction(String d){String x=d.toUpperCase();if(!List.of("DROP_OFF","PICK_UP").contains(x))bad("direction 값이 올바르지 않습니다.");return x;}
    private static long parse(String c){if(c==null||c.isBlank())return 0;try{long n=Long.parseLong(c);if(n>0)return n;}catch(Exception ignored){}bad("cursor 값이 올바르지 않습니다.");return 0;}
    private static void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
