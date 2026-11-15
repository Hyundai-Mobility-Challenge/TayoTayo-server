# TayoTayo 서버 구조와 상세 사용 방법

[README](../README.md)에서 옮긴 상세 문서입니다. 실행 순서와 전체 구성은 README를 먼저 보세요.

## 서버 폴더와 구현 기능 상세

이 절은 데모 데이터 등록 절차가 아니라 저장소에 구현된 백엔드의 구조와 역할을 설명합니다. 실제 웹 화면은 별도 저장소인 [TayoTayo-web](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-web)에 있고, Android 앱도 이 서버 저장소와 별도로 관리됩니다. 서버는 두 클라이언트가 호출하는 HTTP API와 MySQL 데이터 처리를 담당합니다.

### 저장소 구성

```text
TayoTayo-server/
├─ README.md                         실행·구성 안내
├─ TayoTayo_실행용_회의반영.sql      MySQL 스키마와 데모 시드
├─ TayoTayo_회의결정_반영_ERDCloud.sql ERDCloud용 데이터 모델
├─ TayoTayo_OpenAPI_3.0.json         API 정의 파일
└─ backend/
   ├─ start-server.bat               Windows 실행 시작점
   ├─ run-server.ps1                 접속 설정, 빌드, 서버 구동
   ├─ build.gradle.kts               Java·Spring 의존성 및 빌드 설정
   └─ src/main/
      ├─ java/com/tayotayo/backend/  API 구현
      └─ resources/                  설정과 정적 API 문서
```

`backend/.secrets/server.properties`는 첫 실행 때 생성하는 로컬 설정 파일입니다. DB 비밀번호와 Firebase 설정을 담으며 저장소에 올리지 않습니다. Gradle의 `.gradle/`, 빌드 산출물 `build/`도 실행 과정에서 생성됩니다.

### 서버 기술과 요청 구조

- Java 21과 Spring Boot 4.1.2를 사용합니다. API는 Spring MVC 컨트롤러가 HTTP 요청을 받고, 서비스·저장소 또는 `JdbcTemplate`으로 MySQL을 읽고 씁니다.
- DB 연결은 `backend/src/main/resources/application.yml`의 환경 변수 기본값을 사용합니다. 기본 DB는 `tayotayo_dev`, 주소는 로컬 MySQL `127.0.0.1:3306`입니다.
- API 경로의 기본 접두어는 `/api/v1`입니다. Swagger/OpenAPI 접속, 인증, API 영역과 호출 방법은 [Swagger 사용 안내](./SWAGGER.md)를 참고하세요.
- `/health`는 서버 응답 여부, `/health/database`는 DB 연결 확인용입니다.
- 스키마는 서버가 자동 생성하거나 변경하지 않습니다. 초기 SQL 또는 별도 DB 변경 작업으로 테이블을 준비해야 합니다.

### Java 코드 영역

| 폴더 | 구현 역할 |
| --- | --- |
| `auth` | 이메일 인증 코드 발급·확인, 회원가입·로그인, access/refresh token, 내 계정·동의 정보 |
| `orgs` | 기관 생성과 조회·수정, 로그인 사용자의 기관 목록 |
| `management` | 기관의 원아·정류장·노선 CRUD, 정류장 순서 설정, 직원 목록, 기기 푸시 토큰 등록 |
| `vehicles` | 차량 생성·조회·수정, 차량 페어링 코드 발급 및 기기 연결 |
| `children` | 원아의 노선·차량·정류장·요일별 배정, 보호자 연결 조회·해제 |
| `invitations` | 교직원 초대와 수락, 원아 보호자 초대와 수락 |
| `trips` | 날짜별 운행 생성·조회, 출발·복귀·종료·취소, 정류장과 원아 스냅샷, 운행 이벤트 |
| `operations` | 운행 경로 순서 변경 이력, 기관 인계 기록 조회 |
| `telemetry` | 차량 위치 기록과 내비게이션 ETA 스냅샷·정류장별 ETA 저장 |
| `guardian` | 보호자 본인에게 연결된 원아의 오늘 운행·이력·상세 조회 |
| `attendance` | 보호자의 결석·미탑승·정류장 변경 등 요청과 기관의 승인·반려 |
| `pickups` | 보호자 지정 인계자 등록, 기관 검토, 날짜별 픽업 대상 관리 |
| `sharing` | 제한된 운행 공유 링크, 링크 철회, 보호자 응답 전화 요청 |
| `announcements` | 기관 공지와 보호자 알림 목록·읽음 처리 |
| `notifications` | 알림 생성·중복 방지와 Firebase Cloud Messaging 전송, FCM 드라이런 |
| `config` | OpenAPI/Swagger 문서 구성 |

