# 데이터베이스 안내

## 사용 DB와 준비 파일

- DBMS: MySQL 8.0.13 이상
- 기본 데이터베이스: `tayotayo_dev`
- 기본 주소: `jdbc:mysql://127.0.0.1:3306/tayotayo_dev?serverTimezone=Asia/Seoul&characterEncoding=UTF-8`
- 실행용 전체 스키마: [`TayoTayo_실행용_회의반영.sql`](../TayoTayo_실행용_회의반영.sql)
- ERDCloud 모델용 SQL: [`TayoTayo_회의결정_반영_ERDCloud.sql`](../TayoTayo_회의결정_반영_ERDCloud.sql)

서버 실행 스크립트는 DB에 접속하지만 테이블을 자동 생성·변경하지 않습니다. 테이블 생성은 실행용 SQL이 담당합니다. ERDCloud SQL은 모델 확인·가져오기를 위한 파일이며 실행용 스키마를 대신하지 않습니다.

## 초기화 SQL의 구성과 주의사항

실행용 SQL은 데이터베이스를 만들고, 전체 테이블 및 외래 키를 만든 뒤 햇살 유치원 데모 데이터를 입력합니다. 기본 실행은 다음을 포함합니다.

- 기관 관리자 계정: `orgs@example.com` / `orgspass123`
- 기관 ID 1, 차량 ID 1, 정류장 ID 1~3, 원아 ID 1~3
- 차량 기기 credential 및 오늘 날짜의 `PREPARING` 등원 운행

처음 사용하는 빈 개발 데이터베이스에서만 전체 SQL을 실행하세요. 이미 테이블이나 데이터를 가진 데이터베이스에 재실행하면 `already exists` 오류가 나거나 시드 키와 충돌할 수 있습니다. 이 스크립트는 migration도 아니며 기존 데이터를 안전하게 변환하지 않습니다.

시드 없이 빈 스키마만 필요한 경우 Workbench에서 SQL 파일을 열고 다음 주석 직전까지를 선택해 실행합니다.

```sql
-- Demo data for a fresh tayotayo_dev database.
```

즉, 위 주석과 그 아래 데모 `INSERT` 문은 실행에서 제외합니다. 원본 파일을 편집하지 않고 Workbench의 선택 실행을 사용하면 됩니다. 이후 관리자 웹에서 직접 기관·차량·정류장·노선·원아를 생성할 수 있습니다.

## 주요 테이블과 관계

| 영역 | 테이블 | 용도 |
| --- | --- | --- |
| 기관 | `organizations`, `org_staff` | 기관 정보와 사용자별 기관 역할 |
| 계정·인증 | `users`, `auth_sessions`, `email_verifications`, `user_consents`, `policy_documents` | 로그인 계정, 세션, 개발용 이메일 인증, 약관 동의 |
| 차량 | `vehicles`, `vehicle_pairing_codes`, `vehicle_devices` | 차량 명부, 일회용 페어링 코드, 연결된 앱 기기 |
| 원아·정류장 | `children`, `stops`, `routes`, `route_stops`, `child_route_assignments` | 원아, 지도 좌표, 운행 방향, 노선 순서와 요일·기간별 배정 |
| 가족·인계 | `guardian_invitations`, `child_guardians`, `authorized_pickup_persons`, `daily_pickups` | 보호자 연결, 지정 인계인, 날짜별 픽업 대상 |
| 운행 | `trips`, `trip_stops`, `trip_children`, `trip_events`, `trip_route_revisions`, `trip_checklist_items` | 운행 상태, 생성 시점 명단 스냅샷, 정류장, 이벤트, 체크리스트 |
| 위치·ETA | `trip_location_samples`, `trip_eta_snapshots`, `trip_eta_entries` | 차량 좌표와 내비게이션 ETA 관측 이력 |
| 요청·소통 | `attendance_requests`, `org_announcements`, `guardian_call_requests`, `share_links` | 출결 변경, 공지, 보호자 응답 요청, 제한된 운행 공유 |
| 알림 | `notifications`, `notification_deliveries`, `push_devices` | 알림 본문·수신 대상·FCM 전송 결과 |
| 기타 | `staff_invitations`, `org_handover_records` | 직원 초대와 기관 인계 이력 |

주요 연결은 `organizations` → `vehicles/routes/stops/children`이며, 노선 정류장 순서는 `route_stops`가 보관합니다. `child_route_assignments`는 원아와 노선·차량·정류장을 요일 및 적용일로 연결합니다. 운행을 만들면 이 배정 결과가 `trip_children`과 `trip_stops`에 당시 값으로 복사됩니다. 이후 원아 이름이나 노선이 바뀌어도 과거 운행의 스냅샷은 유지됩니다.

`trip_events`는 탑승·하차·정류장 도착 등 운행 이벤트를 기록합니다. `trip_location_samples`와 ETA 테이블은 위치 관측 기록이며 그 자체로 출결 상태를 변경하지 않습니다. `notifications`는 알림을 저장하고, `notification_deliveries`는 개별 기기 전송 결과를 저장합니다.

## 데이터 무결성과 삭제

스키마는 InnoDB와 외래 키를 사용해 기관·차량·노선·운행 관계를 보호합니다. 주요 이력 테이블의 삭제는 참조 중인 기록을 보존하기 위해 제한됩니다. 기관 운영 데이터의 일부는 실제 행 삭제 대신 `status`, `deleted_at`, `revoked_at` 등으로 비활성·철회 상태를 기록합니다. 과거 운행 이력을 보존해야 하므로 임의 `DELETE`보다 API의 비활성화·취소 기능을 사용하세요.

시간 컬럼은 `DATETIME(3)`이며 스키마 초기화 세션의 기본 타임존은 UTC로 설정됩니다. 노선 출발 시간은 `TIME`, 배정 및 운행일은 `DATE`입니다. 위치·ETA는 원천 시각과 서버 수신 시각을 구분하는 컬럼을 사용합니다.

## 서버 연결 설정

