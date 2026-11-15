# TayoTayo 백엔드 API 처음 실행하기

이 문서는 처음 백엔드를 실행하는 사람이 **실행 파일과 Swagger UI만으로 회원가입부터 운행 테스트까지** 따라 할 수 있도록 정리했습니다. STS 실행 방법도 선택 항목으로 남겨 두었습니다.

연동 앱과 전체 소스는 [대표 저장소 · 차량 앱](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-vehicle)에서 확인할 수 있습니다. [보호자 앱](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-parent)에는 설치용 APK와 계정 연결 방법이 있습니다.

## 1. 준비물

- Java 21
- MySQL 8이 실행 중이고 `tayotayo_dev` 데이터베이스에 프로젝트 스키마가 만들어져 있어야 합니다.
- 처음 실행할 때 MySQL 접속 정보를 한 번 입력합니다. 입력한 비밀번호는 이 PC의 `backend/.secrets/server.properties`에 저장되며 Git에는 포함되지 않습니다.

기본 DB 주소는 `jdbc:mysql://127.0.0.1:3306/tayotayo_dev`입니다. 필요하면 `DB_URL` 환경 변수로 바꿀 수 있습니다. 서버는 DB 테이블을 자동으로 만들거나 변경하지 않습니다.

### 다른 컴퓨터의 MySQL을 사용할 때

`127.0.0.1`은 백엔드와 같은 PC를 뜻합니다. MySQL이 다른 PC에서 실행 중이면 `backend/.secrets/server.properties`의 `DB_HOST`를 MySQL PC의 LAN IPv4 주소로 바꿉니다.

```text
jdbc:mysql://192.168.0.40:3306/tayotayo_dev?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
```

MySQL PC에서도 원격 연결을 허용하고, Windows 방화벽의 `3306` 포트와 MySQL 계정의 접속 권한을 설정해야 합니다. MySQL과 백엔드가 같은 PC라면 기본 주소를 유지하세요.

## 2. MySQL 데이터베이스와 테이블 준비

### 어떤 SQL 파일을 실행하나요?

처음 개발 DB를 준비하는 경우 프로젝트 루트에 있는 **[`TayoTayo_실행용_회의반영.sql`](./TayoTayo_실행용_회의반영.sql)**을 사용합니다. 이 파일은 회의 결정이 반영된 MySQL 전체 스키마이며, `tayotayo_dev` 데이터베이스를 선택하고 서비스가 사용하는 테이블을 만듭니다.

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

## 3. 서버 실행하기 (STS 없이)

Windows에서는 `backend/start-server.bat`을 더블클릭합니다. 첫 실행에서 MySQL 주소·포트·데이터베이스·계정·비밀번호를 물어봅니다. MySQL이 백엔드와 같은 PC에 있으면 기본값을 그대로 사용하고, 비밀번호만 입력하면 됩니다. 접속 정보는 로컬 설정 파일에 저장되어 이후에는 실행 파일만 다시 열면 됩니다.

실행 파일은 Gradle로 실행 가능한 Spring Boot JAR를 만들고 서버를 시작합니다. 최초 한 번은 의존성을 내려받고 컴파일하므로 시간이 걸릴 수 있으며, 이후 실행은 변경 사항이 있을 때만 다시 빌드합니다. 종료하려면 열린 창에서 **Ctrl+C**를 누릅니다.

실행 후 창에 표시되는 주소를 사용합니다. 내 PC에서는 `http://localhost:8080/health`, API 문서는 `http://localhost:8080/swagger-ui.html`입니다. 같은 Wi-Fi의 다른 PC나 휴대폰은 창에 표시되는 `http://<백엔드 PC의 LAN IPv4>:8080` 주소를 사용합니다. IP가 바뀌면 앱의 서버 주소만 새 값으로 바꿔야 합니다. 자주 바뀌지 않게 하려면 공유기에서 백엔드 PC의 DHCP 예약을 설정할 수 있습니다.

### DB 주소나 계정을 나중에 바꿀 때

`backend/.secrets/server.properties`를 수정한 뒤 서버를 다시 실행합니다. MySQL이 다른 PC에 있으면 `DB_HOST`를 MySQL PC의 주소로 변경합니다. 이 경우 MySQL 쪽 원격 연결, 계정 권한, 방화벽 `3306` 허용도 필요합니다. 비밀번호가 변경되면 `DB_PASSWORD`도 수정합니다.

FCM 푸시를 사용할 때는 같은 파일에 `FIREBASE_ENABLED=true`, `FIREBASE_CREDENTIALS_PATH=<서비스 계정 JSON의 전체 경로>`, `FIREBASE_PROJECT_ID=<Firebase 프로젝트 ID>`를 입력합니다. 테스트 API를 쓸 때만 `FIREBASE_TEST_ENDPOINT_ENABLED=true`로 설정합니다. 서비스 계정 JSON 파일과 설정 파일은 저장소에 올리지 마세요.