공통 진입 클래스는 `TayoTayoBackendApplication.java`입니다. `ApiAuthorizationInterceptor`와 `AccessTokenStore`가 API 사용자 인증을 보조하고, 차량에서 오는 요청은 `X-Vehicle-Credential` 헤더를 확인합니다.

### 주요 데이터 흐름

#### 계정과 기관

1. `POST /api/v1/auth/email-verifications`가 개발용 인증 코드를 발급합니다. 현재 실제 이메일 발송 연결은 없습니다.
2. 인증 확인 후 `POST /api/v1/auth/signup`으로 계정을 만듭니다.
3. `POST /api/v1/auth/login`은 `ADMIN_WEB`, `GUARDIAN_APP`, `TEACHER_APP` 클라이언트 유형을 받아 토큰을 돌려줍니다.
4. 로그인 계정이 기관을 생성하면 해당 계정은 기관의 `OWNER`로 등록됩니다.
5. 이후 기관 소속 권한과 아이 연결 여부를 기준으로 데이터 범위를 확인합니다.

비밀번호는 임의 salt를 사용한 PBKDF2-HMAC-SHA256(120,000회) 형식으로 저장됩니다. access token은 현재 개발용 메모리 저장소라 서버를 재시작하면 기존 access token이 만료됩니다. refresh token은 해시한 값으로 DB 세션에 저장됩니다.

#### 원아·정류장·노선 배정

기관 관리자는 원아와 차량, 위치 좌표가 있는 정류장을 등록하고 등원(`PICK_UP`)·하원(`DROP_OFF`) 노선을 구성합니다. 노선은 차량과 출발 시각 및 순서가 정해진 정류장 목록을 가집니다. 원아 배정은 노선·차량·정류장·요일·적용 기간을 연결합니다. 운행 생성 시 선택 날짜에 유효한 배정만 운행 원아 명단에 포함됩니다.

#### 운행 및 차량 앱

1. 기관이 차량을 등록하고 6자리 페어링 코드를 발급합니다. 기본 코드는 10분 후 만료합니다.
2. 차량 앱이 `POST /api/v1/vehicle/pair`에 코드를 보내면 서버는 차량 기기를 등록하고 평문 credential을 한 번 반환합니다. DB에는 credential의 SHA-256만 저장합니다.
3. 페어링된 차량으로 날짜·노선을 선택해 운행을 생성합니다. 운행에는 당시 정류장 순서와 원아 정보를 복사해 기록합니다.
4. 차량 앱은 credential을 `X-Vehicle-Credential` 헤더에 넣어 오늘 운행을 가져오고, 출발·정류장 도착·완료·복귀와 탑승/하차 이벤트를 보냅니다.
5. 운행 상태는 준비(`PREPARING`) → 운행 중(`IN_PROGRESS`) → 복귀(`RETURNING`) → 완료(`COMPLETED`) 흐름을 가집니다. 준비 상태에서 취소하면 `CANCELLED`가 됩니다.
6. 위치 API는 좌표·시각·속도·기어·출처를 기록합니다. ETA는 별도 스냅샷과 정류장 항목으로 보관합니다. 경로 안내와 ETA 수신이 출결 기록을 자동으로 대신하지 않습니다.

#### 보호자 기능과 알림

보호자 초대는 기관이 이메일·관계를 지정해 발급하고, 초대 대상자가 계정을 만든 후 코드를 수락해야 아이와 연결됩니다. 보호자 API는 연결된 자녀 범위에서 운행 진행, 출결 요청, 지정 인계자, 일별 픽업 대상, 공유 링크, 알림을 처리합니다. 공유 링크는 토큰을 해시해 저장하고 보호자가 취소할 수 있습니다. 알림은 DB에 먼저 기록하며 Firebase 설정과 실제 기기 토큰이 있을 때 FCM으로 전달합니다.