`backend/start-server.bat`의 첫 실행에서 입력한 연결 정보는 `backend/.secrets/server.properties`에 보관됩니다. 기본 환경 변수는 다음과 같습니다.

| 변수 | 기본값/설명 |
| --- | --- |
| `DB_URL` | 로컬 `tayotayo_dev` MySQL JDBC URL |
| `DB_USERNAME` | MySQL 사용자명. 시작 스크립트의 기본 제안은 `ssafy` |
| `DB_PASSWORD` | MySQL 비밀번호. 로컬 비밀 설정 파일에만 저장 |

MySQL이 다른 컴퓨터에 있으면 주소를 해당 PC의 LAN IP로 바꾸고, MySQL 원격 접속·사용자 권한·방화벽을 설정해야 합니다. 백엔드와 DB가 같은 PC면 `127.0.0.1`을 사용합니다.

Workbench에서 기본 DB와 테이블을 확인하려면 다음을 실행합니다.

```sql
USE tayotayo_dev;
SHOW TABLES;
```

개발 데이터 일부를 조회할 때는 기관과 날짜 범위를 제한하세요.

```sql
SELECT id, name, org_type, status
FROM organizations
ORDER BY id;

SELECT id, service_date, direction, status
FROM trips
ORDER BY id DESC;
```

## 백업·변경 관리

현재 실행용 SQL은 빈 개발 DB용 전체 스키마 파일이지 migration 체계가 아닙니다. 스키마 변경을 기존 DB에 반영하기 전에 백업하고, 변경용 migration을 별도로 작성해 적용하세요. 운영·공유 DB에서 재초기화를 시도하지 마세요. 데모 비밀번호, 차량 credential, FCM 서비스 계정 JSON과 실제 개인정보는 Git에 넣지 않습니다.

## 전체 컬럼 데이터 사전

다음 표는 실행용 SQL의 각 테이블 정의에서 컬럼명과 MySQL 자료형, null/default/comment 정의를 추출한 것입니다. 전체 기본키·외래 키는 다음 절에 연결별로 열거합니다.

### `organizations`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `name` | `varchar(100)	NOT NULL` |
| `org_type` | `varchar(20)	NOT NULL	DEFAULT 'OTHER'	COMMENT 'DAYCARE / KINDERGARTEN / ACADEMY / SCHOOL / OTHER'` |
| `phone` | `varchar(30)	NULL` |
| `address` | `varchar(255)	NULL` |
| `latitude` | `numeric(9,6)	NULL` |
| `longitude` | `numeric(9,6)	NULL` |
| `timezone` | `varchar(40)	NOT NULL	DEFAULT 'Asia/Seoul'` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / SUSPENDED / CLOSED'` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `deleted_at` | `DATETIME(3)	NULL` |

### `users`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `email` | `varchar(255)	NOT NULL` |
| `password_hash` | `varchar(255)	NULL` |
| `name` | `varchar(50)	NOT NULL` |
| `phone_enc` | `BLOB	NULL` |
| `email_verified_at` | `DATETIME(3)	NULL` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / LOCKED / WITHDRAWN'` |
| `last_login_at` | `DATETIME(3)	NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `withdrawn_at` | `DATETIME(3)	NULL` |

### `auth_sessions`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `user_id` | `bigint	NOT NULL` |
| `client_type` | `varchar(20)	NOT NULL	COMMENT 'GUARDIAN_APP / ADMIN_WEB / TEACHER_APP'` |
| `refresh_token_hash` | `char(64)	NOT NULL` |
| `device_label` | `varchar(100)	NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `last_used_at` | `DATETIME(3)	NULL` |
| `expires_at` | `DATETIME(3)	NOT NULL` |
| `revoked_at` | `DATETIME(3)	NULL` |

### `email_verifications`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `user_id` | `bigint	NULL` |
| `email` | `varchar(255)	NOT NULL` |
| `purpose` | `varchar(20)	NOT NULL	COMMENT 'SIGN_UP / EMAIL_CHANGE / PASSWORD_RESET'` |
| `code_hash` | `char(64)	NOT NULL` |
| `attempt_count` | `smallint	NOT NULL	DEFAULT 0` |
| `expires_at` | `DATETIME(3)	NOT NULL` |
| `consumed_at` | `DATETIME(3)	NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `policy_documents`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `policy_type` | `varchar(30)	NOT NULL	COMMENT 'TERMS / PRIVACY / LOCATION / CHILD_INFO / PUSH / MARKETING'` |
| `version` | `varchar(20)	NOT NULL` |
| `title` | `varchar(200)	NOT NULL` |
| `content_url` | `varchar(500)	NULL` |
| `is_required` | `TINYINT(1)	NOT NULL	DEFAULT true` |
| `effective_at` | `DATETIME(3)	NOT NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `user_consents`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `user_id` | `bigint	NOT NULL` |
| `policy_document_id` | `bigint	NOT NULL` |
| `child_id` | `bigint	NULL` |
| `agreed_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `withdrawn_at` | `DATETIME(3)	NULL` |
| `agreed_ip` | `VARCHAR(45)	NULL` |

### `org_staff`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL` |
| `user_id` | `bigint	NULL` |
| `name` | `varchar(50)	NOT NULL` |
| `phone_enc` | `BLOB	NULL` |
| `staff_role` | `varchar(20)	NOT NULL	COMMENT 'ADMIN / TEACHER / DRIVER'` |
| `web_role` | `varchar(20)	NULL	COMMENT 'OWNER / MANAGER / VIEWER'` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / INACTIVE'` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `deleted_at` | `DATETIME(3)	NULL` |

### `vehicles`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL` |
| `name` | `varchar(50)	NOT NULL` |
| `plate_number` | `varchar(20)	NULL` |
| `model` | `varchar(50)	NULL` |
| `seat_capacity` | `smallint	NULL` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / INACTIVE'` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `deleted_at` | `DATETIME(3)	NULL` |

