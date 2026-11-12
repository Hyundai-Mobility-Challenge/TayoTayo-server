# ERDCloud 변경안 — 회의 결정 반영

이 문서는 기존 ERD 기준과 회의 메모를 대조해 ERDCloud에서 반영할 변경을 정리합니다. 실행할 최신 전체 스키마는 `TayoTayo_회의결정_반영_ERDCloud.sql`입니다.

## 방향 규칙

`routes.direction`, `trips.direction`, `attendance_requests.direction`은 이미 존재하므로 유지합니다.

- `DROP_OFF`: 등원(집/정류장에서 기관으로 이동)
- `PICK_UP`: 하원(기관에서 정류장/집으로 이동)

등원과 하원은 각각 다른 운행(`trips`)으로 생성합니다. 아이 배정은 방향이 다른 route에 각각 연결할 수 있게 하고, 정류장은 route 방향별로 관리합니다. 이에 따라 등원 승차 정류장과 하원 하차 지점도 서로 다르게 저장할 수 있습니다. `direction`은 보호자 상태 조회와 차량 운행 조회, 알림 묶음 키에도 사용합니다.

## 기존 테이블 수정

### `trip_children` — 운행별 탑승 계획과 실제 상태

이 테이블을 해당 운행의 아이별 상태와 출결 현황으로 사용합니다.

- `seat_id` 삭제
- `last_belt_status`, `last_belt_status_at` 삭제
- 기본 상태 `EXPECTED` 유지: 운행 명단에 포함된 기본 탑승 예정
- `boarding_expected BOOLEAN NOT NULL DEFAULT TRUE` 추가 권장: 보호자가 탑승하지 않겠다고 신청한 계획을 운행 명단에 반영. 실제 미탑승 확정 상태와 구분
- 기존 `status`는 교사/차량 담당자가 확인한 실제 진행 상태로 사용: `EXPECTED`, `BOARDED`, `NOT_BOARDED`, `HANDED_OVER`, `DROPPED_OFF`, `GUARDIAN_NO_SHOW`, `RETURNED_TO_ORG`
- `not_boarded_reason`에 `GUARDIAN_NOT_RIDING` 또는 `REQUESTED_NOT_RIDING` 추가 권장. 보호자의 의사 표시와 교사의 미탑승 확정은 별도 이벤트로 남김

방향별 의미가 다르므로 이벤트와 상태 API 응답에는 항상 `direction`을 함께 제공합니다. 예를 들어 등원의 `HANDED_OVER`는 보호자에게서 아이를 인계받았다는 뜻이고, 하원의 `HANDED_OVER`는 보호자에게 인계했다는 뜻입니다. 이벤트 payload의 인계 대상(`GUARDIAN`, `AUTHORIZED_PERSON`, `ORG_STAFF`)을 기록합니다.

### `trips` — 운행 확인 시각

- `driver_checked_at TIMESTAMPTZ NULL` 추가
- 교사가 기사에게 음성 요약을 들려주고 기사가 말로 확인한 뒤 교사가 체크하면 서버 시각을 기록
- 확인 없이도 출발은 허용. 미확인 출발은 이벤트/응답에서 확인 누락으로 표시
- 이미 있는 `direction`을 필수로 사용

### `attendance_requests` — 보호자 변경 요청

- `request_type`에 `NOT_RIDING` 추가 권장. `ABSENT`(기관 출결 결석)와 `SELF_PICKUP`(셔틀 대신 보호자가 직접 이동/픽업)과 셔틀만 타지 않는 선택을 구분
- `direction`은 필수로 요청에 넣고, 기본값에 의존하지 않음
- 등원 요청은 다음 날짜도 접수 가능해야 함. 하원 변경 요청의 제출 가능 날짜 범위는 회의에서 확정한 정책을 API 서버 검증에도 동일하게 적용
- 변경 요청은 기록하고, 운행 명단을 만들거나 변경할 때 해당 방향의 탑승 계획(`boarding_expected`)에 반영
- `PENDING/APPROVED/REJECTED` 승인 정책은 회의 안건 OQ-02의 결정란이 비어 있어 확정 전까지 현행 컬럼을 유지

### `notifications`

알림 중복 방지 키에 방향을 포함합니다. 출결 관련 알림의 예: `attendance:{child_id}:{service_date}:{direction}`. 아침 등원 알림과 오후 하원 알림이 서로 덮어쓰지 않아야 합니다.

