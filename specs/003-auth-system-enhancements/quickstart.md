# Quick Start Guide: 인증/인가 시스템 및 알림 기능

**Feature**: 003-auth-system-enhancements
**Last Updated**: 2025-11-26

이 가이드는 로컬 개발 환경에서 인증 시스템과 알림 기능을 테스트하기 위한 설정 방법을 안내합니다.

---

## Prerequisites

- Java 17+
- Docker & Docker Compose
- MySQL 8.0+ (Docker 사용 권장)
- Redis 6.0+ (Docker 사용 권장)
- Google/Kakao 개발자 계정 (소셜 로그인 테스트 시)

---

## 1. 환경 변수 설정

`backend/src/main/resources/application-local.yml` 파일 생성:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/zipduck?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: zipduck
    password: zipduck_dev
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway 사용
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQL8Dialect
  
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  
  data:
    redis:
      host: localhost
      port: 6379
      password:  # 로컬은 비밀번호 없음
      timeout: 2000ms
  
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: profile, email
            redirect-uri: "http://localhost:8080/api/v1/oauth2/callback/google"
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
            client-authentication-method: client_secret_post
            authorization-grant-type: authorization_code
            redirect-uri: "http://localhost:8080/api/v1/oauth2/callback/kakao"
            scope: profile_nickname, account_email
        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            user-name-attribute: id

jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-for-development-only-change-in-production}
  access-token-expiration: 3600000  # 1시간 (ms)
  refresh-token-expiration: 604800000  # 7일 (ms)

aws:
  ses:
    region: ap-northeast-2
    from-email: noreply@zipduck.com
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}
```

**환경 변수 설정** (`.env` 파일 또는 IDE 설정):
```bash
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
KAKAO_CLIENT_ID=your_kakao_rest_api_key
KAKAO_CLIENT_SECRET=your_kakao_client_secret
JWT_SECRET=your-super-secret-jwt-key-min-256-bits
AWS_ACCESS_KEY=your_aws_access_key
AWS_SECRET_KEY=your_aws_secret_key
```

---

## 2. Docker로 MySQL & Redis 실행

**docker-compose.yml** (프로젝트 루트에 생성):

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: zipduck-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: zipduck
      MYSQL_USER: zipduck
      MYSQL_PASSWORD: zipduck_dev
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

  redis:
    image: redis:7-alpine
    container_name: zipduck-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

volumes:
  mysql_data:
  redis_data:
```

**실행**:
```bash
docker-compose up -d
```

**확인**:
```bash
# MySQL 접속 확인
docker exec -it zipduck-mysql mysql -uzipduck -pzipduck_dev zipduck

# Redis 접속 확인
docker exec -it zipduck-redis redis-cli ping
# 응답: PONG
```

---

## 3. OAuth 2.0 Client ID/Secret 발급

### Google OAuth 2.0 설정

