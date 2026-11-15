# Swagger / OpenAPI 사용 안내

## 접속 주소

백엔드를 실행한 뒤 아래 주소를 엽니다.

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/openapi.json](http://localhost:8080/openapi.json)
- 저장소의 API 정의: [`TayoTayo_OpenAPI_3.0.json`](../TayoTayo_OpenAPI_3.0.json)

Swagger UI는 `backend/src/main/resources/static/openapi.json`을 읽도록 설정되어 있습니다. 따라서 저장소 루트의 OpenAPI JSON을 수정했다면 정적 파일에도 같은 변경을 반영해야 실제 서버 문서와 일치합니다. `/v3/api-docs`는 Springdoc 생성 문서가 필요한 경우 사용할 수 있지만, 현재 UI의 기준 파일은 `/openapi.json`입니다.

먼저 [`/health`](http://localhost:8080/health)와 [`/health/database`](http://localhost:8080/health/database)를 열어 서버 및 DB 연결을 확인하세요.

## Swagger 화면에서 API 호출하기

1. 화면 왼쪽에서 태그를 선택하거나 검색해 API를 찾습니다.
2. 요청을 열고 **Try it out**을 누릅니다.
3. 경로의 `{orgId}`, `{childId}`, `{tripId}` 같은 값은 실제 응답에서 받은 ID로 바꿉니다.
4. 요청 본문이 표시되면 예시 JSON을 실제 값으로 수정합니다.
5. **Execute**를 누르고 HTTP 상태 코드와 응답 본문을 확인합니다.
6. 생성 요청의 응답 ID를 기록해 다음 API 경로에 사용합니다.

목록 응답은 보통 `items`, `nextCursor`, `hasMore` 형태의 페이지 정보가 포함됩니다. 기관/원아/운행 ID는 임의로 입력하지 말고 앞선 생성·조회 응답을 사용하세요.

## 로그인과 인증

### 관리자·보호자·교직원 계정

개발 환경에서는 이메일 인증 메일이 전송되지 않습니다.

1. `POST /api/v1/auth/email-verifications`에 이메일과 `purpose: "SIGN_UP"`을 보내고 응답의 `debugCode`를 받습니다.
2. `POST /api/v1/auth/signup`에 이메일, 이름, 8자 이상 비밀번호, `verificationCode`를 보냅니다.
3. `POST /api/v1/auth/login`에 계정 정보를 보냅니다. `clientType`은 `ADMIN_WEB`, `GUARDIAN_APP`, `TEACHER_APP` 중 해당 클라이언트 유형을 사용합니다.
4. 응답의 `accessToken`을 Swagger UI 상단 **Authorize**에 입력합니다. 문서의 Bearer 인증은 `Authorization: Bearer <accessToken>` 헤더를 사용합니다. 입력창이 접두어를 자동으로 붙이는지 확인하고 `Bearer`를 중복 입력하지 마세요.

로그인·기관 생성 후 `GET /api/v1/orgs`에서 접근 가능한 기관 ID를 확인합니다. 보호자 API는 보호자 본인 계정과 연결된 아이만 조회할 수 있습니다.

### 차량 기기 인증

차량 API는 사용자 Bearer token 대신 `X-Vehicle-Credential`을 씁니다.

1. 관리자 인증으로 `POST /api/v1/orgs/{orgId}/vehicles/{vehicleId}/pairing-codes`를 호출합니다.
2. 6자리 코드를 차량 앱에서 `POST /api/v1/vehicle/pair`에 전달합니다. 기본 만료는 10분입니다.
3. 응답의 `vehicleCredential`은 안전하게 보관합니다. 이후 차량용 API의 **Authorize** 또는 헤더 입력란에 `X-Vehicle-Credential` 값으로 사용합니다. 서버 DB에는 credential 원문 대신 SHA-256 해시가 저장됩니다.

페어링 코드는 발급된 차량에 기기를 연결하는 일회성 자격이며 API credential과 서로 다릅니다. 실제 `vehicleCredential`은 한 번 반환되므로 로그나 저장소에 올리지 마세요.

## API 영역 안내

| 기능 | 대표 경로 | 인증 |
| --- | --- | --- |
| 회원가입·로그인·계정 | `/api/v1/auth/*`, `/api/v1/me` | 사용자 인증 일부 제외 |
| 기관·차량·원아·정류장·노선 관리 | `/api/v1/orgs/*` | 관리자/직원 사용자 인증 |
| 직원·보호자 초대 | `/api/v1/orgs/*/staff-invitations`, `/api/v1/orgs/*/children/*/guardian-invitations` | 사용자 인증 |
| 차량 페어링·운행 | `/api/v1/vehicle/*`, `/api/v1/trips/*` | 차량 credential |
| 운행 생성·조회·취소 | `/api/v1/orgs/{orgId}/trips`, `/api/v1/trips/{tripId}` | 기관 사용자 인증 |
| 위치·ETA 업로드 | `/api/v1/vehicle/trips/{tripId}/locations`, `/eta-snapshots` | 차량 credential |
| 보호자 운행·출결·픽업 | `/api/v1/guardian/*`, `/api/v1/guardian/children/*` | 연결된 보호자 사용자 인증 |
| 공지·알림·공유 | `/api/v1/guardian/announcements`, `/notifications`, `/share/*` | API별 인증 또는 공유 토큰 |
| 상태 확인 | `/health`, `/health/database` | 공개 상태 확인 |

정확한 요청 필드·응답 스키마·상태 코드는 Swagger의 현재 명세와 `TayoTayo_OpenAPI_3.0.json`을 기준으로 확인하세요. 명세에 정의되지 않았거나 코드와 불일치하는 항목은 실행 중인 서버 응답을 우선 점검하고 OpenAPI 파일을 함께 갱신해야 합니다.

## 오류를 확인할 때

- `400`: 필수 필드, enum 값, 날짜 형식(`YYYY-MM-DD`), UUID, 요청 간 선행 조건을 확인합니다.
- `401`: access token 만료/누락 또는 차량 credential이 누락·잘못됐는지 확인합니다. 사용자 token과 차량 credential을 바꿔 넣지 마세요.
- `403`: 로그인 계정의 기관 권한이나 원아 보호자 연결 범위를 확인합니다.
- `404`: ID 또는 페어링/초대 코드가 틀렸거나 만료되지 않았는지 확인합니다.
- `409`: 이미 처리된 상태이거나 필요한 선행 상태가 충족되지 않았습니다. 최신 리소스를 다시 조회합니다.
- DB 연결 오류: `/health/database`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, MySQL 실행 상태를 점검합니다.

Swagger에서 운행을 변경하는 요청은 실제 저장 데이터에 반영됩니다. 개발용 DB와 테스트 계정으로만 호출하고, 문·차량 제어가 아닌 서버 기록 API라고 해도 실제 사용자의 개인정보·운행 이력을 함부로 바꾸지 마세요.

## 전체 API 목록

아래 목록은 TayoTayo_OpenAPI_3.0.json의 각 operation을 그대로 분류했습니다. 요청·응답 필드와 예시는 각 항목을 Swagger에서 펼쳐 확인하세요. 명세의 server base path는 /api/v1입니다.

| 태그 | 메서드·경로 | 설명 |
| --- | --- | --- |
| 개발 확인 | `POST /api/v1/dev/fcm/dry-run` | FCM 연결 드라이런 검증 (개발 전용) |
| 인증·계정 | `POST /api/v1/auth/email-verifications` | 이메일 인증 코드 발송 |
| 인증·계정 | `POST /api/v1/auth/email-verifications/confirm` | 이메일 인증 확인 |
| 인증·계정 | `POST /api/v1/auth/signup` | 사용자 가입 |
| 인증·계정 | `POST /api/v1/auth/login` | 로그인 및 세션 발급 |
| 인증·계정 | `POST /api/v1/auth/refresh` | 세션 갱신 |
| 인증·계정 | `POST /api/v1/auth/logout` | 현재 세션 로그아웃 |
| 인증·계정 | `GET /api/v1/me` | 내 계정 및 기관 조회 |
| 인증·계정 | `PATCH /api/v1/me` | 내 계정 수정 |
| 인증·계정 | `GET /api/v1/policies` | 유효 약관 및 정책 목록 |
| 인증·계정 | `PUT /api/v1/me/consents` | 동의 이력 저장 |
| 기관·직원 | `POST /api/v1/orgs` | 기관 생성 |
| 기관·직원 | `GET /api/v1/orgs` | 내 기관 목록 |
| 기관·직원 | `GET /api/v1/orgs/{orgId}` | 기관 상세 |
| 기관·직원 | `PATCH /api/v1/orgs/{orgId}` | 기관 정보 수정 |
| 기관·직원 | `GET /api/v1/orgs/{orgId}/staff` | 직원 목록 |
| 기관·직원 | `PATCH /api/v1/orgs/{orgId}/staff/{staffId}` | 직원 권한 및 상태 수정 |
| 기관·직원 | `POST /api/v1/orgs/{orgId}/staff-invitations` | 직원 초대 발급 |
| 기관·직원 | `POST /api/v1/staff-invitations/accept` | 직원 초대 수락 |
| 차량·기기 | `GET /api/v1/orgs/{orgId}/vehicles` | 차량 목록 |
| 차량·기기 | `POST /api/v1/orgs/{orgId}/vehicles` | 차량 등록 |
| 차량·기기 | `GET /api/v1/orgs/{orgId}/vehicles/{vehicleId}` | 차량 상세 |
| 차량·기기 | `PATCH /api/v1/orgs/{orgId}/vehicles/{vehicleId}` | 차량 정보 수정 |
| 차량·기기 | `POST /api/v1/orgs/{orgId}/vehicles/{vehicleId}/pairing-codes` | 페어링 코드 발급 |
| 차량·기기 | `POST /api/v1/vehicle/pair` | 차량 앱 페어링 |
| 차량·기기 | `POST /api/v1/vehicle/session/revoke` | 차량 기기 연결 해제 |
| 차량·기기 | `POST /api/v1/me/push-devices` | 푸시 기기 등록 또는 갱신 |
| 차량·기기 | `DELETE /api/v1/me/push-devices/{deviceId}` | 푸시 기기 폐기 |
| 아이·노선 | `GET /api/v1/orgs/{orgId}/children` | 아이 목록 |
| 아이·노선 | `POST /api/v1/orgs/{orgId}/children` | 아이 등록 |
| 아이·노선 | `GET /api/v1/orgs/{orgId}/children/{childId}` | 아이 상세 |
| 아이·노선 | `PATCH /api/v1/orgs/{orgId}/children/{childId}` | 아이 정보 수정 |
| 아이·노선 | `GET /api/v1/orgs/{orgId}/stops` | 정류장 목록 |
| 아이·노선 | `POST /api/v1/orgs/{orgId}/stops` | 정류장 등록 |
| 아이·노선 | `PATCH /api/v1/orgs/{orgId}/stops/{stopId}` | 정류장 수정 또는 비활성화 |
| 아이·노선 | `GET /api/v1/orgs/{orgId}/routes` | 노선 목록 |
| 아이·노선 | `POST /api/v1/orgs/{orgId}/routes` | 노선 등록 |
| 아이·노선 | `GET /api/v1/orgs/{orgId}/routes/{routeId}` | 노선 상세 |
| 아이·노선 | `PATCH /api/v1/orgs/{orgId}/routes/{routeId}` | 노선 수정 |
| 아이·노선 | `PUT /api/v1/orgs/{orgId}/routes/{routeId}/stops` | 노선 정류장 순서 저장 |
| 아이·노선 | `GET /api/v1/orgs/{orgId}/children/{childId}/route-assignments` | 아이 배정 이력 |
| 아이·노선 | `PUT /api/v1/orgs/{orgId}/children/{childId}/route-assignments` | 아이 차량·노선·정류장 배정 |
| 아이·노선 | `POST /api/v1/orgs/{orgId}/children/{childId}/guardian-invitations` | 보호자 초대 발급 |
| 아이·노선 | `POST /api/v1/guardian-invitations/accept` | 보호자 초대 수락 |
| 아이·노선 | `GET /api/v1/me/children` | 연결된 아이 목록 |
| 아이·노선 | `DELETE /api/v1/orgs/{orgId}/children/{childId}/guardians/{userId}` | 보호자 연결 폐기 |
| 보호자 요청 | `GET /api/v1/guardian/children/{childId}/attendance-requests` | 아이 요청 목록 |
| 보호자 요청 | `POST /api/v1/guardian/children/{childId}/attendance-requests` | 출결 및 하원 요청 등록 |
| 보호자 요청 | `GET /api/v1/orgs/{orgId}/attendance-requests` | 기관 요청함 |
| 보호자 요청 | `POST /api/v1/orgs/{orgId}/attendance-requests/{requestId}/review` | 요청 승인 또는 반려 |
| 보호자 요청 | `POST /api/v1/guardian/attendance-requests/{requestId}/cancel` | 대기 요청 취소 |
| 보호자 요청 | `GET /api/v1/guardian/children/{childId}/authorized-pickup-persons` | 대리 픽업 인원 목록 |
| 보호자 요청 | `POST /api/v1/guardian/children/{childId}/authorized-pickup-persons` | 대리 픽업 인원 등록 신청 |
| 보호자 요청 | `GET /api/v1/orgs/{orgId}/authorized-pickup-persons` | 기관 대리 픽업 신청 목록 |
| 보호자 요청 | `POST /api/v1/orgs/{orgId}/authorized-pickup-persons/{personId}/review` | 대리 픽업 등록 심사 |
| 운행 | `GET /api/v1/vehicle/trips` | 차량 당일 운행 |
| 운행 | `POST /api/v1/orgs/{orgId}/trips` | 운행 생성 및 아이·정류장 스냅샷 구성 |
| 운행 | `GET /api/v1/trips/{tripId}` | 운행 상세 및 상태 |
| 운행 | `POST /api/v1/trips/{tripId}/prepare` | 운행 준비 완료 |
| 운행 | `PUT /api/v1/trips/{tripId}/checklist` | 운행 체크리스트 저장 |
| 운행 | `POST /api/v1/trips/{tripId}/start` | 운행 출발 |
| 운행 | `POST /api/v1/trips/{tripId}/stops/{tripStopId}/arrive` | 정류장 도착 기록 |
| 운행 | `POST /api/v1/trips/{tripId}/stops/{tripStopId}/complete` | 정류장 완료 |
| 운행 | `POST /api/v1/trips/{tripId}/stops/{tripStopId}/skip` | 정류장 건너뛰기 및 revision 기록 |
| 운행 | `POST /api/v1/trips/{tripId}/return` | 기관 복귀 시작 |
| 운행 | `POST /api/v1/trips/{tripId}/interior-check` | 차량 내부 확인 |
| 운행 | `POST /api/v1/trips/{tripId}/complete` | 운행 종료 |
| 운행 | `POST /api/v1/trips/{tripId}/cancel` | 운행 취소 |
| 운행 | `POST /api/v1/trips/{tripId}/route-revisions` | 노선 revision 추가 |
| 차량 앱 수집 | `POST /api/v1/trips/{tripId}/events` | 운행 이벤트 수집 |
| 차량 앱 수집 | `POST /api/v1/trips/{tripId}/events/batch` | 오프라인 이벤트 동기화 |
| 차량 앱 수집 | `POST /api/v1/vehicle/trips/{tripId}/locations` | 위치 표본 묶음 업로드 |
| 차량 앱 수집 | `POST /api/v1/vehicle/trips/{tripId}/eta-snapshots` | ETA 묶음 업로드 |
| 보호자 조회 | `GET /api/v1/guardian/children/{childId}/today` | 오늘 내 아이 등원 또는 하원 상태 |
| 보호자 조회 | `GET /api/v1/guardian/children/{childId}/trips` | 아이 과거 운행 |
| 보호자 조회 | `GET /api/v1/guardian/trips/{tripId}/children/{childId}` | 아이 운행 진행 상세 |
| 보호자 조회 | `GET /api/v1/guardian/notifications` | 내 알림 목록 |
| 보호자 조회 | `POST /api/v1/guardian/notifications/{notificationId}/read` | 알림 읽음 처리 |
| 보호자 조회 | `POST /api/v1/guardian/children/{childId}/trips/{tripId}/share-links` | 읽기 전용 공유 링크 생성 |
| 보호자 조회 | `GET /api/v1/share/{token}` | 공유 링크로 운행 요약 확인 |
| 보호자 조회 | `POST /api/v1/guardian/share-links/{shareLinkId}/revoke` | 공유 링크 폐기 |
| 공지·운영 | `GET /api/v1/orgs/{orgId}/announcements` | 기관 공지 목록 |
| 공지·운영 | `POST /api/v1/orgs/{orgId}/announcements` | 기관 공지 작성 |
| 공지·운영 | `GET /api/v1/guardian/announcements` | 보호자 대상 공지 목록 |
| 공지·운영 | `GET /api/v1/orgs/{orgId}/handover-records` | 기관 인계 후속 처리 목록 |
| 공지·운영 | `PATCH /api/v1/orgs/{orgId}/handover-records/{recordId}` | 인계 연락 및 해결 기록 |
| 차량 앱 수집 | `POST /api/v1/trips/{tripId}/children/{tripChildId}/guardian-calls` | 등원 미탑승 보호자 호출 |
| 보호자 요청 | `POST /api/v1/guardian/calls/{callId}/response` | 등원 보호자 호출 응답 |
| 보호자 요청 | `GET /api/v1/guardian/children/{childId}/daily-pickups` | 날짜별 픽업 담당자 조회 |
| 보호자 요청 | `PUT /api/v1/guardian/children/{childId}/daily-pickups` | 날짜별 픽업 담당자 지정 |

## 인증 흐름별 요청 본문 예시

### 이메일 인증·가입·로그인

`POST /api/v1/auth/email-verifications`

```json
{"email":"admin@example.com","purpose":"SIGN_UP"}
```

개발 응답의 `debugCode`를 받아 다음 확인 요청에 넣습니다.

```json
{"email":"admin@example.com","purpose":"SIGN_UP","code":"123456"}
```

`POST /api/v1/auth/signup`:

```json
{"email":"admin@example.com","name":"기관 관리자","password":"change-me-123","verificationCode":"123456"}
```

`POST /api/v1/auth/login`:

```json
{"email":"admin@example.com","password":"change-me-123","clientType":"ADMIN_WEB","deviceLabel":"Swagger"}
```

인증 코드 유효시간은 10분입니다. 가입 과정은 유효한 `SIGN_UP` 인증을 요구합니다. 계정 비밀번호 길이는 8~100자이며, 인증 코드는 실제 메일이 아니라 개발 응답에 표시됩니다. 로그인 성공 응답에서 `accessToken`, `refreshToken`, `expiresIn`, `user`를 확인합니다. 서버 재시작 시 메모리 access token이 초기화될 수 있습니다.

### 기관 자원 생성 흐름

권장 호출 순서는 `POST /orgs` → 차량 생성 → 정류장 생성 → 노선 생성 → 노선 정류장 설정 → 원아 생성 → 원아 노선 배정 → 차량 페어링 → 날짜별 운행 생성입니다. 모든 ID는 해당 응답에서 복사합니다.

노선 방향 enum은 `PICK_UP`(등원), `DROP_OFF`(하원)입니다. 노선 본문에는 차량 ID, 이름, 방향, 출발 시각이 필요하고, 정류장 순서는 `PUT /orgs/{orgId}/routes/{routeId}/stops`의 `stops` 배열에 `stopId`와 `sequence`로 지정합니다. 원아 배정은 route/vehicle/stop, 요일 bitmask, 시작일 및 선택적 종료일을 사용합니다. 적용 날짜가 운행일과 요일에 맞아야 운행 명단에 반영됩니다.

### 차량 페어링·운행

페어링 코드는 기본 600초 동안 유효하며 발급 시 60~1800초로 조정할 수 있습니다. 코드는 6자리 숫자이고 한 번 사용하면 재사용할 수 없습니다. `/vehicle/pair` 성공 응답에 포함된 credential 원문은 저장소나 일반 로그에 기록하지 말고 안전하게 차량 앱에 전달하세요.

운행 생성은 기관 사용자 인증을 요구하며 `clientTripId` UUID, 차량 ID, 서비스 날짜, 방향 및 선택적 노선 ID를 받습니다. 차량 연결 기기가 배정되어야 차량 운행 API를 사용할 수 있습니다. 운행 상태 전이는 현재 상태에 따라 제한되며, 잘못된 순서·중복 상태 변경은 보통 `409 Conflict`가 됩니다. 노선/원아 스냅샷은 생성 시점의 데이터를 유지합니다.

### 위치·ETA payload의 의미

차량 위치 endpoint는 운행에 묶인 위치 샘플 묶음을 받고 좌표·측정/수신 시각·속도·방향·정확도·기어·source를 저장합니다. source는 `PLEOS` 또는 `GPS`입니다. ETA endpoint는 하나의 수신 snapshot과 여러 `WAYPOINT`/`DESTINATION` 항목을 저장하며, 검증 상태를 함께 보관합니다. 서버는 SDK/내비 원천 값의 진위를 자체적으로 계산해 보증하지 않으며 위치 수신을 탑승·하차 이벤트로 바꾸지 않습니다.

### 사용자·기기 범위

- 기관 사용자 요청은 `Authorization: Bearer ...`로 인증합니다.
- 차량 요청은 `X-Vehicle-Credential`로 인증하고 연결 기기의 운행만 접근해야 합니다.
- 보호자 API는 초대 수락으로 연결된 자녀를 기준으로 범위를 제한합니다.
- 공유 링크 조회는 일반 계정 token 대신 무작위 공유 token을 사용하며 보호자가 링크를 철회할 수 있습니다.
- Firebase 드라이런은 `FIREBASE_TEST_ENDPOINT_ENABLED=true`일 때만 노출되며 Firebase가 요청 형식을 검증할 뿐 실제 기기에 알림을 전송하지 않습니다.

## OpenAPI 요청·응답 스키마 색인

아래는 명세의 components.schemas에 선언된 객체와 속성 색인입니다. 필드 형식, 필수 여부, enum, 예시 값은 Swagger UI의 schema/example 패널에서 확인합니다.

### `Error`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `error` | object |  |  |

### `Resource`

### `Page`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `items` | array |  |  |
| `nextCursor` | string |  |  |

### `Input`
허용 필드는 API 구현 시 엔드포인트별 검증

### `EmailVerification`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | 예 |  |
| `purpose` | string | 예 |  |

### `EmailCode`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | 예 |  |
| `purpose` | string | 예 |  |
| `code` | string | 예 |  |

### `Signup`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | 예 |  |
| `password` | string | 예 |  |
| `name` | string | 예 |  |
| `verificationCode` | string | 예 |  |
| `consents` | array |  |  |

### `Login`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | 예 |  |
| `password` | string | 예 |  |
| `clientType` | string | 예 |  |
| `deviceLabel` | string |  |  |

### `Tokens`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `accessToken` | string |  |  |
| `refreshToken` | string |  |  |
| `expiresIn` | integer |  |  |
| `user` | #/components/schemas/Resource |  |  |

### `Pair`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `code` | string | 예 |  |
| `installId` | string | 예 |  |
| `appVersion` | string |  |  |
| `pleosSdkVersion` | string |  |  |

### `TripCreate`
서버는 운행에 차량 기기를 연결하고 아이·정류장 스냅샷을 구성합니다.

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `clientTripId` | string | 예 |  |
| `vehicleId` | string | 예 |  |
| `routeId` | string |  |  |
| `serviceDate` | string | 예 |  |
| `direction` | string | 예 |  |
| `driverStaffId` | string |  |  |
| `attendantStaffId` | string |  |  |

### `Attendance`
운행 방향별 당일 변경 요청입니다. 요청이 없으면 운행 아이 명단 상태는 기본 EXPECTED(탑승 예정)입니다. NOT_RIDING은 해당 방향의 셔틀을 이용하지 않는 예외를 기록합니다. 픽업 담당자 정보는 출결 상태와 별도 데이터로 취급합니다.

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `serviceDate` | string | 예 |  |
| `direction` | string | 예 |  |
| `requestType` | string | 예 |  |
| `reasonCode` | string |  |  |
| `reasonText` | string |  |  |
| `requestedStopId` | string |  |  |
| `pickupPersonId` | string |  |  |
| `pickupGuardianUserId` | string |  |  |

### `Review`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `decision` | string | 예 |  |
| `reviewNote` | string |  |  |

### `Event`
운행·탑승·보호자 호출·인계 이력입니다. 좌석·안전벨트 상태는 서버로 전송하거나 저장하지 않습니다.

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `clientEventId` | string | 예 |  |
| `eventType` | string | 예 |  |
| `occurredAt` | string | 예 |  |
| `tripChildId` | string |  |  |
| `tripStopId` | string |  |  |
| `correctsEventId` | string |  |  |
| `latitude` | number |  |  |
| `longitude` | number |  |  |
| `payload` | object |  |  |

### `LocationBatch`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `samples` | array | 예 |  |

### `Eta`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `routeRevisionNo` | integer | 예 |  |
| `navRouteKey` | string |  |  |
| `sourceTime` | string | 예 |  |
| `validationStatus` | string | 예 |  |
| `validationNote` | string |  |  |
| `entries` | array | 예 |  |

### `GuardianToday`
해당 방향의 오늘 운행만 반환합니다. 운행 아이 명단(trip child)의 상태를 출결 현황으로 사용하고, 보호자에게는 해당 아이의 정류장과 ETA만 반환합니다.

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `childId` | string | 예 |  |
| `serviceDate` | string | 예 |  |
| `direction` | string | 예 |  |
| `tripId` | string |  |  |
| `tripStatus` | string |  |  |
| `childStatus` | string | 예 |  |
| `myStop` | object |  |  |
| `stopsBeforeMine` | integer |  |  |
| `eta` | object |  |  |
| `lastLocationAt` | string |  |  |

### `DriverCheck`
확인 시각은 서버가 기록합니다. 미확인 상태에서도 운행 출발은 허용하며 미확인 이력을 남깁니다.

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `driverConfirmed` | boolean | 예 | 음성 요약을 들은 뒤 기사가 확인했다고 교사가 체크했는지 여부 |

### `GuardianCallOpen`
등원 정류장에서 아이가 보이지 않을 때 보호자 호출을 시작합니다.

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `callType` | string | 예 |  |

### `GuardianCallResponse`
호출이 열려 있는 동안 응답을 변경할 수 있습니다. 실제 미탑승 확정은 교사가 별도 이벤트로 기록합니다.

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `response` | string | 예 |  |

### `Trip`
한 운행은 등원 또는 하원 한 방향만 나타냅니다. 좌석·벨트 정보는 포함하지 않습니다.

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | string | 예 |  |
| `serviceDate` | string | 예 |  |
| `direction` | string | 예 |  |
| `status` | string | 예 |  |
| `driverCheckedAt` | string |  |  |
| `driverConfirmationMissing` | boolean |  |  |

### `DailyPickup`
날짜별 인계 담당자이며 출결/운행 상태와 별도 관리합니다. GUARDIAN이면 pickupGuardianUserId, AUTHORIZED_PERSON이면 승인된 pickupPersonId 중 하나만 지정합니다.

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `serviceDate` | string | 예 |  |
| `direction` | string | 예 |  |
| `personType` | string | 예 |  |
| `pickupGuardianUserId` | string |  |  |
| `pickupPersonId` | string |  |  |

### `OrganizationCreate`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | string | 예 |  |
| `orgType` | string |  |  |
| `phone` | string |  |  |
| `address` | string |  |  |
| `latitude` | number |  |  |
| `longitude` | number |  |  |
| `timezone` | string |  |  |

### `OrganizationResponse`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | integer | 예 |  |
| `name` | string | 예 |  |
| `orgType` | string | 예 |  |
| `phone` | string |  |  |
| `address` | string |  |  |
| `latitude` | number |  |  |
| `longitude` | number |  |  |
| `timezone` | string | 예 |  |
| `status` | string | 예 |  |

### `VehicleCreate`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | string | 예 |  |
| `plateNumber` | string |  |  |
| `model` | string |  |  |
| `seatCapacity` | integer |  |  |

### `VehicleResponse`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | integer | 예 |  |
| `orgId` | integer | 예 |  |
| `name` | string | 예 |  |
| `plateNumber` | string |  |  |
| `model` | string |  |  |
| `seatCapacity` | integer |  |  |
| `status` | string | 예 |  |

### `VehiclePage`

| 필드 | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `items` | array | 예 |  |
| `nextCursor` | string | 예 |  |
