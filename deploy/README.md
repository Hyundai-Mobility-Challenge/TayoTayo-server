# 등원 심사용 HTTPS 서버

두 이미지 모두 저장소 루트에서 빌드합니다. API는 루트의 `Dockerfile`, MySQL은 `deploy/mysql/Dockerfile`을 사용합니다.

1. 시연 전용 MySQL 8.4 서비스에 빈 볼륨을 `/var/lib/mysql`로 연결합니다. 데이터베이스 이름은 `tayotayo_dev`로 지정합니다. `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD`를 플랫폼 비공개 변수에 설정합니다.
2. `DEMO_ADMIN_HASH`에는 심사용 관리자 비밀번호를 PBKDF2-HMAC-SHA256(16바이트 무작위 salt, 120000회, 32바이트 출력)로 계산한 `pbkdf2$<base64 salt>$<base64 hash>`를 넣습니다. 비밀번호는 심사 안내로 별도 전달합니다.
3. API 서비스에 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 지정합니다. DB 주소에는 사설 호스트와 데이터베이스 이름을 사용합니다. 공개 HTTPS 도메인은 API에만 연결합니다. 헬스 체크는 `/health/database`입니다.
4. 시연자는 발급된 HTTPS 주소를 사용하고 관리자 이메일 `orgs@example.com`과 전달받은 심사용 비밀번호로 로그인합니다. 차량 페어링 및 당일 등원 운행 생성은 [서버 시연 절차](../README.md)를 따릅니다.

MySQL 첫 기동에서만 가상 원아 3명과 등원 노선을 등록합니다. 공개 로컬 예시의 비밀번호와 차량 인증값은 DB에 넣기 전에 교체합니다. 운영 데이터베이스에 적용하지 마세요.