### `vehicle_pairing_codes`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL	COMMENT '→ 기관.id (ERD 선 생략)'` |
| `vehicle_id` | `bigint	NOT NULL` |
| `code_hash` | `char(64)	NOT NULL` |
| `issued_by_staff_id` | `bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `expires_at` | `DATETIME(3)	NOT NULL` |
| `used_at` | `DATETIME(3)	NULL` |
| `used_by_device_id` | `bigint	NULL` |
| `revoked_at` | `DATETIME(3)	NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `vehicle_devices`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL	COMMENT '→ 기관.id (ERD 선 생략)'` |
| `vehicle_id` | `bigint	NOT NULL` |
| `install_id` | `CHAR(36)	NOT NULL` |
| `credential_hash` | `char(64)	NOT NULL` |
| `app_version` | `varchar(20)	NULL` |
| `pleos_sdk_version` | `varchar(20)	NULL` |
| `paired_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `last_seen_at` | `DATETIME(3)	NULL` |
| `revoked_at` | `DATETIME(3)	NULL` |

### `stops`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL` |
| `name` | `varchar(100)	NOT NULL` |
| `address` | `varchar(255)	NULL` |
| `latitude` | `numeric(9,6)	NOT NULL` |
| `longitude` | `numeric(9,6)	NOT NULL` |
| `arrival_radius_m` | `smallint	NOT NULL	DEFAULT 50` |
| `memo` | `varchar(255)	NULL` |
| `is_active` | `TINYINT(1)	NOT NULL	DEFAULT true` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `deleted_at` | `DATETIME(3)	NULL` |

### `children`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL` |
| `name` | `varchar(50)	NOT NULL` |
| `class_name` | `varchar(50)	NULL` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / INACTIVE / GRADUATED'` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `deleted_at` | `DATETIME(3)	NULL` |

### `route_stops`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL` |
| `route_id` | `bigint	NOT NULL` |
| `stop_id` | `bigint	NOT NULL` |
| `default_seq` | `smallint	NOT NULL` |

### `child_route_assignments`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL` |
| `child_id` | `bigint	NOT NULL` |
| `route_id` | `bigint	NOT NULL` |
| `vehicle_id` | `bigint	NOT NULL` |
| `stop_id` | `bigint	NOT NULL` |
| `weekdays` | `smallint	NOT NULL	DEFAULT 31` |
| `effective_from` | `date	NOT NULL	DEFAULT (CURRENT_DATE)` |
| `effective_to` | `date	NULL` |
| `assigned_by_staff_id` | `bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `routes`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL	COMMENT '→ 기관.id (ERD 선 생략)'` |
| `vehicle_id` | `bigint	NOT NULL` |
| `name` | `varchar(100)	NOT NULL` |
| `direction` | `varchar(10)	NOT NULL	DEFAULT 'DROP_OFF'	COMMENT 'DROP_OFF / PICK_UP'` |
| `departure_time` | `time	NULL` |
| `is_active` | `TINYINT(1)	NOT NULL	DEFAULT true` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `deleted_at` | `DATETIME(3)	NULL` |

### `guardian_invitations`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL` |
| `child_id` | `bigint	NOT NULL` |
| `invitee_email` | `varchar(255)	NOT NULL` |
| `relationship` | `varchar(20)	NULL	COMMENT 'MOTHER / FATHER / GRANDPARENT / SIBLING / OTHER'` |
| `code_hash` | `char(64)	NOT NULL` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / ACCEPTED / EXPIRED / REVOKED'` |
| `expires_at` | `DATETIME(3)	NOT NULL` |
| `accepted_at` | `DATETIME(3)	NULL` |
| `accepted_by_user_id` | `bigint	NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)'` |
| `revoked_at` | `DATETIME(3)	NULL` |
| `reissued_from_id` | `bigint	NULL` |
| `issued_by_staff_id` | `bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `child_guardians`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `child_id` | `bigint	NOT NULL` |
| `user_id` | `bigint	NOT NULL` |
| `relationship` | `varchar(20)	NOT NULL	DEFAULT 'OTHER'	COMMENT 'MOTHER / FATHER / GRANDPARENT / SIBLING / OTHER'` |
| `is_primary` | `TINYINT(1)	NOT NULL	DEFAULT false` |
| `invitation_id` | `bigint	NULL` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / REVOKED'` |
| `linked_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `revoked_at` | `DATETIME(3)	NULL` |

### `authorized_pickup_persons`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL` |
| `child_id` | `bigint	NOT NULL` |
| `name` | `varchar(50)	NOT NULL` |
| `relationship` | `varchar(30)	NOT NULL` |
| `phone_enc` | `BLOB	NULL` |
| `registered_by_user_id` | `bigint	NOT NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)'` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / APPROVED / REJECTED / REVOKED'` |
| `reviewed_by_staff_id` | `bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `reviewed_at` | `DATETIME(3)	NULL` |
| `valid_from` | `date	NULL` |
| `valid_to` | `date	NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `push_devices`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `user_id` | `bigint	NOT NULL` |
| `platform` | `varchar(10)	NOT NULL	COMMENT 'ANDROID / IOS / WEB'` |
| `fcm_token` | `varchar(512)	NOT NULL` |
| `app_version` | `varchar(20)	NULL` |
| `os_permission` | `varchar(20)	NOT NULL	DEFAULT 'UNKNOWN'	COMMENT 'GRANTED / DENIED / UNKNOWN'` |
| `registered_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `last_refreshed_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `revoked_at` | `DATETIME(3)	NULL` |
| `revoke_reason` | `varchar(30)	NULL` |

