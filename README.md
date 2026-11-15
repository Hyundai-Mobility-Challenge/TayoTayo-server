# 타요타요 기관 서버

**통학 차량 한 번의 운행을 기관·차량·보호자가 같은 기록으로 보도록 저장하고 전달하는 API 서버입니다.**
Pleos 차량 앱이 보낸 탑승·도착·인계·하차 기록을 저장하고, 보호자 앱과 기관 관리자 웹이 그 기록을 조회합니다. 도착과 하차 때는 해당 보호자에게 FCM 알림을 보냅니다.

| 구성 | 저장소 / 위치 |
| --- | --- |
| 서버 · API | 이 저장소 [`backend/`](backend) |
| 기관 관리자 웹 | [TayoTayo-web](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-web) · 이 저장소의 [`web/`](web) 서브모듈 |
| Pleos 차량 앱 (대표 저장소) | [TayoTayo-vehicle](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-vehicle) |
| 보호자 앱 | [TayoTayo-parent](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-parent) |

## 서버가 맡는 일

```mermaid
flowchart LR
    W["기관 관리자 웹"] -->|원아·노선·차량 배정, 코드 발급| S["기관 서버 (Spring Boot)"]
    V["Pleos 차량 앱"] -->|X-Vehicle-Credential, 운행·이벤트·위치·ETA| S
    G["보호자 앱"] -->|Bearer 토큰, 출결·마중 담당자| S
    S --> DB[("MySQL tayotayo_dev")]
    S -->|도착·하차 알림| F["Firebase Cloud Messaging"]
    F --> G
```

| 영역 | 서버가 하는 일 |
| --- | --- |
| 기관 운영 | 기관·직원·차량·정류장·노선을 관리하고, 원아를 노선·차량·정류장·요일에 배정합니다. |
| 연결 | 차량 페어링 코드(10분)와 보호자 초대 코드(7일)를 발급합니다. 차량 credential은 SHA-256 값만 저장합니다. |
| 운행 기록 | 날짜·노선으로 운행을 만들 때 그날의 정류장 순서와 원아 명단을 복사해 둡니다. 이후 배정이 바뀌어도 지난 운행 기록은 그대로 남습니다. |
| 위치·예상 시간 | 차량 앱이 보낸 위치와 내비 ETA를 출처·시각과 함께 저장합니다. 서버가 경로를 계산하거나 도착·하차를 판정하지는 않습니다. |
| 보호자 | 연결된 아이 범위에서만 운행 진행, 출결 요청, 지정 인계자, 공유 링크, 알림을 처리합니다. |
| 알림 | 알림을 DB에 먼저 기록하고 중복을 막은 뒤, Firebase 설정이 있으면 FCM으로 보냅니다. |

### 운행 상태

```mermaid
stateDiagram-v2
    [*] --> PREPARING: 기관이 운행 생성
    PREPARING --> IN_PROGRESS: 차량 앱 출발
    PREPARING --> CANCELLED: 준비 중 취소
    IN_PROGRESS --> RETURNING: 정류장 처리 후 기관 복귀
    RETURNING --> COMPLETED: 실내 확인 후 운행 종료
    IN_PROGRESS --> COMPLETED: 등원 운행 종료
```

정류장은 `PENDING → ARRIVED → COMPLETED`(또는 `SKIPPED`) 순서로 바뀝니다. 상태 순서에 맞지 않는 요청은 `409 Conflict`로 거절하고, 완료되지 않은 정류장이 남아 있으면 운행을 종료하지 않습니다.

## 빠르게 실행하기

### 준비물

Windows PC에 Java 21과 MySQL 8을 준비합니다. MySQL 서비스와 데이터베이스를 만들 수 있는 계정이 필요합니다. 첫 Gradle 빌드에는 인터넷 연결이 필요합니다.

GitHub에서 **Code → Download ZIP**을 눌러 압축을 풀거나 아래 명령으로 서버를 받습니다. 아래 등원 시연은 서버에 포함된 Swagger UI로 진행하므로 관리자 웹과 Node.js 설치를 생략할 수 있습니다.

```sh
git clone https://github.com/Hyundai-Mobility-Challenge/TayoTayo-server.git
```

관리자 웹도 실행할 팀원은 웹 저장소 접근 권한과 Node.js 20 이상을 준비한 뒤 `git submodule update --init --recursive`로 `web/`을 받습니다.

### 1. 데이터베이스 만들기

새 개발 DB를 만들 때만 [실행용 SQL](TayoTayo_실행용_회의반영.sql)을 MySQL Workbench에서 열어 전체 실행합니다. `tayotayo_dev` 데이터베이스와 테이블, 햇살 유치원 데모 데이터가 함께 만들어집니다.

> [!WARNING]
> 전체 초기화 파일입니다. 이미 테이블이나 데이터가 있는 DB에는 실행하지 마세요. 기존 DB를 고치는 migration 파일이 아닙니다.

### 2. 서버 실행

