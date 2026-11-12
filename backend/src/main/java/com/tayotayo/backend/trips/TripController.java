package com.tayotayo.backend.trips;

import com.tayotayo.backend.auth.AccessTokenStore;
import com.tayotayo.backend.notifications.PushNotificationService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1")
public class TripController {
    private final JdbcTemplate jdbc; private final AccessTokenStore tokens; private final PushNotificationService notifications;
    public TripController(JdbcTemplate jdbc,AccessTokenStore tokens,PushNotificationService notifications){this.jdbc=jdbc;this.tokens=tokens;this.notifications=notifications;}

    @PostMapping("/orgs/{orgId}/trips") @Transactional
    public Map<String,Object> create(@PathVariable("orgId")long org,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object>b){
        long user=tokens.requireUser(auth);String clientId=text(b.get("clientTripId"),"clientTripId");long vehicle=id(b.get("vehicleId"),"vehicleId");LocalDate date=date(b.get("serviceDate"));String dir=direction(text(b.get("direction"),"direction"));Long route=b.get("routeId")==null?null:id(b.get("routeId"),"routeId");
        exists("SELECT COUNT(*) FROM vehicles WHERE id=? AND org_id=? AND status='ACTIVE' AND deleted_at IS NULL",vehicle,org);
        List<Map<String,Object>> devices=jdbc.queryForList("SELECT id FROM vehicle_devices WHERE vehicle_id=? AND revoked_at IS NULL ORDER BY paired_at DESC LIMIT 1",vehicle);if(devices.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"차량을 페어링한 기기가 필요합니다.");long device=((Number)devices.get(0).get("id")).longValue();
        if(route!=null)exists("SELECT COUNT(*) FROM routes WHERE id=? AND org_id=? AND vehicle_id=? AND direction=? AND deleted_at IS NULL",route,org,vehicle,dir);
        Long driver=b.get("driverStaffId")==null?null:id(b.get("driverStaffId"),"driverStaffId"),attendant=b.get("attendantStaffId")==null?null:id(b.get("attendantStaffId"),"attendantStaffId");
        KeyHolder key=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO trips(client_trip_id,org_id,vehicle_id,route_id,vehicle_device_id,direction,service_date,status,driver_staff_id,attendant_staff_id) VALUES(?,?,?,?,?,?,?,'PREPARING',?,?)",Statement.RETURN_GENERATED_KEYS);p.setString(1,clientId);p.setLong(2,org);p.setLong(3,vehicle);if(route==null)p.setNull(4,java.sql.Types.BIGINT);else p.setLong(4,route);p.setLong(5,device);p.setString(6,dir);p.setDate(7,java.sql.Date.valueOf(date));if(driver==null)p.setNull(8,java.sql.Types.BIGINT);else p.setLong(8,driver);if(attendant==null)p.setNull(9,java.sql.Types.BIGINT);else p.setLong(9,attendant);return p;},key);
        long trip=key.getKey().longValue();
        if(route!=null){jdbc.update("INSERT INTO trip_stops(trip_id,stop_id,seq,stop_name,latitude,longitude) SELECT ?,s.id,rs.default_seq,s.name,s.latitude,s.longitude FROM route_stops rs JOIN stops s ON s.id=rs.stop_id WHERE rs.org_id=? AND rs.route_id=? ORDER BY rs.default_seq",trip,org,route);
            jdbc.update("INSERT INTO trip_children(org_id,trip_id,vehicle_id,child_id,trip_stop_id,child_name,boarding_expected,status) SELECT ?,?, ?,c.id,ts.id,c.name,1,'EXPECTED' FROM child_route_assignments a JOIN children c ON c.id=a.child_id JOIN trip_stops ts ON ts.trip_id=? AND ts.stop_id=a.stop_id WHERE a.org_id=? AND a.route_id=? AND a.vehicle_id=? AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>=?) AND c.status='ACTIVE' AND c.deleted_at IS NULL",org,trip,vehicle,trip,org,route,vehicle,java.sql.Date.valueOf(date),java.sql.Date.valueOf(date));
            jdbc.update("UPDATE trip_children tc JOIN attendance_requests ar ON ar.child_id=tc.child_id AND ar.service_date=? AND ar.direction=? AND ar.status='APPROVED' SET tc.boarding_expected=0,tc.status='NOT_BOARDED',tc.not_boarded_reason=ar.request_type,tc.attendance_request_id=ar.id WHERE tc.trip_id=? AND ar.request_type IN ('ABSENT','SELF_PICKUP','NOT_RIDING')",java.sql.Date.valueOf(date),dir,trip);
        }
        return getTrip(trip);
    }

