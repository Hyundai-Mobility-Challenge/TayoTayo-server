# TayoTayo 백엔드 API 처음 실행하기

이 문서는 처음 백엔드를 실행하는 사람이 **STS와 Swagger UI만으로 회원가입부터 운행 테스트까지** 따라 할 수 있도록 정리했습니다.

## 1. 준비물

- Java 21
- MySQL 8이 실행 중이고 `tayotayo_dev` 데이터베이스에 프로젝트 스키마가 만들어져 있어야 합니다.
- STS에 `backend` 프로젝트를 Gradle 프로젝트로 가져옵니다.
- 실행 설정에 MySQL 접속 환경 변수 `DB_USERNAME`, `DB_PASSWORD`를 등록합니다. 비밀번호를 코드나 Git에 적지 마세요.

기본 DB 주소는 `jdbc:mysql://127.0.0.1:3306/tayotayo_dev`입니다. 필요하면 `DB_URL` 환경 변수로 바꿀 수 있습니다. 서버는 DB 테이블을 자동으로 만들거나 변경하지 않습니다.

## 2. MySQL 데이터베이스와 테이블 준비

### 어떤 SQL 파일을 실행하나요?

처음 개발 DB를 준비하는 경우 프로젝트 루트에 있는 **[`TayoTayo_실행용_회의반영.sql`](../TayoTayo_실행용_회의반영.sql)**을 사용합니다. 이 파일은 회의 결정이 반영된 MySQL 전체 스키마이며, `tayotayo_dev` 데이터베이스를 선택하고 서비스가 사용하는 테이블을 만듭니다.

다른 SQL 파일은 목적이 다르므로 백엔드 개발 DB를 처음 준비할 때 섞어서 실행하지 마세요.

- `TayoTayo_실행용_회의반영.sql`: 백엔드 개발용 최신 MySQL 스키마, 대상은 `tayotayo_dev`.
- `TayoTayo_회의결정_반영_ERDCloud.sql`: ERDCloud에 가져올 데이터 모델 파일.

### MySQL Workbench에서 실행하기

1. MySQL 서버에 연결합니다.
2. **File > Open SQL Script**를 선택해 `TayoTayo_실행용_회의반영.sql`을 엽니다.
3. 파일 안에 `CREATE DATABASE ... tayotayo_dev`와 `USE tayotayo_dev`가 있는지 확인합니다.
4. 번개 모양의 **Execute** 버튼으로 스크립트 전체를 실행합니다.
5. 왼쪽 **SCHEMAS** 목록을 새로고침하고 `tayotayo_dev` 아래에 테이블이 만들어졌는지 확인합니다. 예: `organizations`, `users`, `vehicles`, `children`, `trips`.

> 이 파일은 빈 개발 데이터베이스를 위한 전체 스키마 생성 파일이지, 기존 데이터를 보존하면서 구조를 바꾸는 migration 파일이 아닙니다. 이미 `tayotayo_dev`에 테이블과 테스트 데이터가 있다면 다시 실행하지 말고 다음 단계로 넘어가세요. 테이블이 이미 존재한다는 오류가 나와도 전체 파일을 반복 실행하지 마세요.

MySQL Workbench에서 `tayotayo_dev`를 기본 스키마로 선택한 뒤 아래 쿼리로 연결 대상과 테이블을 확인할 수 있습니다.

```sql
USE tayotayo_dev;
SHOW TABLES;
```

## 3. STS에서 서버 실행하기