### `attendance_requests`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL` |
| `child_id` | `bigint	NOT NULL` |
| `requested_by_user_id` | `bigint	NOT NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)'` |
| `service_date` | `date	NOT NULL` |
| `direction` | `varchar(10)	NOT NULL	COMMENT 'DROP_OFF / PICK_UP'` |
| `request_type` | `varchar(20)	NOT NULL	COMMENT 'ABSENT / SELF_PICKUP / STOP_CHANGE / PICKUP_PERSON / NOT_RIDING'` |
| `reason_code` | `varchar(20)	NULL	COMMENT 'SICK / FAMILY / TRIP / OTHER'` |
| `reason_text` | `varchar(500)	NULL` |
| `requested_stop_id` | `bigint	NULL` |
| `pickup_person_id` | `bigint	NULL` |
| `pickup_guardian_user_id` | `bigint	NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)'` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / APPROVED / REJECTED / CANCELLED'` |
| `reviewed_by_staff_id` | `bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `reviewed_at` | `DATETIME(3)	NULL` |
| `review_note` | `varchar(500)	NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `trips`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `client_trip_id` | `CHAR(36)	NOT NULL` |
| `org_id` | `bigint	NOT NULL` |
| `vehicle_id` | `bigint	NOT NULL` |
| `route_id` | `bigint	NULL` |
| `vehicle_device_id` | `bigint	NOT NULL` |
| `direction` | `varchar(10)	NOT NULL	DEFAULT 'DROP_OFF'	COMMENT 'DROP_OFF / PICK_UP'` |
| `service_date` | `date	NOT NULL` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'PREPARING'	COMMENT 'PREPARING / IN_PROGRESS / RETURNING / COMPLETED / CANCELLED'` |
| `attendant_staff_id` | `bigint	NULL` |
| `driver_staff_id` | `bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `route_revision_no` | `integer	NOT NULL	DEFAULT 0` |
| `last_latitude` | `numeric(9,6)	NULL` |
| `last_longitude` | `numeric(9,6)	NULL` |
| `last_speed_kph` | `numeric(5,1)	NULL` |
| `last_gear` | `varchar(10)	NULL	COMMENT 'P / R / N / D / UNKNOWN'` |
| `last_location_at` | `DATETIME(3)	NULL` |
| `driver_checked_at` | `DATETIME(3)	NULL	COMMENT '기사 구두 확인을 교사가 체크한 시각. 미확인 출발 가능'` |
| `boarding_completed_at` | `DATETIME(3)	NULL` |
| `started_at` | `DATETIME(3)	NULL` |
| `returning_at` | `DATETIME(3)	NULL` |
| `returned_at` | `DATETIME(3)	NULL` |
| `interior_checked_at` | `DATETIME(3)	NULL` |
| `ended_at` | `DATETIME(3)	NULL` |
| `cancelled_at` | `DATETIME(3)	NULL` |
| `cancel_reason` | `varchar(200)	NULL` |
| `end_note` | `varchar(500)	NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `trip_stops`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `trip_id` | `bigint	NOT NULL` |
| `stop_id` | `bigint	NOT NULL` |
| `seq` | `smallint	NOT NULL` |
| `suggested_seq` | `smallint	NULL` |
| `stop_name` | `varchar(100)	NOT NULL` |
| `latitude` | `numeric(9,6)	NOT NULL` |
| `longitude` | `numeric(9,6)	NOT NULL` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / ARRIVED / COMPLETED / SKIPPED'` |
| `arrived_at` | `DATETIME(3)	NULL` |
| `completed_at` | `DATETIME(3)	NULL` |
| `eta_remaining_sec` | `integer	NULL` |
| `eta_remaining_m` | `integer	NULL` |
| `eta_snapshot_id` | `bigint	NULL` |
| `eta_received_at` | `DATETIME(3)	NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `trip_route_revisions`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `trip_id` | `bigint	NOT NULL` |
| `revision_no` | `integer	NOT NULL` |
| `stop_order` | `JSON	NOT NULL` |
| `change_reason` | `varchar(20)	NOT NULL	COMMENT 'INITIAL / MANUAL_REORDER / STOP_SKIPPED / STOP_ADDED'` |
| `changed_by_staff_id` | `bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `changed_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `vehicle_speed_kph` | `numeric(5,1)	NULL` |
| `vehicle_gear` | `varchar(10)	NULL` |

### `trip_events`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `client_event_id` | `CHAR(36)	NOT NULL` |
| `trip_id` | `bigint	NOT NULL` |
| `trip_child_id` | `bigint	NULL` |
| `trip_stop_id` | `bigint	NULL` |
| `event_type` | `varchar(40)	NOT NULL	COMMENT 'TRIP_PREPARED / CHILD_BOARDED / CHILD_BOARDING_UNDONE / CHILD_NOT_BOARDED / BOARDING_COMPLETED / ROUTE_REORDERED / TRIP_STARTED / NAVIGATION_REQUESTED / STOP_ARRIVED / CHILD_HANDED_OVER / CHILD_DROPPED_OFF / GUARDIAN_NO_SHOW / STOP_COMPLETED / STOP_SKIPPED / TRIP_RETURNING / TRIP_RETURNED / CHILD_RETURNED_TO_ORG / INTERIOR_CHECKED / TRIP_ENDED / TRIP_CANCELLED / VOICE_ANNOUNCED / PROXIMITY_ALERT / GUARDIAN_CALL_REQUESTED / GUARDIAN_CALL_RESPONDED / TEACHER_CONFIRMED_NOT_BOARDED / CHILD_RECEIVED_BY_ORG / DRIVER_PRE_DEPARTURE_CONFIRMED / CORRECTION'` |
| `corrects_event_id` | `bigint	NULL` |
| `occurred_at` | `DATETIME(3)	NOT NULL` |
| `received_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `recorded_by_device_id` | `bigint	NULL` |
| `recorded_by_staff_id` | `bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `vs_speed_kph` | `numeric(5,1)	NULL` |
| `vs_gear` | `varchar(10)	NULL	COMMENT 'P / R / N / D / UNKNOWN'` |
| `vs_ux_restricted` | `TINYINT(1)	NULL` |
| `vs_connected` | `TINYINT(1)	NULL` |
| `vs_sampled_at` | `DATETIME(3)	NULL` |
| `latitude` | `numeric(9,6)	NULL` |
| `longitude` | `numeric(9,6)	NULL` |
| `payload` | `JSON	NOT NULL	DEFAULT (JSON_OBJECT())` |

### `trip_children`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL` |
| `trip_id` | `bigint	NOT NULL` |
| `vehicle_id` | `bigint	NOT NULL` |
| `child_id` | `bigint	NOT NULL` |
| `trip_stop_id` | `bigint	NOT NULL` |
| `child_name` | `varchar(50)	NOT NULL` |
| `boarding_expected` | `TINYINT(1)	NOT NULL	DEFAULT 1	COMMENT '해당 방향 운행에서 탑승 예정인지. 기본 탑승'` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'EXPECTED'	COMMENT 'EXPECTED / BOARDED / NOT_BOARDED / HANDED_OVER / DROPPED_OFF / GUARDIAN_NO_SHOW / RETURNED_TO_ORG'` |
| `not_boarded_reason` | `varchar(20)	NULL	COMMENT 'ABSENT / SELF_PICKUP / OTHER / NOT_RIDING'` |
| `attendance_request_id` | `bigint	NULL` |
| `boarded_at` | `DATETIME(3)	NULL` |
| `handed_over_at` | `DATETIME(3)	NULL` |
| `handover_to_type` | `varchar(20)	NULL	COMMENT 'GUARDIAN / AUTHORIZED_PERSON / ORG_STAFF'` |
| `handover_guardian_user_id` | `bigint	NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)'` |
| `handover_pickup_person_id` | `bigint	NULL` |
| `handover_person_name` | `varchar(50)	NULL` |
| `dropped_off_at` | `DATETIME(3)	NULL` |
| `no_show_at` | `DATETIME(3)	NULL` |
| `no_show_reason` | `varchar(300)	NULL` |
| `returned_to_org_at` | `DATETIME(3)	NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `trip_eta_snapshots`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `trip_id` | `bigint	NOT NULL` |
| `route_revision_no` | `integer	NOT NULL` |
| `nav_route_key` | `varchar(100)	NULL` |
| `source_time` | `DATETIME(3)	NOT NULL` |
| `received_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `validation_status` | `varchar(20)	NOT NULL	COMMENT 'VALID / STOP_MISMATCH / ROUTE_MISMATCH / REROUTING / STALE / INVALID_PAYLOAD'` |
| `validation_note` | `varchar(300)	NULL` |
| `raw_payload` | `JSON	NOT NULL` |

