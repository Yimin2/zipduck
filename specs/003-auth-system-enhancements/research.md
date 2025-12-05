# Research & Technical Decisions

**Feature**: 인증/인가 시스템 및 알림 기능 구현
**Date**: 2025-11-26
**Status**: ✅ Completed

이 문서는 `/speckit.plan` Phase 0의 결과물로, 구현 전 기술적 의사결정을 기록합니다.

---

## 1. OAuth 2.0 통합 방식

### Decision
**Spring Security OAuth2 Client** 사용

### Rationale
1. **Spring Security와 완벽한 통합**: 기존 Spring Security 6 인프라를 그대로 활용하면서 OAuth2 추가 가능
2. **표준 준수**: OAuth 2.0 Authorization Code Flow를 표준에 맞게 구현
3. **유지보수성**: Spring 팀의 지속적인 업데이트와 보안 패치 제공

### Alternatives Considered
1. **직접 구현 (HttpClient + 수동 처리)**: 
   - 거부 사유: 보안 취약점 리스크, OAuth 2.0 스펙 준수 어려움, 유지보수 부담
2. **외부 라이브러리 (ScribeJava, Pac4j)**:
   - 거부 사유: Spring Security와 중복 기능, 불필요한 dependency 증가

### Implementation Notes

**의존성 추가**:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

**Google Provider 설정** (`application.yml`):
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: profile, email
            redirect-uri: "{baseUrl}/api/v1/oauth2/callback/google"
        provider:
          google:
            authorization-uri: https://accounts.google.com/o/oauth2/v2/auth
            token-uri: https://oauth2.googleapis.com/token
            user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo
```

**Kakao Provider 설정**:
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
            client-authentication-method: client_secret_post
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/api/v1/oauth2/callback/kakao"
            scope: profile_nickname, account_email
        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            user-name-attribute: id
```

**계정 연동 플로우**:
1. OAuth2 인증 완료 후 email 추출
2. 기존 User 조회 (email 기준)
3. 존재하면 → 사용자에게 연동 확인 UI 표시
4. 연동 승인 시 → User 엔티티에 provider, providerId 업데이트
5. 연동 거부 시 → 로그인 취소, 에러 응답

**주의사항**:
- Kakao는 `user-name-attribute: id` 필수 (기본값이 'sub'가 아님)
- Redirect URI는 정확히 OAuth2 Provider Console에 등록된 값과 일치해야 함
- Client Secret은 환경변수로 관리 (절대 코드에 하드코딩 금지)

---

## 2. Refresh Token 관리 전략

### Decision
**Redis 기반 Token Rotation 전략**

### Rationale
1. **TTL 자동 관리**: Redis EXPIRE로 7일 후 자동 삭제, 별도 배치 작업 불필요
2. **빠른 조회**: 메모리 기반으로 토큰 검증 시 낮은 latency
3. **보안 강화**: Token Rotation으로 탈취된 토큰 재사용 방지

### Alternatives Considered
1. **MySQL 저장**:
   - 거부 사유: 만료된 토큰 정리를 위한 배치 작업 필요, 조회 성능 낮음
2. **Rotation 없이 단순 저장**:
   - 거부 사유: 토큰 탈취 시 7일간 악용 가능, 보안 취약

### Implementation Notes

**Redis 키 구조**:
```
refresh_token:{tokenValue} → userId (String)
user:{userId}:refresh_tokens → Set<tokenValue>
login_attempts:{email} → count (Integer, TTL 30분)
```

**Token Rotation 전략**:
1. Refresh Token으로 Access Token 갱신 요청 시
2. 기존 Refresh Token을 Redis에서 검증
3. **새로운 Refresh Token 발급** (기존 것 무효화)
4. 새로운 Access Token + 새로운 Refresh Token 응답
5. 클라이언트는 새 Refresh Token으로 교체

**재사용 감지**:
- 이미 사용된(무효화된) Refresh Token으로 요청 시
- 해당 사용자의 모든 Refresh Token 즉시 무효화
- 강제 로그아웃 및 재로그인 요구

**로그아웃 처리**:
```java
// 1. 특정 토큰만 무효화 (단일 기기 로그아웃)
redisTemplate.delete("refresh_token:" + tokenValue);
redisTemplate.opsForSet().remove("user:" + userId + ":refresh_tokens", tokenValue);

// 2. 모든 토큰 무효화 (전체 기기 로그아웃)
Set<String> tokens = redisTemplate.opsForSet().members("user:" + userId + ":refresh_tokens");
tokens.forEach(token -> redisTemplate.delete("refresh_token:" + token));
redisTemplate.delete("user:" + userId + ":refresh_tokens");
```

**보안 Best Practices**:
- Refresh Token은 HttpOnly Cookie 또는 Secure Storage에만 저장
- Access Token은 클라이언트 메모리에만 보관 (LocalStorage 사용 금지)
- Refresh Token 값은 UUID v4 또는 cryptographically secure random
- Redis 연결은 TLS 암호화 필수

---

## 3. Domain Event 구현 방식