1. [Google Cloud Console](https://console.cloud.google.com/) 접속
2. 프로젝트 생성 또는 선택
3. **API 및 서비스 > 사용자 인증 정보** 메뉴
4. **사용자 인증 정보 만들기 > OAuth 클라이언트 ID**
5. 애플리케이션 유형: **웹 애플리케이션**
6. **승인된 리디렉션 URI** 추가:
   - `http://localhost:8080/api/v1/oauth2/callback/google`
   - `http://localhost:3000/oauth2/redirect` (프론트엔드)
7. 클라이언트 ID와 Secret 복사 → 환경 변수에 설정

### Kakao OAuth 2.0 설정

1. [Kakao Developers](https://developers.kakao.com/) 접속
2. 내 애플리케이션 > 애플리케이션 추가하기
3. **앱 설정 > 플랫폼 > Web 플랫폼 등록**
   - 사이트 도메인: `http://localhost:8080`
4. **제품 설정 > 카카오 로그인**
   - Redirect URI 등록: `http://localhost:8080/api/v1/oauth2/callback/kakao`
5. **동의 항목 설정**:
   - 닉네임: 필수
   - 카카오계정(이메일): 필수
6. **앱 키 > REST API 키** 복사 → `KAKAO_CLIENT_ID`
7. **보안 > Client Secret** 생성 및 복사 → `KAKAO_CLIENT_SECRET`

---

## 4. AWS SES 설정 (이메일 발송)

### Sandbox 모드 사용 (개발 환경)

1. [AWS Console > SES](https://console.aws.amazon.com/ses/) 접속
2. **Verified identities > Create identity**
3. **Email address** 선택, 개발자 이메일 입력
4. 이메일 수신함에서 인증 링크 클릭
5. **SMTP settings**에서 IAM 사용자 생성
6. Access Key, Secret Key 복사 → 환경 변수 설정

**주의**: Sandbox 모드에서는 verified된 이메일로만 발송 가능

### 운영 환경 (Production Access 신청)

1. AWS Console > SES > **Account dashboard**
2. **Request production access** 버튼 클릭
3. Use case 설명 (24시간 내 승인)

---

## 5. 애플리케이션 실행

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

**Swagger UI 접속**:
- URL: http://localhost:8080/swagger-ui.html

---

## 6. 테스트 시나리오

### 6.1 이메일/비밀번호 회원가입 및 로그인

**회원가입**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "SecurePass123!",
    "username": "테스트유저"
  }'
```

**응답**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "email": "test@example.com",
    "username": "테스트유저",
    "provider": "LOCAL",
    "status": "ACTIVE"
  }
}
```

**로그인**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "SecurePass123!"
  }'
```

### 6.2 Google 소셜 로그인

브라우저에서 접속:
```
http://localhost:8080/api/v1/oauth2/authorize/google
```

Google 로그인 → 권한 승인 → 콜백 → Access Token 발급

### 6.3 토큰 갱신

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "{your_refresh_token}"
  }'
```

### 6.4 알림 설정 변경

```bash
curl -X PUT http://localhost:8080/api/v1/notifications/settings \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "emailEnabled": true,
    "pushEnabled": false,
    "newSubscriptionEnabled": true,
    "expiringSubscriptionEnabled": false
  }'
```

---

## 7. Redis 데이터 확인

```bash
# Refresh Token 확인
docker exec -it zipduck-redis redis-cli
> KEYS refresh_token:*
> GET refresh_token:{token_value}

# 로그인 시도 횟수 확인
> GET login_attempts:test@example.com

# TTL 확인
> TTL refresh_token:{token_value}
```

---

## 8. 이메일 발송 테스트

### 로컬 테스트 (AWS SES Sandbox)

1. AWS Console에서 개발자 이메일 verify
2. 신규 청약 생성 (기존 Subscription API 사용)
3. Domain Event 발행 → NotificationEventListener 실행
4. 이메일 수신함 확인

### 실제 이메일 템플릿 확인

`backend/src/main/resources/templates/email/new-subscription.html` 파일 확인

---

## 9. Troubleshooting

### MySQL 접속 실패
```bash
# MySQL 컨테이너 로그 확인
docker logs zipduck-mysql

# 포트 충돌 확인
lsof -i :3306
```

### Redis 접속 실패
```bash
# Redis 컨테이너 상태 확인
docker ps | grep redis

# Redis CLI 접속 테스트
docker exec -it zipduck-redis redis-cli ping
```

### OAuth 콜백 에러
- Redirect URI가 정확히 일치하는지 확인
- Google/Kakao 콘솔에서 URI 재확인
- 로컬 환경에서는 `http://localhost:8080` 사용 (HTTPS 불필요)

### 이메일 발송 실패
- AWS SES에서 발신/수신 이메일 모두 verified 확인
- IAM Access Key 권한 확인 (`ses:SendEmail`)
- CloudWatch Logs에서 에러 확인

---

## 10. Next Steps

- ✅ 로컬 환경 설정 완료
- ⏸️ `/speckit.tasks` 실행하여 구현 작업 분해
- ⏸️ TDD 방식으로 테스트 먼저 작성
- ⏸️ 도메인 엔티티부터 구현 시작

---

**문제 발생 시**: GitHub Issues 또는 팀 Slack 채널에 문의