1. `backend\start-server.bat`을 실행합니다.
2. 처음 실행하면 MySQL 주소·포트·DB 이름·계정·비밀번호를 묻습니다. 같은 PC의 기본값은 `127.0.0.1`, `3306`, `tayotayo_dev`, `ssafy`이며, 입력값은 `backend\.secrets\server.properties`에 저장됩니다(Git 제외).
3. 시작이 끝나면 [http://localhost:8080/health](http://localhost:8080/health)와 [DB 연결 상태](http://localhost:8080/health/database)를 차례로 확인합니다. API 문서는 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)입니다.
4. 종료는 실행 창에서 `Ctrl+C`입니다.

| 접속하는 곳 | 서버 주소 |
| --- | --- |
| 서버 PC 브라우저 | `http://127.0.0.1:8080` |
| 같은 PC의 Android 에뮬레이터 | `http://10.0.2.2:8080` |
| 같은 Wi-Fi의 휴대폰·다른 PC | `http://<서버 PC의 LAN IPv4>:8080` (필요하면 Windows 방화벽에서 8080 허용) |

앱에는 호스트와 포트만 입력합니다. `/api/v1`은 앱이 붙입니다.

### 3. Swagger로 등원 시연 준비하기

초기 SQL에는 햇살 유치원(기관 `1`), 햇살 1호차(차량 `1`), 오전 등원 노선(`1`), 정류장 3곳과 원아 3명의 배정이 들어 있습니다. 함께 생성되는 등원 운행 1건은 SQL의 테스트 기기에 연결됩니다. **새 차량 앱을 먼저 페어링하고 그 기기에서 불러올 오늘 운행을 한 건 생성하세요.**

**앱 준비.** 위 표의 로컬 HTTP 주소로 시연할 때는 차량·보호자 앱의 디버그 빌드를 사용합니다. 차량 앱은 Pleos Connect 에뮬레이터에서, 보호자 앱은 Android 에뮬레이터나 같은 Wi-Fi의 휴대폰에서 실행합니다. [차량 앱 빌드 안내](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-vehicle#시작하기) · [보호자 앱 빌드 안내](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-parent#시작하기)

[Swagger UI](http://localhost:8080/swagger-ui.html)에서 아래 경로를 열고 **Try it out → Execute**를 누릅니다. Swagger가 `/api/v1`을 붙입니다. 경로의 `orgId`, `vehicleId`, `childId`에는 아래에서 지정한 숫자를 입력합니다.

1. **관리자 로그인.** `POST /auth/login`의 요청 본문에 아래 값을 넣습니다. 응답의 `accessToken`을 복사해 상단 **Authorize → BearerAuth**에 토큰 값만 입력합니다.

   ```json
   {"email":"orgs@example.com","password":"orgspass123","clientType":"ADMIN_WEB"}
   ```

2. **기관 좌표 확인.** `GET /orgs/{orgId}`에 `orgId=1`을 넣습니다. 새 DB라면 시연용 가상 목적지의 `latitude`는 `37.4186`, `longitude`는 `127.1286`입니다. 이전 시드로 만든 **데모 DB**에서 좌표가 비어 있으면 `PATCH /orgs/{orgId}`에 `{"latitude":37.4186,"longitude":127.1286}`을 보냅니다. 기존 DB를 초기화할 필요는 없습니다.

3. **차량 페어링.** `POST /orgs/{orgId}/vehicles/{vehicleId}/pairing-codes`에 기관과 차량 모두 `1`, 본문 `{"expiresIn":600}`을 입력합니다. 응답의 6자리 `code`를 **10분 안에 차량 앱의 기관 연결 화면**에 서버 주소와 함께 입력합니다. 앞자리가 0이면 그대로 입력하고, 앱에 연결 완료가 표시된 뒤 다음 단계로 갑니다.

4. **오늘 등원 운행 생성.** PowerShell에서 아래 명령을 실행하면 오늘 날짜와 새 운행 식별자가 담긴 JSON이 출력됩니다. 출력 전체를 `POST /orgs/{orgId}/trips`(`orgId=1`)의 요청 본문에 붙여 넣고 한 번 실행합니다. `serviceDate`는 서버 DB의 오늘 날짜와 같아야 합니다.

   ```powershell
   @{
       clientTripId = [guid]::NewGuid().ToString()
       vehicleId    = "1"
       routeId      = "1"
       serviceDate  = Get-Date -Format 'yyyy-MM-dd'
       direction    = "PICK_UP"
   } | ConvertTo-Json
   ```

   응답에서 운행 `id`, `status=PREPARING`, `stops` 3개, `children` 3명을 확인합니다. 차량 앱에서 **등원 → 오늘 운행 목록**을 열어 방금 만든 운행을 선택합니다. 명단은 **판교역 김하늘 → 야탑역 김바다 → 성남시청 이마음** 순서입니다. 목록이 비면 페어링이 완료된 시각이 운행 생성보다 앞서는지, 운행 날짜와 등원 방향이 맞는지 확인합니다.

5. **보호자 앱에 김하늘 연결.** 보호자 앱의 **개발 서버 설정**에서 같은 서버를 지정한 뒤 시연용 이메일로 가입·로그인합니다. 개발용 가입 인증번호는 앱에 표시됩니다. 관리자 Swagger에서 `POST /orgs/{orgId}/children/{childId}/guardian-invitations`에 `orgId=1`, `childId=1`을 넣고, 아래 이메일을 **방금 가입한 주소로 바꿔** 요청합니다.

   ```json
   {"email":"guardian@example.com","relationship":"OTHER"}
   ```

   응답의 `invitationCode`를 보호자 앱 **초대 코드 연결**에 입력합니다. 보호자 홈에 김하늘이 표시되면 연결이 끝납니다.

**등원 시연 순서.** 차량에서 배정 명단을 확인하고 출발합니다. 정류장마다 차량을 P·속도 0으로 정차하고 **보호자에게서 인계받음 → 탑승 기록**을 진행합니다. 마지막 정류장을 마치면 기관으로 이동해 **기관 도착 → 탑승한 아이별 기관 인계 → 실내 확인 → 운행 종료** 순서로 마칩니다. Swagger의 `GET /trips/{tripId}`에 4단계에서 받은 운행 ID를 넣어 `status=COMPLETED`와 아이별 기록을 확인할 수 있습니다. Firebase 설정은 휴대폰 푸시를 시험할 때 필요합니다.

기본 관리자 계정과 시드 기기의 API 점검용 credential(`DEMO-HASAL-VEHICLE-1`)은 로컬 데모용입니다. 차량 앱 연결에는 3단계에서 발급한 일회성 코드를 사용합니다.

## 설정

| 설정 | 역할 | 기본값·주의 |
| --- | --- | --- |
| `DB_URL` | JDBC 주소 | `jdbc:mysql://127.0.0.1:3306/tayotayo_dev?...` |
| `DB_USERNAME`, `DB_PASSWORD` | MySQL 로그인 | 첫 실행 때 입력해 로컬 파일에 저장 |
| `FIREBASE_ENABLED` | FCM 전송 사용 | 기본 `false` |
| `FIREBASE_CREDENTIALS_PATH` | Firebase Admin 서비스 계정 JSON 경로 | 저장소 밖의 키 파일 |
| `FIREBASE_PROJECT_ID` | Firebase 프로젝트 ID | 서비스 계정의 `project_id`와 같아야 함 |
| `FIREBASE_TEST_ENDPOINT_ENABLED` | 개발용 FCM 드라이런 API | 기본 `false`, 확인 후 끄기 |

Kakao 지도 검색은 서버가 아니라 관리자 웹이 중계합니다. 서비스 계정 JSON, 실제 토큰, 비밀번호는 Git에 넣지 않습니다.

## 기술 스택과 구조

- **Java 21 · Spring Boot 4.1.2 · Spring MVC**: 컨트롤러가 요청을 받고 `JdbcTemplate`으로 MySQL을 읽고 씁니다.
- **MySQL 8**: 스키마는 서버가 만들거나 바꾸지 않고 실행용 SQL로 준비합니다.
- **Firebase Admin SDK**: 도착·하차 알림을 FCM으로 보냅니다.
- **springdoc-openapi**: `/swagger-ui.html`에서 API를 시험합니다.

```text
TayoTayo-server/
├─ backend/                      Spring Boot API (start-server.bat, run-server.ps1)
│  └─ src/main/java/com/tayotayo/backend/
│     ├─ auth · orgs · management · vehicles · children · invitations
│     ├─ trips · operations · telemetry
│     └─ guardian · attendance · pickups · sharing · announcements · notifications
├─ web/                          관리자 웹 서브모듈
├─ docs/                         Swagger·DB·상세 안내
├─ TayoTayo_실행용_회의반영.sql   스키마 + 데모 데이터
└─ TayoTayo_OpenAPI_3.0.json     API 정의
```

패키지별 역할, 계정·운행·보호자 데이터 흐름, 테이블 묶음, 관리자 운영 순서는 [서버 구조와 상세 사용 방법](docs/SERVER_GUIDE.md)에 정리했습니다.

## 현재 범위와 유의점

- 인증 코드 응답의 `debugCode`는 메일 서버가 없는 개발 설정입니다. 운영 인증 수단으로 쓰지 마세요.
- access token은 개발용 메모리 저장소에 있어 서버를 재시작하면 만료됩니다. refresh token은 해시해 DB에 저장합니다. 비밀번호는 salt를 넣은 PBKDF2-HMAC-SHA256(120,000회)로 저장합니다.
- 공개 운영 전에는 인증, 비밀값, HTTPS, 권한 정책을 따로 점검해야 합니다.
- 데이터가 있는 DB는 실행용 SQL을 다시 돌려 올리지 말고, 백업 후 별도 migration으로 바꿉니다.

## 문서

- [서버 구조와 상세 사용 방법](docs/SERVER_GUIDE.md)
- [Swagger / OpenAPI 사용 안내](docs/SWAGGER.md)
- [데이터베이스 안내](docs/DATABASE.md)