    @GetMapping("/orgs/{orgId}/trips")
    public Map<String,Object> organizationTrips(@PathVariable("orgId")long org,@RequestHeader(name="Authorization",required=false)String auth,
            @RequestParam(name="serviceDate",required=false)String serviceDate,@RequestParam(name="direction",required=false)String direction) {
        tokens.requireUser(auth);
        LocalDate date=serviceDate==null||serviceDate.isBlank()?LocalDate.now():date(serviceDate);
        StringBuilder sql=new StringBuilder("SELECT t.id,t.org_id AS orgId,t.vehicle_id AS vehicleId,v.name AS vehicleName,t.route_id AS routeId,r.name AS routeName,t.direction,t.service_date AS serviceDate,t.status,t.started_at AS startedAt,t.ended_at AS endedAt,(SELECT COUNT(*) FROM trip_children tc WHERE tc.trip_id=t.id) AS childCount FROM trips t JOIN vehicles v ON v.id=t.vehicle_id LEFT JOIN routes r ON r.id=t.route_id WHERE t.org_id=? AND t.service_date=?");
        if(direction!=null&&!direction.isBlank())sql.append(" AND t.direction=?");
        sql.append(" ORDER BY t.id DESC");
        List<Map<String,Object>> items=direction==null||direction.isBlank()?jdbc.queryForList(sql.toString(),org,java.sql.Date.valueOf(date)):jdbc.queryForList(sql.toString(),org,java.sql.Date.valueOf(date),direction(direction));
        Map<String,Object> response=new LinkedHashMap<>();response.put("items",items);response.put("nextCursor",null);return response;
    }

    @GetMapping("/trips/{tripId}") public Map<String,Object> get(@PathVariable("tripId")long id,@RequestHeader(name="Authorization",required=false)String auth){tokens.requireUser(auth);return getTrip(id);}

    @GetMapping("/vehicle/trips") public Map<String,Object> vehicleTrips(@RequestHeader(name="X-Vehicle-Credential",required=false)String credential,@RequestParam(name="limit",defaultValue="20")int limit,@RequestParam(name="cursor",required=false)String cursor,@RequestParam(name="direction",required=false)String direction){long device=device(credential);if(limit<1||limit>100)bad("limit은 1~100이어야 합니다.");long after=cursor(cursor);String sql="SELECT id,org_id AS orgId,vehicle_id AS vehicleId,direction,service_date AS serviceDate,status FROM trips WHERE vehicle_device_id=? AND service_date=CURRENT_DATE AND id>?";if(direction!=null)sql+=" AND direction=?";sql+=" ORDER BY id DESC LIMIT ?";List<Map<String,Object>> rows=direction==null?jdbc.queryForList(sql,device,after,limit+1):jdbc.queryForList(sql,device,after,direction,limit+1);return page(rows,limit);}

    @PostMapping("/trips/{tripId}/prepare") public Map<String,Object> prepare(@PathVariable("tripId")long id,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential){requireDeviceTrip(id,credential);return getTrip(id);}

    @PutMapping("/trips/{tripId}/checklist") @Transactional
    public Map<String,Object> checklist(@PathVariable("tripId")long id,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential,@RequestBody Map<String,Object>b){requireDeviceTrip(id,credential);boolean confirmed=Boolean.TRUE.equals(b.get("driverConfirmed"));if(confirmed)jdbc.update("UPDATE trips SET driver_checked_at=CURRENT_TIMESTAMP(3) WHERE id=?",id);jdbc.update("INSERT INTO trip_checklist_items(trip_id,phase,item_code,result,note) VALUES(?,'PRE_DEPARTURE','DRIVER_CONFIRMATION',?,?)",id,confirmed?"OK":"NG",confirmed?"기사 구두 확인을 교사가 기록":"기사 확인 없이 출발 가능");return getTrip(id);}

