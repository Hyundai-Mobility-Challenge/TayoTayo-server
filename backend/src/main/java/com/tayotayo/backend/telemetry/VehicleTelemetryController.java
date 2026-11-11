package com.tayotayo.backend.telemetry;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/vehicle/trips/{tripId}")
public class VehicleTelemetryController {
    private final JdbcTemplate jdbc;
    public VehicleTelemetryController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @PostMapping("/locations") @Transactional
    public Map<String,Object> locations(@PathVariable("tripId")long trip,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential,@RequestBody Map<String,Object>b){long device=deviceTrip(trip,credential);Object raw=b.get("samples");if(!(raw instanceof List<?>))bad("samples 배열이 필요합니다.");List<?> samples=(List<?>)raw;if(samples.isEmpty()||samples.size()>500)bad("samples는 1~500개여야 합니다.");int saved=0;
        for(Object item:samples){if(!(item instanceof Map<?,?>))bad("표본 항목은 객체여야 합니다.");Map<?,?>s=(Map<?,?>)item;Timestamp at=timestamp(s.get("recordedAt"));double lat=number(s.get("latitude"),-90,90,"latitude"),lng=number(s.get("longitude"),-180,180,"longitude");Object speed=s.get("speedKph"),heading=s.get("headingDeg"),accuracy=s.get("accuracyM");String gear=s.get("gear")==null?null:String.valueOf(s.get("gear")),source=s.get("source")==null?"PLEOS":String.valueOf(s.get("source"));
            jdbc.update("INSERT INTO trip_location_samples(trip_id,recorded_at,latitude,longitude,speed_kph,heading_deg,accuracy_m,gear,source) VALUES(?,?,?,?,?,?,?,?,?)",trip,at,lat,lng,speed,heading,accuracy,gear,source);jdbc.update("UPDATE trips SET last_latitude=?,last_longitude=?,last_speed_kph=?,last_gear=?,last_location_at=?,updated_at=CURRENT_TIMESTAMP(3) WHERE id=?",lat,lng,speed,gear,at,trip);saved++;}
        return Map.of("acceptedCount",saved,"vehicleDeviceId",device);
    }

    @PostMapping("/eta-snapshots") @Transactional
    public Map<String,Object> eta(@PathVariable("tripId")long trip,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential,@RequestBody Map<String,Object>b){long device=deviceTrip(trip,credential);int revision=intValue(b.get("routeRevisionNo"),"routeRevisionNo");Timestamp source=timestamp(b.get("sourceTime"));String validation=text(b.get("validationStatus"),"validationStatus");List<?> entries=b.get("entries") instanceof List<?> list?list:List.of();
        jdbc.update("INSERT INTO trip_eta_snapshots(trip_id,route_revision_no,nav_route_key,source_time,validation_status,validation_note,raw_payload) VALUES(?,?,?,?,?,?,JSON_OBJECT())",trip,revision,b.get("navRouteKey"),source,validation,b.get("validationNote"));Long snapshot=jdbc.queryForObject("SELECT id FROM trip_eta_snapshots WHERE trip_id=? ORDER BY id DESC LIMIT 1",Long.class,trip);
        for(Object item:entries){if(!(item instanceof Map<?,?>))bad("entries 항목은 객체여야 합니다.");Map<?,?>e=(Map<?,?>)item;int idx=intValue(e.get("bundleIndex"),"bundleIndex"),sec=intValue(e.get("remainingSec"),"remainingSec");String type=text(e.get("pointType"),"pointType");Object stop=e.get("tripStopId")==null?null:Long.valueOf(String.valueOf(e.get("tripStopId")));jdbc.update("INSERT INTO trip_eta_entries(trip_id,snapshot_id,bundle_index,point_type,trip_stop_id,latitude,longitude,remaining_sec,remaining_m) VALUES(?,?,?,?,?,?,?,?,?)",trip,snapshot,idx,type,stop,e.get("latitude"),e.get("longitude"),sec,e.get("remainingM"));if(stop!=null)jdbc.update("UPDATE trip_stops SET eta_remaining_sec=?,eta_remaining_m=?,eta_snapshot_id=?,eta_received_at=CURRENT_TIMESTAMP(3) WHERE id=? AND trip_id=?",sec,e.get("remainingM"),snapshot,stop,trip);}
        return Map.of("snapshotId",snapshot,"acceptedEntries",entries.size(),"vehicleDeviceId",device);
    }
    private long deviceTrip(long trip,String credential){if(credential==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"차량 credential이 필요합니다.");List<Map<String,Object>>d=jdbc.queryForList("SELECT id FROM vehicle_devices WHERE credential_hash=SHA2(?,256) AND revoked_at IS NULL",credential);if(d.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"차량 credential이 유효하지 않습니다.");long id=((Number)d.get(0).get("id")).longValue();Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM trips WHERE id=? AND vehicle_device_id=?",Integer.class,trip,id);if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"운행을 찾을 수 없습니다.");return id;}
    private static Timestamp timestamp(Object o){try{return Timestamp.from(OffsetDateTime.parse(String.valueOf(o)).toInstant());}catch(Exception e){bad("날짜와 시간은 ISO date-time이어야 합니다.");return null;}}
    private static double number(Object o,double min,double max,String key){if(!(o instanceof Number n)||n.doubleValue()<min||n.doubleValue()>max)bad(key+" 값이 범위를 벗어났습니다.");return ((Number)o).doubleValue();}
    private static int intValue(Object o,String key){if(!(o instanceof Number n)||n.intValue()<0)bad(key+"은 0 이상 정수여야 합니다.");return ((Number)o).intValue();}
    private static String text(Object o,String key){if(!(o instanceof String s)||s.isBlank())bad(key+" 값은 필수입니다.");return ((String)o).trim();}
    private static void bad(String s){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
}