### 데이터베이스 구성

실행용 SQL은 MySQL 8.0.13 이상을 대상으로 `tayotayo_dev` 데이터베이스와 외래 키를 포함한 테이블을 만듭니다. 테이블별 역할과 관계, 시드 없는 초기화, 연결 설정과 백업 주의사항은 [데이터베이스 안내](./DATABASE.md)를 참고하세요. 주요 데이터 묶음은 다음과 같습니다.

- **기관 운영:** `organizations`, `org_staff`, `vehicles`, `vehicle_devices`, `stops`, `routes`, `route_stops`
- **계정·관계:** `users`, `auth_sessions`, `email_verifications`, `staff_invitations`, `guardian_invitations`, `child_guardians`, `user_consents`
- **원아 배정:** `children`, `child_route_assignments`, `authorized_pickup_persons`, `daily_pickups`, `attendance_requests`
- **운행 기록:** `trips`, `trip_stops`, `trip_children`, `trip_events`, `trip_route_revisions`, `trip_checklist_items`, `org_handover_records`
- **위치·알림:** `trip_location_samples`, `trip_eta_snapshots`, `trip_eta_entries`, `notifications`, `notification_deliveries`, `push_devices`, `share_links`, `guardian_call_requests`, `org_announcements`
- **정책:** `policy_documents`

운행과 인계 이력은 과거 상태를 보존하도록 외래 키와 삭제 제한을 둡니다. 기관·정류장·노선·원아는 일부 항목에서 상태 변경이나 soft delete를 사용합니다. 테이블 정의 및 참조 관계의 기준은 실행용 SQL이며, ERDCloud SQL은 시각화/모델 가져오기에 쓰는 별도 산출물입니다.

### 설정 파일과 외부 연동

| 설정 | 역할 | 기본/주의 |
| --- | --- | --- |
| `DB_URL` | JDBC 접속 주소 | `jdbc:mysql://127.0.0.1:3306/tayotayo_dev?...` |
| `DB_USERNAME`, `DB_PASSWORD` | MySQL 로그인 | `start-server.bat` 첫 실행 때 입력해 로컬 파일에 저장 |
| `FIREBASE_ENABLED` | FCM 전송 사용 여부 | 기본 `false` |
| `FIREBASE_CREDENTIALS_PATH` | Firebase Admin 서비스 계정 JSON 경로 | 저장소 바깥의 키 파일을 지정 |
| `FIREBASE_PROJECT_ID` | Firebase 프로젝트 ID | 서비스 계정의 `project_id`와 일치해야 함 |
| `FIREBASE_TEST_ENDPOINT_ENABLED` | 개발용 FCM 드라이런 API | 기본 `false`; 확인 후 꺼 두기 |

서버는 Kakao 지도 검색을 직접 제공하지 않습니다. 관리자 웹이 위치 검색을 중계하며, Kakao REST 키 설정과 검색 동작은 웹 저장소 설명을 따릅니다. Firebase 실제 전송에는 같은 프로젝트의 앱 토큰과 서버 서비스 계정이 필요합니다. 서비스 계정 JSON이나 실제 토큰·비밀번호를 Git에 넣지 마세요.

### 현재 구현 범위와 유의점

- 이 저장소는 API 백엔드이며 관리자 웹·보호자 앱의 화면 소스는 포함하지 않습니다.
- 인증 코드 응답에 `debugCode`가 포함되는 것은 메일 서버가 연결되지 않은 개발 설정입니다. 운영 인증 수단으로 사용하지 마세요.
- access token 메모리 저장소, 개발용 초기 비밀번호/데모 credential, 선택적 FCM 설정은 로컬 개발을 위한 것입니다. 공개 운영 서비스로 배포하기 전 인증·비밀값·HTTPS·권한 정책을 별도 점검해야 합니다.
- 위치와 ETA는 차량 앱이 제출한 값과 원천·시각을 기록합니다. 서버가 내비 경로를 계산하거나 차량의 안전 상태를 판정하는 기능으로 간주하면 안 됩니다.
- 기존 DB에 실행용 전체 SQL을 다시 돌리는 방식으로 버전을 올리지 마세요. 데이터가 있는 환경은 백업 후 별도 migration 계획을 사용해야 합니다.
## 상세 사용 방법