STS를 계속 쓰고 싶다면 아래 방법도 사용할 수 있습니다.

## STS에서 서버 실행하기 (선택)

### DB 접속 환경 변수 등록

서버가 MySQL에 접속하려면 실행 설정에 아래 값을 등록해야 합니다.

| 변수 이름 | 입력할 값 |
| --- | --- |
| `DB_USERNAME` | MySQL 계정 이름 |
| `DB_PASSWORD` | 해당 MySQL 계정의 비밀번호 |

STS에서 **Run > Run Configurations... > Spring Boot App > TayoTayoBackendApplication > Environment**로 이동합니다. **Add...**를 눌러 변수 이름과 값을 하나씩 추가한 뒤 **Apply**를 누릅니다. 비밀번호는 README, 코드, Git에 기록하지 마세요.

`DB_URL`은 기본값이 `jdbc:mysql://127.0.0.1:3306/tayotayo_dev?serverTimezone=Asia/Seoul&characterEncoding=UTF-8`이므로 보통 추가할 필요가 없습니다. 다른 주소나 데이터베이스를 사용할 때만 `DB_URL`을 추가합니다.

### 서버 시작

1. STS에서 `backend` 프로젝트를 선택합니다.
2. 우클릭하여 **Gradle > Refresh Gradle Project**를 실행합니다.
3. `TayoTayoBackendApplication.java`를 찾아 우클릭하고 **Run As > Spring Boot App**을 선택합니다.
4. 콘솔에 서버가 실행되었다는 메시지가 나오면 브라우저에서 아래 주소를 엽니다.

   - 서버 확인: [http://localhost:8080/health](http://localhost:8080/health)
   - DB 확인: [http://localhost:8080/health/database](http://localhost:8080/health/database)
   - API 문서: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

DB 확인 응답의 `database`가 `tayotayo_dev`인지 확인합니다. 서버를 끌 때는 STS 콘솔의 빨간 정지 버튼을 누릅니다.

## 다른 PC·에뮬레이터·휴대폰에서 API 접속

백엔드 기본 포트는 `8080`입니다. 서버 PC에서 PowerShell의 `ipconfig`를 실행해 Wi-Fi 또는 Ethernet 어댑터의 `IPv4 주소`를 확인합니다. 예를 들어 `192.168.0.30`이면 같은 네트워크의 다른 PC 브라우저에서 `http://192.168.0.30:8080/health`를 열어 연결을 확인합니다.

| 접속 기기 | 백엔드 주소 | 조건 |
| --- | --- | --- |
| 백엔드 PC 자체 | `http://127.0.0.1:8080` | 기본값 |
| Android 에뮬레이터 | `http://10.0.2.2:8080` | 에뮬레이터와 백엔드가 같은 PC에서 실행 |
| 실제 휴대폰 | `http://<백엔드 PC의 LAN IPv4>:8080` | 휴대폰과 PC가 같은 Wi-Fi/LAN |
| 다른 PC의 에뮬레이터 | `http://<백엔드 PC의 LAN IPv4>:8080` | `10.0.2.2`는 에뮬레이터가 실행되는 PC를 가리킴 |

휴대폰과 다른 PC에서는 `127.0.0.1`이 각자 자기 기기를 가리키므로 서버 주소로 사용할 수 없습니다. 앱의 서버 주소에는 호스트와 포트만 입력하고 `/api/v1`은 붙이지 않습니다. Windows 방화벽에서 `8080` 포트를 개인 네트워크에 허용해야 할 수 있습니다. 개발 서버를 공용 인터넷에 노출하지 마세요.

LAN IP가 바뀌면 휴대폰·에뮬레이터의 서버 주소와, 관리자 웹 서버가 다른 PC에 있는 경우 `BACKEND_URL`을 새 주소로 바꿔야 합니다. 주소 변경을 줄이려면 공유기에서 백엔드 PC의 DHCP 예약을 설정할 수 있습니다.

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
  "direction": "PICK_UP",
  "departureTime": "08:30:00"
}
```

`direction`은 등원 `PICK_UP` 또는 하원 `DROP_OFF`입니다.

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
  "effectiveFrom": "YYYY-MM-DD"
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
  "serviceDate": "YYYY-MM-DD",
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
  "occurredAt": "YYYY-MM-DDTHH:mm:ss+09:00",
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

## 11. 도착·하차 푸시(FCM) 실제 수신 확인

서버는 정류장 도착 처리 때 해당 정류장에 배정된 아이의 보호자에게 `ARRIVED` 알림을 만들고, `CHILD_DROPPED_OFF` 또는 `CHILD_RECEIVED_BY_ORG` 운행 이벤트가 들어오면 `DROPPED_OFF` 알림을 만듭니다. Firebase 설정이 없으면 알림은 DB에 `PENDING`으로 남아서 서버의 기본 실행에는 영향을 주지 않습니다.

### 11-1. Firebase 서비스 계정 키 준비

1. Firebase Console에서 사용할 Firebase 프로젝트를 엽니다. 테스트용 앱의 FCM 등록 토큰과 서버 키는 **같은 Firebase 프로젝트**에 속해야 합니다.
2. 프로젝트 설정의 **서비스 계정** 탭에서 비공개 키(JSON)를 생성해 다운로드합니다.
3. JSON 파일은 프로젝트/Git 폴더 바깥에 보관합니다. 예: `C:\firebase-keys\tayotayo-service-account.json`. 이 파일은 저장소 폴더 밖에 보관하고 채팅이나 Git에 올리지 않습니다. 저장소 루트에 서비스 계정 키가 이미 존재한다면 즉시 키를 폐기·재발급하고, 새 키를 저장소 밖에 보관하세요.
4. Firebase Console의 Cloud Messaging 설정에서 Firebase Cloud Messaging API가 활성화되어 있는지 확인합니다.

### 11-2. STS에 Firebase 환경 변수 입력

1. Gradle 의존성을 반영하려고 프로젝트 우클릭 > **Gradle > Refresh Gradle Project**를 한 번 실행합니다.
2. **Run > Run Configurations... > Spring Boot App > TayoTayoBackendApplication**을 선택합니다.
3. **Environment** 탭에서 아래 변수를 추가합니다. 파일 경로와 프로젝트 ID는 실제 값으로 바꿉니다.

   - `FIREBASE_ENABLED` = `true`
   - `FIREBASE_CREDENTIALS_PATH` = `C:\firebase-keys\tayotayo-service-account.json`
   - `FIREBASE_PROJECT_ID` = Firebase 프로젝트 설정에 표시된 프로젝트 ID

4. **Apply > Run**으로 서버를 시작합니다. 키 파일 경로가 틀리거나 JSON이 유효하지 않으면 시작 로그에 원인이 표시됩니다. 서비스 계정 JSON은 Firebase Console의 **프로젝트 설정 > 서비스 계정 > 새 비공개 키 생성**에서 받습니다. 앱용 `google-services.json`은 서버 키가 아닙니다. `FIREBASE_ENABLED`가 `false`이거나 설정이 빠지면 서버는 실행되지만 실제 푸시 발송은 꺼져 있습니다. 설정 전에 생성된 도착·하차 알림은 `PENDING`으로 남고, FCM이 켜진 상태로 서버를 시작하면 다시 발송을 시도합니다.

`FIREBASE_PROJECT_ID`에는 Firebase 화면의 프로젝트 표시 이름이 아니라 **프로젝트 설정 > 일반**의 프로젝트 ID를 넣습니다. 서비스 계정 JSON 안의 `project_id` 값과 같아야 합니다. 이번 연결 확인에서는 표시 이름 `TayoTayo`를 넣어 `projects/TayoTayo`로 전송하면서 403 `CONSUMER_INVALID`가 발생했습니다. 프로젝트 ID를 `tayotayo-da4d1`로 바꾼 뒤 드라이런 검증이 성공했습니다. Firebase Cloud Messaging API도 해당 프로젝트의 Cloud Messaging 설정에서 활성화되어 있어야 합니다.

### 11-3. 실제 기기 토큰 등록

서비스 계정 키만으로는 휴대폰에 푸시를 보낼 수 없습니다. Firebase에 연결된 Android/iOS 앱이 실행되어 발급받은 **실제 FCM 등록 토큰**도 필요합니다. 앱 담당자가 토큰을 제공하거나, 해당 Firebase 프로젝트에 연결된 테스트 앱/기기를 준비해야 합니다. 임의 문자열은 사용할 수 없습니다.

푸시를 받을 보호자 계정은 아이와 연결되어 있어야 합니다. 아직 연결 전이면 다음 흐름으로 연결합니다.

1. 보호자 이메일로 별도 계정을 가입하고 `clientType`을 `GUARDIAN_APP`으로 로그인합니다. 개발용 인증 코드는 응답의 `debugCode`를 사용합니다.
2. 기관 관리자 계정에서 `POST /orgs/{orgId}/children/{childId}/guardian-invitations`를 호출합니다. 요청 예: `{"email":"guardian@example.com","relationship":"MOTHER"}`. 응답의 `invitationCode`를 복사합니다.
3. 보호자 계정으로 로그인하고 `POST /guardian-invitations/accept`를 호출해 `{"code":"초대 코드"}`를 보냅니다. 초대한 이메일과 로그인 계정 이메일이 같아야 합니다.

로그인한 보호자 access token으로 `POST /me/push-devices`에 등록합니다. 앱에서 권한 승인을 받은 기기여야 `osPermission`을 `GRANTED`로 보냅니다.

```json
{
  "platform": "ANDROID",
  "fcmToken": "앱에서 발급받은 실제 FCM 토큰",
  "appVersion": "1.0.0",
  "osPermission": "GRANTED"
}
```

Swagger에서 먼저 로그인한 뒤 **Authorize**에 로그인 access token을 넣어야 합니다. 응답에 등록된 기기 ID가 오는지 확인합니다.

### 앱 없이 FCM 연결만 검증하기

앱과 FCM 기기 토큰이 아직 없다면 STS 실행 설정에 `FIREBASE_TEST_ENDPOINT_ENABLED=true`를 추가하고 서버를 재시작합니다. 로그인한 상태에서 Swagger의 `POST /dev/fcm/dry-run`을 호출하면 FCM에 실제 발송 없이 요청 형식과 서버 인증을 검증합니다.

성공 응답은 `status: VALIDATED`, `dryRun: true`, `messageId`입니다. 이번 확인에서도 Firebase가 요청을 검증해 `VALIDATED`를 반환했습니다. 이는 서버 인증, 프로젝트 ID, FCM 요청이 Firebase에서 받아들여졌다는 뜻이며 기기 푸시 수신을 확인한 것은 아닙니다.

`401 Unauthorized`가 나오면 서버를 재시작한 뒤 다시 로그인하고 Swagger의 **Authorize**에 새 access token을 입력합니다. access token은 서버 재시작 때 초기화됩니다. `403 Permission denied`와 `CONSUMER_INVALID`가 나오면 `FIREBASE_PROJECT_ID`가 표시 이름이 아닌 프로젝트 ID인지 확인하고, FCM API 활성화 여부도 확인합니다. 개발용 엔드포인트는 기본 비활성화이며, 확인 후 `FIREBASE_TEST_ENDPOINT_ENABLED=false`로 되돌리고 서버를 재시작하세요.

### 11-4. 가상 운행에서 실제 푸시 확인

README의 5~8절대로 가상 기관, 아이, 정류장, 노선, 배정, 차량 페어링, 운행을 준비합니다. 실제 앱 화면 없이도 Swagger의 차량 API로 이벤트를 시뮬레이션할 수 있습니다.

1. `POST /trips/{tripId}/stops/{tripStopId}/arrive` 실행: 보호자 휴대폰에 **셔틀 도착 안내**가 오는지 확인합니다.
2. 같은 운행의 아이에 대해 `POST /trips/{tripId}/events`를 실행하고 `eventType`을 `CHILD_DROPPED_OFF`로 설정합니다. 휴대폰에 **하차 안내**가 오는지 확인합니다. 이 이벤트는 테스트 데이터의 아이 상태도 `DROPPED_OFF`로 바꿉니다.
3. 서버 콘솔과 MySQL의 발송 기록을 확인합니다.

```sql
USE tayotayo_dev;

SELECT id,user_id,child_id,trip_id,notification_type,status,created_at,sent_at
FROM notifications
ORDER BY id DESC LIMIT 20;

SELECT nd.notification_id,nd.push_device_id,nd.status,nd.fcm_message_id,nd.error_code,nd.attempted_at
FROM notification_deliveries nd
ORDER BY nd.id DESC LIMIT 20;
```

`notifications.status='SENT'` 및 `notification_deliveries.status='SENT'`는 Firebase가 기기 토큰을 받아들였다는 뜻입니다. 화면에 실제 표시되는지는 기기 알림 권한, 앱의 포그라운드 처리, OS 설정도 영향을 줍니다. `SKIPPED`면 `push_devices`에 권한 허용 상태의 활성 토큰이 없는지 확인하고, `FAILED`면 `error_code`와 서버 로그를 확인합니다.

### 참고: 개발 환경 제한

- 이메일 발송 대신 회원가입 인증 코드가 응답의 `debugCode`에 표시됩니다.
- FCM 발송은 서비스 계정 경로와 실제 앱 토큰을 등록한 경우에만 활성화됩니다.
- access token은 현재 개발용 메모리 저장소를 사용하므로 서버를 재시작하면 기존 토큰이 무효가 됩니다.
- 좌석·안전벨트 상태는 서버에서 저장하지 않습니다.