### Decision
**Spring ApplicationEventPublisher + @TransactionalEventListener(phase = AFTER_COMMIT)**

### Rationale
1. **Spring 내장 기능**: 추가 dependency 없이 사용 가능, 러닝 커브 낮음
2. **트랜잭션 안정성**: AFTER_COMMIT으로 DB 커밋 성공 후에만 이벤트 발행
3. **비동기 처리**: @Async와 결합하여 알림 발송이 Subscription 생성을 blocking하지 않음

### Alternatives Considered
1. **커스텀 Event Bus (Guava EventBus, MBassador)**:
   - 거부 사유: 불필요한 복잡도, Spring 표준 이탈, 유지보수 부담
2. **메시지 큐 (RabbitMQ, Kafka)**:
   - 거부 사유: 현재 규모(10K users)에 과도한 인프라, 운영 복잡도 증가

### Implementation Notes

**이벤트 발행**:
```java
@Service
@Transactional
public class SubscriptionCommandService {
    private final ApplicationEventPublisher eventPublisher;
    
    public Subscription create(SubscriptionRequest request) {
        Subscription subscription = subscriptionRepository.save(...);
        
        // 트랜잭션 커밋 후 이벤트 발행됨
        eventPublisher.publishEvent(new SubscriptionCreatedEvent(subscription.getId()));
        
        return subscription;
    }
}
```

**이벤트 리스닝**:
```java
@Component
public class NotificationEventListener {
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSubscriptionCreated(SubscriptionCreatedEvent event) {
        // 새 트랜잭션에서 실행됨
        // 알림 대상 사용자 조회 후 이메일 발송
    }
}
```

**트랜잭션 경계**:
- **AFTER_COMMIT**: Subscription 저장 트랜잭션이 성공적으로 커밋된 후에만 이벤트 처리
- 이벤트 리스너는 **새로운 트랜잭션**에서 실행 (@Transactional(propagation = REQUIRES_NEW))
- 알림 발송 실패해도 Subscription 생성은 성공 (느슨한 결합)

**비동기 처리**:
- `@Async`로 이벤트 리스너를 비동기 실행
- 기존 AsyncConfig의 ThreadPoolTaskExecutor 활용
- Core: 10, Max: 50, Queue: 100

**에러 핸들링**:
- 이벤트 리스너 내부에서 try-catch로 모든 예외 처리
- 알림 발송 실패 시 Notification 엔티티에 FAILED 상태 기록
- 재시도는 별도 스케줄러에서 FAILED 상태 알림 재처리

**주의사항**:
- @TransactionalEventListener는 Spring 4.2+ 필요 (Spring Boot 3.x는 문제없음)
- 이벤트 객체는 불변(immutable)으로 설계 권장
- 이벤트 리스너 간 순서 보장 불가 (필요 시 @Order 사용)

---

## 4. 이메일 발송 인프라 선택

### Decision
**AWS SES (Simple Email Service) + Thymeleaf 템플릿**

### Rationale
1. **비용 효율성**: 월 62,000건까지 무료, 이후 $0.10/1000건 (SendGrid보다 저렴)
2. **AWS 통합**: 기존 S3 사용 중이므로 AWS 계정 재사용, IAM 기반 보안
3. **높은 전송률**: 99% 전송률 보장, 스팸 필터링 우수

### Alternatives Considered
1. **Spring Mail (SMTP)**:
   - 거부 사유: Gmail/Yahoo SMTP는 일일 발송 제한(500건), 스팸 처리 위험 높음
2. **SendGrid**:
   - 거부 사유: 무료 플랜 100건/day로 부족, 유료 플랜 $19.95/month부터 시작

### Implementation Notes

**의존성 추가**:
```xml
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-java-sdk-ses</artifactId>
    <version>1.12.x</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

**AWS SES 설정**:
```yaml
aws:
  ses:
    region: ap-northeast-2  # 서울 리전
    from-email: noreply@zipduck.com
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}
```

**Thymeleaf 템플릿 예시** (`templates/email/new-subscription.html`):
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <h1>새로운 청약이 등록되었습니다!</h1>
    <p th:text="${userName}">사용자님,</p>
    <p>귀하의 조건에 맞는 새로운 청약이 등록되었습니다.</p>
    <h3 th:text="${subscriptionName}">청약 이름</h3>
    <a th:href="${detailUrl}">자세히 보기</a>
</body>
</html>
```

**재시도 메커니즘**:
```java
@Retryable(
    value = {SesException.class, MessagingException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 2000, multiplier = 2)
)
public void sendEmail(String to, String subject, String body) {
    // AWS SES 발송 로직
}
```

**개발 환경 테스트**:
1. **로컬**: AWS SES Sandbox 모드 사용 (verified email만 발송 가능)
2. **검증된 이메일 추가**: AWS Console에서 개발자 이메일 verify
3. **운영 환경**: Production access 신청 (AWS 승인 필요, 24시간 소요)

**템플릿 엔진 선택 (Thymeleaf)**:
- Spring Boot 공식 지원, 자동 설정
- HTML 기반으로 디자이너 협업 용이
- 서버사이드 렌더링 성능 우수