이 절은 서버 API를 실제 서비스 흐름에 맞춰 사용할 때의 순서입니다. 관리자 웹에서 수행하는 방법은 [TayoTayo-web README](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-web), Swagger의 요청 필드와 응답 형식은 [Swagger 안내](./SWAGGER.md), 테이블과 SQL 사용법은 [DB 안내](./DATABASE.md)를 함께 참고하세요.

### 관리자 운영 흐름

1. **계정과 기관:** 관리자가 계정을 만들고 로그인한 뒤 기관을 등록합니다. 첫 기관을 만든 사용자가 해당 기관의 소유자 관리자가 됩니다.
2. **기초 데이터:** 기관에 차량과 좌표가 있는 정류장을 등록합니다. 정류장은 주소만이 아니라 지도 검색 결과의 위도·경도까지 선택해야 합니다.
3. **노선 구성:** 차량, 운행 방향, 출발 시간, 정류장 방문 순서를 정해 노선을 만듭니다. 등원은 `PICK_UP`, 하원은 `DROP_OFF`입니다.
4. **원아 배정:** 원아를 등록하고 노선·차량·정류장과 운행 요일·적용 기간을 연결합니다. 운행 예정일이 요일과 기간에 포함되어야 명단에 들어갑니다.
5. **기기 연결:** 차량 페어링 코드를 발급해 차량 앱을 연결합니다. 보호자에게는 원아별 초대 코드를 발급하고, 보호자가 자기 계정을 만든 뒤 해당 아이와 연결합니다.
6. **운행 생성:** 날짜와 노선을 선택해 운행을 만듭니다. 생성 시 그 날짜에 유효한 원아 배정과 노선 정류장 순서가 운행 기록으로 복사됩니다.
7. **운행 처리:** 차량 앱이 오늘 운행을 불러와 준비·출발·정류장 도착·원아 이벤트·복귀·종료를 순서대로 기록합니다. 상태가 맞지 않는 요청은 서버가 거절할 수 있으니 작업 뒤 운행 조회 API로 최신 상태를 확인합니다.

### Swagger로 API 흐름을 확인하기

1. 서버와 DB를 실행하고 `/health/database`가 정상인지 확인합니다.
2. `/swagger-ui.html`을 열고 계정을 생성하거나 기존 개발 계정으로 로그인합니다. 개발 가입 코드는 이메일로 보내지 않고 인증 요청 응답의 `debugCode`에서 확인합니다.
3. **Authorize**에 로그인 `accessToken`을 등록한 뒤 기관·차량·정류장·노선·원아 관리 API를 호출합니다.
4. 차량 API를 시험할 때는 관리자 인증으로 페어링 코드를 발급하고 `/vehicle/pair`를 호출합니다. 응답의 `vehicleCredential`을 차량 인증으로 등록합니다.
5. 리소스 생성 응답에서 ID를 복사해 다음 요청에 사용합니다. 실제 데이터를 바꾸는 POST/PUT/PATCH/DELETE 요청은 개발 DB에서만 시험합니다.

주소, 인증 헤더, 대표 API, 오류 코드는 [Swagger / OpenAPI 사용 안내](./SWAGGER.md)에 설명되어 있습니다.

### DB에서 결과 확인하기

Workbench에서 `tayotayo_dev`를 선택해 `SHOW TABLES;`로 테이블을 확인합니다. 기관, 노선, 원아, 운행을 조회할 때는 [데이터베이스 안내](./DATABASE.md)의 예시처럼 필요한 컬럼과 날짜를 제한해 조회하세요. 운행 기준 정보와 당시 복사된 스냅샷은 서로 다른 테이블에 있습니다. 현재 노선 배정과 과거 운행 명단이 달라 보여도 과거 운행의 스냅샷은 생성 당시 내용을 보존하기 위한 것입니다.

운행·출결·보호자 연결을 확인하려고 DB를 직접 수정하지 마세요. API가 상태 전이와 권한을 확인하도록 하고, 이력 데이터는 삭제하지 말고 서버 기능으로 취소·철회·비활성화하세요.