### `trip_location_samples`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `trip_id` | `bigint	NOT NULL` |
| `recorded_at` | `DATETIME(3)	NOT NULL` |
| `received_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `latitude` | `numeric(9,6)	NOT NULL` |
| `longitude` | `numeric(9,6)	NOT NULL` |
| `speed_kph` | `numeric(5,1)	NULL` |
| `heading_deg` | `smallint	NULL` |
| `accuracy_m` | `numeric(6,1)	NULL` |
| `gear` | `varchar(10)	NULL	COMMENT 'P / R / N / D / UNKNOWN'` |
| `source` | `varchar(10)	NOT NULL	DEFAULT 'PLEOS'	COMMENT 'PLEOS / GPS'` |

### `org_announcements`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL` |
| `trip_id` | `bigint	NULL` |
| `audience` | `varchar(20)	NOT NULL	COMMENT 'ALL / VEHICLE / TRIP'` |
| `vehicle_id` | `bigint	NULL` |
| `category` | `varchar(20)	NOT NULL	COMMENT 'DELAY / EMERGENCY / NOTICE'` |
| `title` | `varchar(100)	NOT NULL` |
| `body` | `varchar(1000)	NOT NULL` |
| `created_by_staff_id` | `bigint	NOT NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `sent_at` | `DATETIME(3)	NULL` |

### `trip_eta_entries`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `trip_id` | `bigint	NOT NULL` |
| `snapshot_id` | `bigint	NOT NULL` |
| `bundle_index` | `smallint	NOT NULL` |
| `point_type` | `varchar(20)	NOT NULL	COMMENT 'WAYPOINT / DESTINATION'` |
| `trip_stop_id` | `bigint	NULL` |
| `latitude` | `numeric(9,6)	NULL` |
| `longitude` | `numeric(9,6)	NULL` |
| `remaining_sec` | `integer	NOT NULL` |
| `remaining_m` | `integer	NULL` |

### `notifications`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `user_id` | `bigint	NOT NULL` |
| `child_id` | `bigint	NULL` |
| `trip_id` | `bigint	NULL` |
| `trip_event_id` | `bigint	NULL` |
| `announcement_id` | `bigint	NULL` |
| `notification_type` | `varchar(30)	NOT NULL	COMMENT 'TRIP_DEPARTED / APPROACHING / ARRIVED / DROPPED_OFF / GUARDIAN_NO_SHOW / RETURNED_TO_ORG / TRIP_DELAYED / ANNOUNCEMENT / REQUEST_REVIEWED'` |
| `dedupe_key` | `varchar(150)	NOT NULL	COMMENT '출결 알림 묶음 키에는 direction 포함: attendance:{child_id}:{service_date}:{direction}'` |
| `title` | `varchar(100)	NOT NULL` |
| `body` | `varchar(500)	NOT NULL` |
| `data` | `JSON	NOT NULL	DEFAULT (JSON_OBJECT())` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / SENT / PARTIAL / FAILED / CANCELLED / SKIPPED'` |
| `cancel_reason` | `varchar(50)	NULL` |
| `eta_snapshot_id` | `bigint	NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `sent_at` | `DATETIME(3)	NULL` |
| `read_at` | `DATETIME(3)	NULL` |

### `trip_checklist_items`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `trip_id` | `bigint	NOT NULL` |
| `phase` | `varchar(20)	NOT NULL	COMMENT 'PRE_DEPARTURE / POST_TRIP'` |
| `item_code` | `varchar(40)	NOT NULL` |
| `result` | `varchar(10)	NOT NULL	COMMENT 'OK / NG / NA'` |
| `note` | `varchar(300)	NULL` |
| `checked_by_staff_id` | `bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `checked_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `share_links`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `token_hash` | `char(64)	NOT NULL` |
| `child_id` | `bigint	NOT NULL` |
| `trip_id` | `bigint	NOT NULL` |
| `created_by_user_id` | `bigint	NOT NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)'` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `expires_at` | `DATETIME(3)	NOT NULL` |
| `revoked_at` | `DATETIME(3)	NULL` |
| `revoke_reason` | `varchar(20)	NULL	COMMENT 'MANUAL / DROPPED_OFF / TRIP_ENDED / GUARDIAN_UNLINKED'` |
| `last_accessed_at` | `DATETIME(3)	NULL` |
| `access_count` | `integer	NOT NULL	DEFAULT 0` |