1. STS에서 `backend` 프로젝트를 선택합니다.
2. 우클릭하여 **Gradle > Refresh Gradle Project**를 실행합니다.
3. `TayoTayoBackendApplication.java`를 찾아 우클릭하고 **Run As > Spring Boot App**을 선택합니다.
4. 콘솔에 서버가 실행되었다는 메시지가 나오면 브라우저에서 아래 주소를 엽니다.

   - 서버 확인: [http://localhost:8080/health](http://localhost:8080/health)
   - DB 확인: [http://localhost:8080/health/database](http://localhost:8080/health/database)
   - API 문서: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

DB 확인 응답의 `database`가 `tayotayo_dev`인지 확인합니다. 서버를 끌 때는 STS 콘솔의 빨간 정지 버튼을 누릅니다.

## 4. Swagger UI에서 요청 보내는 법

1. Swagger UI에서 필요한 API 항목을 펼칩니다.
2. **Try it out**을 누릅니다.
3. `{orgId}`, `{vehicleId}`처럼 중괄호가 있는 값은 실제 ID로 바꿉니다.
4. 요청 본문이 있으면 아래 예시 JSON을 붙여 넣습니다.
5. **Execute**를 누릅니다.
6. 응답에서 받은 ID나 토큰을 다음 요청에 사용합니다.

새 서버를 시작했거나 서버를 재시작했다면 로그인 access token이 초기화되므로 다시 로그인해야 합니다.

## 5. 회원가입과 로그인

테스트 이메일은 실제 메일을 받을 수 있는 주소일 필요는 없습니다. 중복되지 않는 임의 주소를 사용해도 됩니다.

### 4-1. 인증 코드 요청

`POST /auth/email-verifications`

```json
{
  "email": "test@example.com",
  "purpose": "SIGN_UP"
}
```

개발 환경에서는 메일 대신 응답의 `debugCode`로 인증 코드를 확인합니다. 이 코드를 복사합니다.

### 4-2. 인증 코드 확인

`POST /auth/email-verifications/confirm`

```json
{
  "email": "test@example.com",
  "purpose": "SIGN_UP",
  "code": "응답에서 받은 debugCode"
}
```

### 4-3. 가입

`POST /auth/signup`

```json
{
  "email": "test@example.com",
  "password": "testpass123",
  "name": "테스트 사용자",
  "verificationCode": "같은 debugCode"
}
```

비밀번호는 8자 이상이어야 합니다.

### 4-4. 로그인과 Swagger 인증

`POST /auth/login`

```json
{
  "email": "test@example.com",
  "password": "testpass123",
  "clientType": "ADMIN_WEB",
  "deviceLabel": "Swagger 테스트"
}
```

응답의 `accessToken`을 복사합니다. Swagger 화면 위쪽의 **Authorize**를 누르고 Bearer 인증 입력란에 토큰을 붙여 넣은 다음 **Authorize**를 누릅니다. 입력란이 `Bearer` 접두어를 직접 요구하는 경우에만 `Bearer `를 앞에 붙입니다.

## 6. 기관·차량·아이·노선 만들기

각 단계에서 응답에 나온 ID를 기록합니다. 예를 들어 기관 응답의 `id`는 아래 요청의 `{orgId}`에 사용합니다.

### 5-1. 기관 등록

`POST /orgs`

```json
{
  "name": "테스트 기관",
  "orgType": "KINDERGARTEN"
}
```

기관을 만든 로그인 계정은 해당 기관의 `OWNER`로 등록됩니다.

### 5-2. 차량 등록

`POST /orgs/{orgId}/vehicles`

```json
{
  "name": "테스트 차량",
  "plateNumber": "12가3456",
  "model": "테스트 모델",
  "seatCapacity": 20
}
```

### 5-3. 아이 등록

`POST /orgs/{orgId}/children`

```json
{
  "name": "테스트 아이",
  "className": "햇살반"
}
```

### 5-4. 정류장 등록

`POST /orgs/{orgId}/stops`

```json
{
  "name": "테스트 정류장",
  "address": "서울시 중구 세종대로 1",
  "latitude": 37.5665,
  "longitude": 126.978,
  "arrivalRadiusM": 50,
  "memo": "정문 앞"
}
```

### 5-5. 노선 등록

`POST /orgs/{orgId}/routes`

```json
{
  "vehicleId": "차량 ID",
  "name": "아침 등원 노선",
  "direction": "DROP_OFF",
  "departureTime": "08:30:00"
}
```

`direction`은 등원 `DROP_OFF` 또는 하원 `PICK_UP`입니다.

### 5-6. 노선에 정류장 넣기

`PUT /orgs/{orgId}/routes/{routeId}/stops`

```json
{
  "stops": [
    {
      "stopId": "정류장 ID",
      "sequence": 1
    }
  ]
}
```

### 5-7. 아이를 노선·차량·정류장에 배정

`PUT /orgs/{orgId}/children/{childId}/route-assignments`

```json
{
  "routeId": "노선 ID",
  "vehicleId": "차량 ID",
  "stopId": "정류장 ID",
  "weekdays": 31,
  "effectiveFrom": "2026-11-11"
}
```

`weekdays: 31`은 월요일부터 금요일까지를 뜻하는 비트 값입니다. 실제 테스트 날짜에 배정 기간이 유효해야 운행에 아이가 포함됩니다.

## 7. 차량 앱 페어링

### 6-1. 페어링 코드 발급

`POST /orgs/{orgId}/vehicles/{vehicleId}/pairing-codes`

Body:

```json
{}
```

응답의 `code`를 복사합니다. 기본 유효 시간은 10분입니다.

### 6-2. 차량 기기 연결

`POST /vehicle/pair`

```json
{
  "code": "페어링 코드",
  "installId": "550e8400-e29b-41d4-a716-446655440000",
  "appVersion": "1.0.0",
  "pleosSdkVersion": "1.0.0"
}
```

응답의 `vehicleCredential`을 복사합니다. 이후 Swagger의 **Authorize**에서 `X-Vehicle-Credential` 입력란에 넣습니다. 차량용 API를 호출할 때는 이 credential이 필요합니다.

## 8. 운행 생성과 운행 테스트

차량이 페어링되어 있고 아이가 노선에 배정되어 있어야 합니다.

### 7-1. 운행 생성

`POST /orgs/{orgId}/trips`

```json
{
  "clientTripId": "b6d5a28f-627f-4ef5-a2e3-2a0637f04c21",
  "vehicleId": "차량 ID",
  "routeId": "노선 ID",
  "serviceDate": "2026-11-11",
  "direction": "DROP_OFF"
}
```

`clientTripId`는 UUID 형식이어야 하며, 실행할 날짜는 현재 날짜를 넣습니다. 응답의 운행 `id`, `stops[].id`, `children[].tripChildId`를 기록합니다.

### 7-2. 기사 확인 체크

`PUT /trips/{tripId}/checklist`

```json
{
  "driverConfirmed": true
}
```

### 7-3. 운행 출발

`POST /trips/{tripId}/start`

응답의 상태가 `IN_PROGRESS`가 되는지 확인합니다.

### 7-4. 정류장 도착

`POST /trips/{tripId}/stops/{tripStopId}/arrive`

응답에서 정류장 상태가 `ARRIVED`인지 확인합니다.

### 7-5. 아이 탑승 이벤트 기록

`POST /trips/{tripId}/events`

```json
{
  "clientEventId": "c5a0977d-5528-47e2-a3b1-975da8e0cb4a",
  "eventType": "CHILD_BOARDED",
  "occurredAt": "2026-11-11T08:31:00+09:00",
  "tripChildId": "운행 아이 ID",
  "tripStopId": "운행 정류장 ID"
}
```

이벤트 응답에서 `accepted: true`를 확인한 뒤 `GET /trips/{tripId}`로 다시 조회합니다. 해당 아이 상태가 `BOARDED`인지 확인합니다.

### 7-6. 정류장 완료와 운행 종료

1. `POST /trips/{tripId}/stops/{tripStopId}/complete`로 정류장을 완료합니다.
2. `POST /trips/{tripId}/return`으로 기관 복귀를 시작합니다.
3. `POST /trips/{tripId}/complete`로 운행을 종료합니다.

최종 상태가 `COMPLETED`인지 확인합니다.

## 9. MySQL에서 저장 결과 확인

MySQL Workbench에서 `tayotayo_dev`를 선택하고 아래 조회를 실행합니다.

```sql
USE tayotayo_dev;

SELECT * FROM organizations ORDER BY id DESC;
SELECT * FROM vehicles ORDER BY id DESC;
SELECT * FROM children ORDER BY id DESC;
SELECT * FROM routes ORDER BY id DESC;
SELECT * FROM child_route_assignments ORDER BY id DESC;
SELECT * FROM trips ORDER BY id DESC;
SELECT * FROM trip_stops ORDER BY id DESC;
SELECT * FROM trip_children ORDER BY id DESC;
SELECT * FROM trip_events ORDER BY id DESC;
```

## 10. 자주 만나는 오류

- **401 Unauthorized**: 다시 로그인하고 Swagger의 Bearer token 또는 차량 credential을 설정합니다. 서버를 재시작했다면 새 access token을 받아야 합니다.
- **403 Forbidden**: 해당 기관의 직원·관리자이거나, 해당 아이와 연결된 보호자 계정인지 확인합니다. 권한 검사 추가 전에 만든 기관은 새 테스트 계정에 연결되어 있지 않을 수 있습니다. 새 기관을 생성하면 생성자가 소유자로 등록됩니다.
- **400 Bad Request**: JSON 필드명·값 형식·ID·날짜를 확인합니다. `clientTripId`는 UUID, 날짜는 `YYYY-MM-DD`, 운행 방향은 `DROP_OFF` 또는 `PICK_UP`이어야 합니다.
- **404 Not Found**: 경로에 넣은 기관·차량·아이·운행 ID가 실제 응답에서 받은 ID인지 확인합니다.
- **409 Conflict**: 이미 진행된 운행 상태를 다시 변경하려 하거나 필요한 선행 단계가 끝나지 않았는지 확인합니다.
- **운행에 아이나 정류장이 없음**: 운행 전에 route stops를 저장하고, 아이의 노선·차량·정류장 배정과 적용 날짜를 확인합니다.

## 참고: 개발 환경 제한

- 이메일 발송 대신 회원가입 인증 코드가 응답의 `debugCode`에 표시됩니다.
- 푸시 알림은 DB에 쌓이며 실제 FCM 발송은 아직 연결되지 않았습니다.
- access token은 현재 개발용 메모리 저장소를 사용하므로 서버를 재시작하면 기존 토큰이 무효가 됩니다.
- 좌석·안전벨트 상태는 서버에서 저장하지 않습니다.