    @PostMapping("/trips/{tripId}/start") public Map<String,Object> start(@PathVariable("tripId")long id,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential){requireDeviceTrip(id,credential);int n=jdbc.update("UPDATE trips SET status='IN_PROGRESS',started_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND status='PREPARING'",id);if(n==0)conflict();return getTrip(id);}

    @PostMapping("/trips/{tripId}/stops/{tripStopId}/arrive") public Map<String,Object> arrive(@PathVariable("tripId")long id,@PathVariable("tripStopId")long stop,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential){requireDeviceTrip(id,credential);int n=jdbc.update("UPDATE trip_stops SET status='ARRIVED',arrived_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND trip_id=? AND status='PENDING'",stop,id);if(n==0)conflict();notifications.notifyStopArrived(id,stop);return getTrip(id);}

    @PostMapping("/trips/{tripId}/stops/{tripStopId}/complete") public Map<String,Object> stopComplete(@PathVariable("tripId")long id,@PathVariable("tripStopId")long stop,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential){requireDeviceTrip(id,credential);int n=jdbc.update("UPDATE trip_stops SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND trip_id=? AND status='ARRIVED'",stop,id);if(n==0)conflict();return getTrip(id);}

    @PostMapping("/trips/{tripId}/stops/{tripStopId}/skip") public Map<String,Object> skip(@PathVariable("tripId")long id,@PathVariable("tripStopId")long stop,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential){requireDeviceTrip(id,credential);int n=jdbc.update("UPDATE trip_stops SET status='SKIPPED',completed_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND trip_id=? AND status IN ('PENDING','ARRIVED')",stop,id);if(n==0)conflict();jdbc.update("UPDATE trips SET route_revision_no=route_revision_no+1 WHERE id=?",id);return getTrip(id);}