### `notification_deliveries`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `notification_id` | `bigint	NOT NULL` |
| `push_device_id` | `bigint	NOT NULL` |
| `attempt_no` | `smallint	NOT NULL	DEFAULT 1` |
| `status` | `varchar(20)	NOT NULL	COMMENT 'QUEUED / SENT / FAILED / INVALID_TOKEN'` |
| `fcm_message_id` | `varchar(200)	NULL` |
| `error_code` | `varchar(50)	NULL` |
| `attempted_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `next_retry_at` | `DATETIME(3)	NULL` |

### `org_handover_records`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `trip_child_id` | `bigint	NOT NULL` |
| `received_by_staff_id` | `bigint	NOT NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `received_at` | `DATETIME(3)	NOT NULL` |
| `guardian_contacted_at` | `DATETIME(3)	NULL` |
| `resolution` | `varchar(30)	NULL	COMMENT 'PICKED_UP_AT_ORG / REDELIVERED / OTHER'` |
| `resolved_at` | `DATETIME(3)	NULL` |
| `note` | `varchar(500)	NULL` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `staff_invitations`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint	NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint	NOT NULL	COMMENT '→ 기관.id (ERD 선 생략)'` |
| `staff_id` | `bigint	NULL` |
| `invitee_email` | `varchar(255)	NOT NULL` |
| `staff_role` | `varchar(20)	NOT NULL	COMMENT 'ADMIN / TEACHER / DRIVER'` |
| `web_role` | `varchar(20)	NULL	COMMENT 'OWNER / MANAGER / VIEWER'` |
| `code_hash` | `char(64)	NOT NULL` |
| `status` | `varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / ACCEPTED / EXPIRED / REVOKED'` |
| `expires_at` | `DATETIME(3)	NOT NULL` |
| `accepted_at` | `DATETIME(3)	NULL` |
| `accepted_by_user_id` | `bigint	NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)'` |
| `revoked_at` | `DATETIME(3)	NULL` |
| `issued_by_staff_id` | `bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)'` |
| `created_at` | `DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)` |

### `daily_pickups`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint NOT NULL AUTO_INCREMENT` |
| `org_id` | `bigint NOT NULL` |
| `child_id` | `bigint NOT NULL` |
| `service_date` | `date NOT NULL` |
| `direction` | `varchar(10) NOT NULL COMMENT 'DROP_OFF / PICK_UP'` |
| `pickup_person_id` | `bigint NULL` |
| `pickup_guardian_user_id` | `bigint NULL` |
| `person_name_snapshot` | `varchar(50) NULL` |
| `relationship_snapshot` | `varchar(30) NULL` |
| `updated_by_user_id` | `bigint NULL` |
| `created_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` |

### `guardian_call_requests`

| 컬럼 | SQL 정의 |
| --- | --- |
| `id` | `bigint NOT NULL AUTO_INCREMENT` |
| `trip_id` | `bigint NOT NULL` |
| `trip_child_id` | `bigint NOT NULL` |
| `status` | `varchar(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / RESPONDED / CLOSED / EXPIRED'` |
| `opened_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` |
| `expires_at` | `DATETIME(3) NOT NULL` |
| `response` | `varchar(20) NULL COMMENT 'ON_THE_WAY / NOT_RIDING'` |
| `responded_at` | `DATETIME(3) NULL` |
| `closed_at` | `DATETIME(3) NULL` |
| `created_by_staff_id` | `bigint NULL` |
| `created_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` |
| `updated_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` |

## 외래 키 연결 목록

실행용 SQL의 ALTER TABLE ... ADD CONSTRAINT ... FOREIGN KEY 관계입니다. 모든 관계는 업데이트 CASCADE와 삭제 RESTRICT 정책을 사용합니다.

### `attendance_requests`

- `org_id` → `organizations.id` (`fk_attendance_requests_org_id`)
- `child_id` → `children.id` (`fk_attendance_requests_child_id`)
- `requested_by_user_id` → `users.id` (`fk_attendance_requests_requested_by_user_id`)
- `requested_stop_id` → `stops.id` (`fk_attendance_requests_requested_stop_id`)
- `pickup_person_id` → `authorized_pickup_persons.id` (`fk_attendance_requests_pickup_person_id`)
- `pickup_guardian_user_id` → `users.id` (`fk_attendance_requests_pickup_guardian_user_id`)
- `reviewed_by_staff_id` → `org_staff.id` (`fk_attendance_requests_reviewed_by_staff_id`)

### `auth_sessions`

- `user_id` → `users.id` (`fk_auth_sessions_user_id`)

### `authorized_pickup_persons`

- `org_id` → `organizations.id` (`fk_authorized_pickup_persons_org_id`)
- `child_id` → `children.id` (`fk_authorized_pickup_persons_child_id`)
- `registered_by_user_id` → `users.id` (`fk_authorized_pickup_persons_registered_by_user_id`)
- `reviewed_by_staff_id` → `org_staff.id` (`fk_authorized_pickup_persons_reviewed_by_staff_id`)

### `child_guardians`

- `child_id` → `children.id` (`fk_child_guardians_child_id`)
- `user_id` → `users.id` (`fk_child_guardians_user_id`)
- `invitation_id` → `guardian_invitations.id` (`fk_child_guardians_invitation_id`)

### `child_route_assignments`