### `child_route_assignments` / `routes`

`routes.direction`을 기준으로 아이의 등원 노선 배정과 하원 노선 배정을 따로 둡니다. 동일한 아이가 양방향 노선에 각각 배정될 수 있게 유일성 제약을 점검합니다. 현재 `child_route_assignments`에 정류장과 노선이 연결되어 있으므로 방향별 승차/하차 정류장을 분리해 표현할 수 있습니다.

## 픽업 담당자 분리

회의 결론대로 픽업/인계 담당자 정보는 출결 상태 컬럼과 별도로 관리합니다. 현재 `authorized_pickup_persons`는 승인된 사람의 목록이고, `trip_children.handover_*`는 실제 인계 결과 스냅샷입니다. 둘은 역할이 다릅니다.

보호자가 날짜별 담당자를 미리 지정할 수 있어야 한다면 다음 테이블을 추가합니다.

### `daily_pickups` (추가 권장)

| 컬럼 | 정의 |
|---|---|
| `id` | PK |
| `org_id`, `child_id` | 기관 및 아이 FK |
| `service_date`, `direction` | 날짜 및 `DROP_OFF`/`PICK_UP` |
| `pickup_person_id` | 승인된 대리 픽업 인원 FK, nullable |
| `pickup_guardian_user_id` | 보호자 계정 FK, nullable |
| `person_name_snapshot`, `relationship_snapshot` | 운행 시점 이름/관계 스냅샷 |
| `updated_by_user_id`, `created_at`, `updated_at` | 변경 주체 및 시간 |

`(child_id, service_date, direction)`에 UNIQUE 제약을 둡니다. 운행 스냅샷 생성 시 해당 담당자를 `trip_children.handover_*`로 복사하고, 실제 인계 이벤트도 `trip_events`에 기록합니다. 보호자가 날짜별 담당자를 지정하지 않는 정책이면 이 테이블은 생략하고, 요청 레코드와 운행 인계 스냅샷만 사용해도 됩니다.

## 보호자 호출 및 응답 (등원)

회의록의 N-17, P-19~21, A-12, S-18~20을 저장할 데이터가 현재 SQL에 명확히 없습니다. `trip_events`에 알림/응답 이벤트를 남기고, 응답 창과 최신 응답을 조회해야 하므로 다음 테이블을 추가하는 방안을 권합니다.

### `guardian_call_requests` (추가 권장)

`id`, `trip_id`, `trip_child_id`, `status`(OPEN/RESPONDED/CLOSED/EXPIRED), `opened_at`, `expires_at`, `response`(ON_THE_WAY/NOT_RIDING), `responded_at`, `closed_at`, `created_by_staff_id`, `created_at`.

- 한 아이의 특정 운행에 진행 중 호출은 최대 하나
- 호출이 유효한 동안 보호자 응답을 수정할 수 있고, 변경 이력은 `trip_events`에 남김
- 보호자의 `NOT_RIDING` 응답만으로 실제 미탑승 확정하지 않음. 교사의 미탑승 확인 이벤트가 별도로 필요
- `trip_events`의 event type에 보호자 호출/응답 및 교사 미탑승 확인 이벤트 추가

## 제거할 테이블 및 관계

- `vehicle_seats` 삭제
- `trip_seat_status_logs` 삭제
- `child_route_assignments.default_seat_id` 삭제
- `trip_children.seat_id` 및 안전벨트 컬럼 삭제
- 해당 테이블을 가리키는 FK/인덱스도 함께 제거

벨트 해제 경고음과 화면 경고는 차량 앱 내부 동작으로 처리하며, 서버 DB/API로 센서 상태나 경고 로그를 보내지 않습니다.

## ERD에 반영하기 전에 회의에서 확정할 항목

1. 보호자의 일반 출결/변경 요청을 즉시 반영할지, 기관 승인을 받을지(OQ-02 결정란 공란)
2. `daily_pickups`를 실제로 둘지: 날짜별 담당자를 사전에 등록하는 화면/기능이 필요한지
3. 미탑승 요청을 하원 변경 요청과 같은 테이블에 둘지, 등원/하원 공통 `attendance_requests`로 관리할지
4. 하원 변경 요청의 접수 날짜 범위와 운행 시작 30분 전 이후 수정 규칙
5. 호출 만료 시각과 기사 확인 체크 누락 기록 방식