**주의사항**:
- AWS SES는 발신 이메일 도메인 verify 필수 (DNS TXT 레코드 추가)
- Sandbox 모드에서는 수신자 이메일도 verify 필요
- 발송 실패 시 바운스/컴플레인 모니터링 필수 (높으면 계정 정지)

---

## 5. Database Migration 전략

### Decision
**Flyway + Versioned SQL Scripts**

### Rationale
1. **명시적 제어**: SQL 스크립트로 정확한 스키마 변경 가능, DDL 자동 생성보다 안전
2. **롤백 지원**: 각 버전마다 rollback 스크립트 작성 가능
3. **팀 협업**: Git으로 마이그레이션 이력 관리, 코드 리뷰 가능

### Alternatives Considered
1. **Liquibase**:
   - 거부 사유: XML/YAML 설정 복잡, SQL이 더 직관적
2. **Hibernate Auto DDL (spring.jpa.hibernate.ddl-auto=update)**:
   - 거부 사유: 운영 환경에서 위험, 의도하지 않은 스키마 변경 가능

### Implementation Notes

**의존성 추가**:
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

**마이그레이션 스크립트** (`resources/db/migration/V1__add_social_login_to_users.sql`):
```sql
-- 1. provider 컬럼 추가 (기본값 'LOCAL')
ALTER TABLE users 
ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL' 
COMMENT '로그인 제공자: LOCAL, GOOGLE, KAKAO';

-- 2. provider_id 컬럼 추가 (nullable)
ALTER TABLE users 
ADD COLUMN provider_id VARCHAR(255) NULL 
COMMENT '소셜 로그인 제공자의 사용자 ID';

-- 3. password를 nullable로 변경
ALTER TABLE users 
MODIFY COLUMN password VARCHAR(255) NULL 
COMMENT 'BCrypt 해시 비밀번호 (소셜 로그인 시 NULL)';

-- 4. 복합 unique 제약조건 추가 (email + provider)
ALTER TABLE users 
ADD CONSTRAINT uk_users_email_provider UNIQUE (email, provider);

-- 5. provider_id에 인덱스 추가 (소셜 로그인 조회 최적화)
CREATE INDEX idx_users_provider_id ON users(provider, provider_id);

-- 6. 기존 사용자는 provider='LOCAL'로 이미 설정됨 (DEFAULT 값)
-- 추가 업데이트 불필요
```

**롤백 스크립트** (`resources/db/migration/U1__rollback_social_login.sql`):
```sql
-- 1. 인덱스 삭제
DROP INDEX idx_users_provider_id ON users;

-- 2. unique 제약조건 삭제
ALTER TABLE users DROP CONSTRAINT uk_users_email_provider;

-- 3. provider, provider_id 컬럼 삭제
ALTER TABLE users DROP COLUMN provider;
ALTER TABLE users DROP COLUMN provider_id;

-- 4. password를 NOT NULL로 복구
ALTER TABLE users 
MODIFY COLUMN password VARCHAR(255) NOT NULL;
```

**기존 사용자 처리**:
- `DEFAULT 'LOCAL'` 덕분에 기존 모든 사용자는 자동으로 provider='LOCAL' 설정됨
- password는 기존값 유지 (NULL 아님)
- 별도 UPDATE 쿼리 불필요

**Unique Constraint 전략**:
- 기존: `UNIQUE(email)`
- 변경: `UNIQUE(email, provider)`
- 이유: 같은 이메일로 LOCAL + GOOGLE + KAKAO 각각 계정 가능
- 주의: 계정 연동 시 중복 방지 로직은 Application Layer에서 처리

**Flyway 설정** (`application.yml`):
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
```

**마이그레이션 실행 순서**:
1. 로컬 개발 환경에서 먼저 테스트
2. 스테이징 환경 배포 및 검증
3. 운영 환경 배포 전 DB 백업
4. 운영 환경 배포 (Flyway 자동 실행)
5. 실패 시 롤백 스크립트 수동 실행 + 애플리케이션 롤백

**주의사항**:
- 마이그레이션 스크립트는 한번 실행되면 절대 수정 금지
- 새로운 변경사항은 새 버전 스크립트로 추가 (V2__, V3__ ...)
- 운영 DB 마이그레이션 전 반드시 백업
- ALTER TABLE은 테이블 잠금을 유발할 수 있으므로 트래픽 낮은 시간대 실행

---

## Summary

| 주제 | 결정 | 핵심 이유 |
|------|------|----------|
| OAuth 2.0 통합 | Spring Security OAuth2 Client | Spring Security 통합, 표준 준수 |
| Refresh Token 관리 | Redis + Token Rotation | TTL 자동 관리, 보안 강화 |
| Domain Event | ApplicationEventPublisher + AFTER_COMMIT | Spring 내장, 트랜잭션 안정성 |
| 이메일 발송 | AWS SES + Thymeleaf | 비용 효율성, AWS 통합 |
| DB Migration | Flyway + SQL Scripts | 명시적 제어, 롤백 지원 |

**다음 단계**: Phase 1 (Design & Contracts)로 진행 가능