- `org_id` → `organizations.id` (`fk_child_route_assignments_org_id`)
- `child_id` → `children.id` (`fk_child_route_assignments_child_id`)
- `route_id` → `routes.id` (`fk_child_route_assignments_route_id`)
- `vehicle_id` → `vehicles.id` (`fk_child_route_assignments_vehicle_id`)
- `stop_id` → `stops.id` (`fk_child_route_assignments_stop_id`)
- `assigned_by_staff_id` → `org_staff.id` (`fk_child_route_assignments_assigned_by_staff_id`)

### `children`

- `org_id` → `organizations.id` (`fk_children_org_id`)

### `daily_pickups`

- `org_id` → `organizations.id` (`fk_daily_pickups_org_id`)
- `child_id` → `children.id` (`fk_daily_pickups_child_id`)
- `pickup_person_id` → `authorized_pickup_persons.id` (`fk_daily_pickups_pickup_person_id`)
- `pickup_guardian_user_id` → `users.id` (`fk_daily_pickups_guardian_user_id`)
- `updated_by_user_id` → `users.id` (`fk_daily_pickups_updated_by_user_id`)

### `email_verifications`

- `user_id` → `users.id` (`fk_email_verifications_user_id`)

### `guardian_call_requests`

- `trip_id` → `trips.id` (`fk_guardian_call_requests_trip_id`)
- `trip_child_id` → `trip_children.id` (`fk_guardian_call_requests_trip_child_id`)
- `created_by_staff_id` → `org_staff.id` (`fk_guardian_call_requests_created_by_staff_id`)

### `guardian_invitations`

- `org_id` → `organizations.id` (`fk_guardian_invitations_org_id`)
- `child_id` → `children.id` (`fk_guardian_invitations_child_id`)
- `accepted_by_user_id` → `users.id` (`fk_guardian_invitations_accepted_by_user_id`)
- `reissued_from_id` → `guardian_invitations.id` (`fk_guardian_invitations_reissued_from_id`)
- `issued_by_staff_id` → `org_staff.id` (`fk_guardian_invitations_issued_by_staff_id`)

### `notification_deliveries`

- `notification_id` → `notifications.id` (`fk_notification_deliveries_notification_id`)
- `push_device_id` → `push_devices.id` (`fk_notification_deliveries_push_device_id`)

### `notifications`

- `user_id` → `users.id` (`fk_notifications_user_id`)
- `child_id` → `children.id` (`fk_notifications_child_id`)
- `trip_id` → `trips.id` (`fk_notifications_trip_id`)
- `trip_event_id` → `trip_events.id` (`fk_notifications_trip_event_id`)
- `announcement_id` → `org_announcements.id` (`fk_notifications_announcement_id`)
- `eta_snapshot_id` → `trip_eta_snapshots.id` (`fk_notifications_eta_snapshot_id`)

### `org_announcements`

- `org_id` → `organizations.id` (`fk_org_announcements_org_id`)
- `trip_id` → `trips.id` (`fk_org_announcements_trip_id`)
- `vehicle_id` → `vehicles.id` (`fk_org_announcements_vehicle_id`)
- `created_by_staff_id` → `org_staff.id` (`fk_org_announcements_created_by_staff_id`)

### `org_handover_records`

- `trip_child_id` → `trip_children.id` (`fk_org_handover_records_trip_child_id`)
- `received_by_staff_id` → `org_staff.id` (`fk_org_handover_records_received_by_staff_id`)

### `org_staff`

- `org_id` → `organizations.id` (`fk_org_staff_org_id`)
- `user_id` → `users.id` (`fk_org_staff_user_id`)

### `push_devices`

- `user_id` → `users.id` (`fk_push_devices_user_id`)

### `route_stops`

- `org_id` → `organizations.id` (`fk_route_stops_org_id`)
- `route_id` → `routes.id` (`fk_route_stops_route_id`)
- `stop_id` → `stops.id` (`fk_route_stops_stop_id`)

### `routes`

- `org_id` → `organizations.id` (`fk_routes_org_id`)
- `vehicle_id` → `vehicles.id` (`fk_routes_vehicle_id`)

### `share_links`

- `child_id` → `children.id` (`fk_share_links_child_id`)
- `trip_id` → `trips.id` (`fk_share_links_trip_id`)
- `created_by_user_id` → `users.id` (`fk_share_links_created_by_user_id`)

### `staff_invitations`

- `org_id` → `organizations.id` (`fk_staff_invitations_org_id`)
- `staff_id` → `org_staff.id` (`fk_staff_invitations_staff_id`)
- `accepted_by_user_id` → `users.id` (`fk_staff_invitations_accepted_by_user_id`)
- `issued_by_staff_id` → `org_staff.id` (`fk_staff_invitations_issued_by_staff_id`)

### `stops`

- `org_id` → `organizations.id` (`fk_stops_org_id`)

### `trip_checklist_items`

- `trip_id` → `trips.id` (`fk_trip_checklist_items_trip_id`)
- `checked_by_staff_id` → `org_staff.id` (`fk_trip_checklist_items_checked_by_staff_id`)

### `trip_children`

- `org_id` → `organizations.id` (`fk_trip_children_org_id`)
- `trip_id` → `trips.id` (`fk_trip_children_trip_id`)
- `vehicle_id` → `vehicles.id` (`fk_trip_children_vehicle_id`)
- `child_id` → `children.id` (`fk_trip_children_child_id`)
- `trip_stop_id` → `trip_stops.id` (`fk_trip_children_trip_stop_id`)
- `attendance_request_id` → `attendance_requests.id` (`fk_trip_children_attendance_request_id`)
- `handover_guardian_user_id` → `users.id` (`fk_trip_children_handover_guardian_user_id`)
- `handover_pickup_person_id` → `authorized_pickup_persons.id` (`fk_trip_children_handover_pickup_person_id`)

### `trip_eta_entries`

- `trip_id` → `trips.id` (`fk_trip_eta_entries_trip_id`)
- `snapshot_id` → `trip_eta_snapshots.id` (`fk_trip_eta_entries_snapshot_id`)
- `trip_stop_id` → `trip_stops.id` (`fk_trip_eta_entries_trip_stop_id`)