    @PostMapping("/trips/{tripId}/complete") public Map<String,Object> complete(@PathVariable("tripId")long id,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential){requireDeviceTrip(id,credential);Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM trip_stops WHERE trip_id=? AND status NOT IN ('COMPLETED','SKIPPED')",Integer.class,id);if(pending!=null&&pending>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"완료되지 않은 정류장이 있습니다.");int n=jdbc.update("UPDATE trips SET status='COMPLETED',ended_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND status IN ('IN_PROGRESS','RETURNING')",id);if(n==0)conflict();return getTrip(id);}

    @PostMapping("/trips/{tripId}/return") public Map<String,Object> returnToOrg(@PathVariable("tripId")long id,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential){requireDeviceTrip(id,credential);int n=jdbc.update("UPDATE trips SET status='RETURNING',returning_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND status='IN_PROGRESS'",id);if(n==0)conflict();return getTrip(id);}

    @PostMapping("/trips/{tripId}/interior-check") public Map<String,Object> interiorCheck(@PathVariable("tripId")long id,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential,@RequestBody Map<String,Object> body){requireDeviceTrip(id,credential);if(!Boolean.TRUE.equals(body.get("checked")))bad("차량 내부 확인 완료 여부 checked=true가 필요합니다.");jdbc.update("UPDATE trips SET interior_checked_at=CURRENT_TIMESTAMP(3) WHERE id=?",id);return getTrip(id);}

    @PostMapping("/trips/{tripId}/cancel") public Map<String,Object> cancel(@PathVariable("tripId")long id,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object> body){tokens.requireUser(auth);String reason=body.get("reason") instanceof String s?s:null;int n=jdbc.update("UPDATE trips SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP(3),cancel_reason=?,updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND status='PREPARING'",reason,id);if(n==0)conflict();return getTrip(id);}

    @PostMapping("/trips/{tripId}/events") @Transactional
    public Map<String,Object> event(@PathVariable("tripId")long id,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential,@RequestBody Map<String,Object> body){long device=requireDeviceTripId(id,credential);String event=text(body.get("eventType"),"eventType"),client=text(body.get("clientEventId"),"clientEventId");String occurred=text(body.get("occurredAt"),"occurredAt");java.sql.Timestamp at;try{at=java.sql.Timestamp.from(java.time.OffsetDateTime.parse(occurred).toInstant());}catch(Exception e){bad("occurredAt은 ISO date-time 형식이어야 합니다.");return Map.of();}
        List<Map<String,Object>> duplicate=jdbc.queryForList("SELECT id FROM trip_events WHERE trip_id=? AND client_event_id=?",id,client);if(!duplicate.isEmpty())return Map.of("id",duplicate.get(0).get("id"),"duplicate",true);
        Object rawChild=body.get("tripChildId"),rawStop=body.get("tripStopId");Long child=rawChild==null?null:id(rawChild,"tripChildId"),stop=rawStop==null?null:id(rawStop,"tripStopId");
        if(child!=null)exists("SELECT COUNT(*) FROM trip_children WHERE trip_id=? AND id=?",id,child);if(stop!=null)exists("SELECT COUNT(*) FROM trip_stops WHERE trip_id=? AND id=?",id,stop);
        jdbc.update("INSERT INTO trip_events(client_event_id,trip_id,trip_child_id,trip_stop_id,event_type,occurred_at,recorded_by_device_id,latitude,longitude,payload) VALUES(?,?,?,?,?,?,?,?,?,JSON_OBJECT())",client,id,child,stop,event,at,device,body.get("latitude"),body.get("longitude"));
        Long eventId=jdbc.queryForObject("SELECT id FROM trip_events WHERE trip_id=? AND client_event_id=?",Long.class,id,client);
        updateChildFromEvent(id,child,event,body);
        if(child!=null&&(event.equals("CHILD_DROPPED_OFF")||event.equals("CHILD_RECEIVED_BY_ORG")))notifications.notifyChildDroppedOff(id,child,eventId);
        return Map.of("accepted",true,"clientEventId",client,"eventType",event);
    }

    @PostMapping("/trips/{tripId}/events/batch") @Transactional
    public Map<String,Object> eventBatch(@PathVariable("tripId")long id,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential,@RequestBody Map<String,Object> body){Object raw=body.get("events");if(!(raw instanceof List<?> list))bad("events 배열이 필요합니다.");List<?> events=(List<?>)raw;int saved=0;for(Object item:events){if(!(item instanceof Map<?,?>))bad("events 항목은 객체여야 합니다.");@SuppressWarnings("unchecked") Map<String,Object> e=(Map<String,Object>)item;event(id,credential,e);saved++;}return Map.of("acceptedCount",saved);}

    private void updateChildFromEvent(long trip,Long child,String type,Map<String,Object> body){if(child==null)return;String status=null,col=null;
        switch(type){case "CHILD_BOARDED"->status="BOARDED";case "CHILD_NOT_BOARDED","TEACHER_CONFIRMED_NOT_BOARDED"->status="NOT_BOARDED";case "CHILD_HANDED_OVER"->status="HANDED_OVER";case "CHILD_DROPPED_OFF","CHILD_RECEIVED_BY_ORG"->status="DROPPED_OFF";case "GUARDIAN_NO_SHOW"->status="GUARDIAN_NO_SHOW";case "CHILD_RETURNED_TO_ORG"->status="RETURNED_TO_ORG";default->{return;}}
        String sql="UPDATE trip_children SET status=?,updated_at=CURRENT_TIMESTAMP(3)";
        switch(status){case "BOARDED"->sql+=",boarded_at=CURRENT_TIMESTAMP(3)";case "HANDED_OVER"->sql+=",handed_over_at=CURRENT_TIMESTAMP(3)";case "DROPPED_OFF"->sql+=",dropped_off_at=CURRENT_TIMESTAMP(3)";case "GUARDIAN_NO_SHOW"->sql+=",no_show_at=CURRENT_TIMESTAMP(3)";case "RETURNED_TO_ORG"->sql+=",returned_to_org_at=CURRENT_TIMESTAMP(3)";default->{}}
        if(type.equals("CHILD_NOT_BOARDED")){sql+=",boarding_expected=0,not_boarded_reason=?";jdbc.update(sql+" WHERE trip_id=? AND id=?",status,body.getOrDefault("reason","OTHER"),trip,child);}else jdbc.update(sql+" WHERE trip_id=? AND id=?",status,trip,child);
    }
    private long requireDeviceTripId(long trip,String credential){long d=device(credential);exists("SELECT COUNT(*) FROM trips WHERE id=? AND vehicle_device_id=?",trip,d);return d;}

    private Map<String,Object> getTrip(long id){List<Map<String,Object>>r=jdbc.queryForList("SELECT id,client_trip_id AS clientTripId,org_id AS orgId,vehicle_id AS vehicleId,route_id AS routeId,direction,service_date AS serviceDate,status,driver_checked_at AS driverCheckedAt,started_at AS startedAt,ended_at AS endedAt FROM trips WHERE id=?",id);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"운행을 찾을 수 없습니다.");Map<String,Object> out=new LinkedHashMap<>(r.get(0));out.put("stops",jdbc.queryForList("SELECT id,stop_id AS stopId,seq,stop_name AS stopName,latitude,longitude,status,arrived_at AS arrivedAt,completed_at AS completedAt FROM trip_stops WHERE trip_id=? ORDER BY seq",id));out.put("children",jdbc.queryForList("SELECT id AS tripChildId,child_id AS childId,child_name AS childName,boarding_expected AS boardingExpected,status,trip_stop_id AS tripStopId FROM trip_children WHERE trip_id=? ORDER BY id",id));return out;}
    private long device(String credential){if(credential==null||credential.isBlank())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"차량 credential이 필요합니다.");List<Map<String,Object>>r=jdbc.queryForList("SELECT id FROM vehicle_devices WHERE credential_hash=SHA2(?,256) AND revoked_at IS NULL",credential);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"차량 credential이 유효하지 않습니다.");return ((Number)r.get(0).get("id")).longValue();}
    private void requireDeviceTrip(long trip,String credential){long device=device(credential);exists("SELECT COUNT(*) FROM trips WHERE id=? AND vehicle_device_id=?",trip,device);}
    private void exists(String q,Object...a){Integer n=jdbc.queryForObject(q,Integer.class,a);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"대상을 찾을 수 없습니다.");}
    private Map<String,Object> page(List<Map<String,Object>>r,int size){boolean more=r.size()>size;List<Map<String,Object>>items=r.subList(0,Math.min(size,r.size()));Map<String,Object> out=new LinkedHashMap<>();out.put("items",items);out.put("nextCursor",more?String.valueOf(items.get(items.size()-1).get("id")):null);return out;}
    private static long id(Object x,String k){try{long n=Long.parseLong(String.valueOf(x));if(n>0)return n;}catch(Exception ignored){}bad(k+" 값이 올바르지 않습니다.");return 0;}
    private static long cursor(String x){return x==null||x.isBlank()?0:id(x,"cursor");}
    private static String text(Object x,String k){if(!(x instanceof String s)||s.isBlank())bad(k+" 값은 필수입니다.");return ((String)x).trim();}
    private static LocalDate date(Object x){try{return LocalDate.parse(String.valueOf(x));}catch(Exception e){bad("serviceDate는 YYYY-MM-DD 형식이어야 합니다.");return null;}}
    private static String direction(String x){String d=x.toUpperCase();if(!List.of("DROP_OFF","PICK_UP").contains(d))bad("direction 값이 올바르지 않습니다.");return d;}
    private static void conflict(){throw new ResponseStatusException(HttpStatus.CONFLICT,"운행 상태가 현재 요청과 맞지 않습니다.");}
    private static void bad(String x){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,x);}
}
