package com.tayotayo.backend.operations;

import com.tayotayo.backend.auth.AccessTokenStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1")
public class OperationsController {
    private final JdbcTemplate jdbc;private final AccessTokenStore tokens;
    public OperationsController(JdbcTemplate jdbc,AccessTokenStore tokens){this.jdbc=jdbc;this.tokens=tokens;}

    @GetMapping("/orgs/{orgId}/handover-records")
    public Map<String,Object> handovers(@PathVariable("orgId")long org,@RequestHeader(name="Authorization",required=false)String auth,@RequestParam(name="limit",defaultValue="20")int limit,@RequestParam(name="cursor",required=false)String cursor){long user=tokens.requireUser(auth);staff(org,user);if(limit<1||limit>100)bad("limit은 1~100이어야 합니다.");long after=parse(cursor);List<Map<String,Object>>rows=jdbc.queryForList("SELECT h.id,h.trip_child_id AS tripChildId,tc.child_name AS childName,t.service_date AS serviceDate,t.direction,h.received_by_staff_id AS receivedByStaffId,h.received_at AS receivedAt,h.guardian_contacted_at AS guardianContactedAt,h.resolution,h.resolved_at AS resolvedAt,h.note FROM org_handover_records h JOIN trip_children tc ON tc.id=h.trip_child_id JOIN trips t ON t.id=tc.trip_id WHERE t.org_id=? AND h.id>? ORDER BY h.id DESC LIMIT ?",org,after,limit+1);return page(rows,limit);}

    @org.springframework.web.bind.annotation.PatchMapping("/orgs/{orgId}/handover-records/{recordId}")
    public Map<String,Object> updateHandover(@PathVariable("orgId")long org,@PathVariable("recordId")long id,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object>b){long user=tokens.requireUser(auth);staff(org,user);exists("SELECT COUNT(*) FROM org_handover_records h JOIN trip_children tc ON tc.id=h.trip_child_id JOIN trips t ON t.id=tc.trip_id WHERE t.org_id=? AND h.id=?",org,id);if(Boolean.TRUE.equals(b.get("contacted")))jdbc.update("UPDATE org_handover_records SET guardian_contacted_at=COALESCE(guardian_contacted_at,CURRENT_TIMESTAMP(3)) WHERE id=?",id);if(b.containsKey("resolution")){String r=String.valueOf(b.get("resolution")).toUpperCase();if(!List.of("PICKED_UP_AT_ORG","REDELIVERED","OTHER").contains(r))bad("resolution 값이 올바르지 않습니다.");jdbc.update("UPDATE org_handover_records SET resolution=?,resolved_at=CURRENT_TIMESTAMP(3) WHERE id=?",r,id);}if(b.containsKey("note")){Object n=b.get("note");if(n!=null&&(!(n instanceof String)||((String)n).length()>500))bad("note는 500자 이하여야 합니다.");jdbc.update("UPDATE org_handover_records SET note=?,updated_at=CURRENT_TIMESTAMP(3) WHERE id=?",n,id);}return jdbc.queryForMap("SELECT id,trip_child_id AS tripChildId,received_by_staff_id AS receivedByStaffId,received_at AS receivedAt,guardian_contacted_at AS guardianContactedAt,resolution,resolved_at AS resolvedAt,note FROM org_handover_records WHERE id=?",id);}

    @PostMapping("/trips/{tripId}/route-revisions") @Transactional
    public Map<String,Object> revise(@PathVariable("tripId")long trip,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object>b){long user=tokens.requireUser(auth);List<Map<String,Object>>tripRows=jdbc.queryForList("SELECT org_id FROM trips WHERE id=?",trip);if(tripRows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"운행을 찾을 수 없습니다.");long org=((Number)tripRows.get(0).get("org_id")).longValue(),staff=staff(org,user);List<Map<String,Object>>stops=jdbc.queryForList("SELECT id FROM trip_stops WHERE trip_id=? ORDER BY seq",trip);if(stops.isEmpty())bad("정류장이 없는 운행은 노선을 변경할 수 없습니다.");Object raw=b.get("stopIds");List<Long>order=new ArrayList<>();if(raw instanceof List<?>list){for(Object x:list)order.add(id(x));}else for(Map<String,Object>s:stops)order.add(((Number)s.get("id")).longValue());List<Long>actual=stops.stream().map(s->((Number)s.get("id")).longValue()).sorted().toList();if(order.size()!=stops.size()||order.stream().distinct().count()!=stops.size()||!order.stream().sorted().toList().equals(actual))bad("stopIds는 운행 정류장을 빠짐없이 한 번씩 포함해야 합니다.");String reason=b.get("changeReason")==null?"MANUAL_REORDER":String.valueOf(b.get("changeReason")).toUpperCase();if(!List.of("INITIAL","MANUAL_REORDER","STOP_SKIPPED","STOP_ADDED").contains(reason))bad("changeReason 값이 올바르지 않습니다.");Integer rev=jdbc.queryForObject("SELECT route_revision_no FROM trips WHERE id=?",Integer.class,trip);int next=(rev==null?0:rev)+1;String json="["+String.join(",",order.stream().map(String::valueOf).toList())+"]";jdbc.update("INSERT INTO trip_route_revisions(trip_id,revision_no,stop_order,change_reason,changed_by_staff_id) VALUES(?,?,CAST(? AS JSON),?,?)",trip,next,json,reason,staff);for(int i=0;i<order.size();i++)jdbc.update("UPDATE trip_stops SET seq=?,updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND trip_id=?",i+1,order.get(i),trip);jdbc.update("UPDATE trips SET route_revision_no=? WHERE id=?",next,trip);return Map.of("tripId",trip,"revisionNo",next,"stopIds",order,"changeReason",reason);}

    private long staff(long org,long user){List<Map<String,Object>>r=jdbc.queryForList("SELECT id FROM org_staff WHERE org_id=? AND user_id=? AND status='ACTIVE' AND deleted_at IS NULL",org,user);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"기관 직원이 아닙니다.");return ((Number)r.get(0).get("id")).longValue();}
    private void exists(String q,Object...a){Integer n=jdbc.queryForObject(q,Integer.class,a);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"대상을 찾을 수 없습니다.");}
    private Map<String,Object>page(List<Map<String,Object>>r,int size){boolean more=r.size()>size;List<Map<String,Object>>items=r.subList(0,Math.min(size,r.size()));Map<String,Object>o=new LinkedHashMap<>();o.put("items",items);o.put("nextCursor",more?String.valueOf(items.get(items.size()-1).get("id")):null);return o;}
    private static long id(Object x){try{long n=Long.parseLong(String.valueOf(x));if(n>0)return n;}catch(Exception ignored){}bad("stopIds에는 양의 정수 ID가 필요합니다.");return 0;}
    private static long parse(String x){if(x==null||x.isBlank())return 0;return id(x);}
    private static void bad(String s){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
}