### `trip_eta_snapshots`

- `trip_id` → `trips.id` (`fk_trip_eta_snapshots_trip_id`)

### `trip_events`

- `trip_id` → `trips.id` (`fk_trip_events_trip_id`)
- `trip_child_id` → `trip_children.id` (`fk_trip_events_trip_child_id`)
- `trip_stop_id` → `trip_stops.id` (`fk_trip_events_trip_stop_id`)
- `corrects_event_id` → `trip_events.id` (`fk_trip_events_corrects_event_id`)
- `recorded_by_device_id` → `vehicle_devices.id` (`fk_trip_events_recorded_by_device_id`)
- `recorded_by_staff_id` → `org_staff.id` (`fk_trip_events_recorded_by_staff_id`)

### `trip_location_samples`

- `trip_id` → `trips.id` (`fk_trip_location_samples_trip_id`)

### `trip_route_revisions`

- `trip_id` → `trips.id` (`fk_trip_route_revisions_trip_id`)
- `changed_by_staff_id` → `org_staff.id` (`fk_trip_route_revisions_changed_by_staff_id`)

### `trip_stops`

- `trip_id` → `trips.id` (`fk_trip_stops_trip_id`)
- `stop_id` → `stops.id` (`fk_trip_stops_stop_id`)
- `eta_snapshot_id` → `trip_eta_snapshots.id` (`fk_trip_stops_eta_snapshot_id`)

### `trips`

- `org_id` → `organizations.id` (`fk_trips_org_id`)
- `vehicle_id` → `vehicles.id` (`fk_trips_vehicle_id`)
- `route_id` → `routes.id` (`fk_trips_route_id`)
- `vehicle_device_id` → `vehicle_devices.id` (`fk_trips_vehicle_device_id`)
- `attendant_staff_id` → `org_staff.id` (`fk_trips_attendant_staff_id`)
- `driver_staff_id` → `org_staff.id` (`fk_trips_driver_staff_id`)

### `user_consents`

- `user_id` → `users.id` (`fk_user_consents_user_id`)
- `policy_document_id` → `policy_documents.id` (`fk_user_consents_policy_document_id`)
- `child_id` → `children.id` (`fk_user_consents_child_id`)

### `vehicle_devices`

- `org_id` → `organizations.id` (`fk_vehicle_devices_org_id`)
- `vehicle_id` → `vehicles.id` (`fk_vehicle_devices_vehicle_id`)

### `vehicle_pairing_codes`

- `org_id` → `organizations.id` (`fk_vehicle_pairing_codes_org_id`)
- `vehicle_id` → `vehicles.id` (`fk_vehicle_pairing_codes_vehicle_id`)
- `issued_by_staff_id` → `org_staff.id` (`fk_vehicle_pairing_codes_issued_by_staff_id`)
- `used_by_device_id` → `vehicle_devices.id` (`fk_vehicle_pairing_codes_used_by_device_id`)

### `vehicles`

- `org_id` → `organizations.id` (`fk_vehicles_org_id`)

## 고유성과 인덱스

실행 SQL에서 명시한 고유 제약은 `daily_pickups`의 `uq_daily_pickup_child_date_direction`입니다. 한 원아·날짜·운행 방향 조합에 하루 픽업 대상을 하나만 저장하도록 합니다. 그 외 데이터 무결성은 기본키, 외래 키, 애플리케이션 검증에 의존하는 필드가 많습니다. 테이블에 컬럼이 있다고 해서 DB 차원의 UNIQUE 제약이나 enum 검증이 자동으로 존재한다고 가정하지 마세요.

## 상태 코드 및 enum 목록

스키마 주석에 정의된 상태/enum 값은 SQL의 원문을 기준으로 합니다. 아래 값은 도메인별 주요 목록이며, API validation 및 컨트롤러 구현에서 추가로 제한하는 값은 Swagger/API 구현과 함께 확인해야 합니다.

| 도메인 | 저장 값 |
| --- | --- |
| 기관 유형 | `DAYCARE`, `KINDERGARTEN`, `ACADEMY`, `SCHOOL`, `OTHER` |
| 기관 상태 | `ACTIVE`, `SUSPENDED`, `CLOSED` |
| 사용자 상태 | `ACTIVE`, `LOCKED`, `WITHDRAWN` |
| 직원 역할 | `ADMIN`, `TEACHER`, `DRIVER`; 웹 역할 `OWNER`, `MANAGER`, `VIEWER` |
| 차량/직원 상태 | `ACTIVE`, `INACTIVE` |
| 노선 방향 | `PICK_UP`, `DROP_OFF` |
| 초대 상태 | `PENDING`, `ACCEPTED`, `EXPIRED`, `REVOKED` |
| 배정 요일 | 월~일을 bitmask로 합산. 월~금은 `31` |
| 운행 상태 | `PREPARING`, `IN_PROGRESS`, `RETURNING`, `COMPLETED`, `CANCELLED` |
| 정류장 상태 | `PENDING`, `ARRIVED`, `COMPLETED`, `SKIPPED` |
| 운행 원아 상태 | `EXPECTED`, `BOARDED`, `NOT_BOARDED`, `HANDED_OVER`, `DROPPED_OFF`, `GUARDIAN_NO_SHOW`, `RETURNED_TO_ORG` |
| ETA 검증 | `VALID`, `STOP_MISMATCH`, `ROUTE_MISMATCH`, `REROUTING`, `STALE`, `INVALID_PAYLOAD` |
| 위치 출처 | `PLEOS`, `GPS` |
| 알림 전송 | 알림 `PENDING`, `SENT`, `PARTIAL`, `FAILED`, `CANCELLED`, `SKIPPED`; 기기 전달 `QUEUED`, `SENT`, `FAILED`, `INVALID_TOKEN` |

전체 event type과 개별 상태 주석은 컬럼 데이터 사전 표에 표시된 `COMMENT`를 우선하세요.
