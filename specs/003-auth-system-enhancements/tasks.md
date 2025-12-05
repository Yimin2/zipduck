# Implementation Tasks: 인증/인가 시스템 및 알림 기능

**Feature**: 003-auth-system-enhancements
**Created**: 2025-11-27
**Status**: Ready for Implementation

이 문서는 `/speckit.tasks`의 결과물로, 구체적인 구현 작업을 독립적으로 테스트 가능한 단위로 분해합니다.

---

## Task Legend

- `[ ]`: 미완료
- `[x]`: 완료
- `[P]`: 병렬 실행 가능 (다른 태스크와 의존성 없음)
- `[US1]`: User Story 1 (이메일/비밀번호 인증) - Priority P1
- `[US2]`: User Story 2 (소셜 로그인) - Priority P2
- `[US3]`: User Story 3 (토큰 관리) - Priority P1
- `[US4]`: User Story 4 (청약 알림) - Priority P2

**구현 순서**: Setup → Foundational → US1 (P1) → US3 (P1) → US2 (P2) → US4 (P2) → Polish

---

## Phase 0: Setup (환경 구성)

### Dependencies & Configuration

- [x] **T001** [P] [Setup] Add dependencies to `backend/build.gradle`
  ```
  - spring-boot-starter-oauth2-client
  - spring-boot-starter-data-redis
  - spring-boot-starter-thymeleaf
  - jjwt-api, jjwt-impl, jjwt-jackson (JWT 라이브러리)
  - aws-java-sdk-ses (이메일 발송)
  - flyway-core, flyway-mysql (DB 마이그레이션)
  ```
  **Test**: `./gradlew build` 성공 ✅

- [x] **T002** [P] [Setup] Create `application-local.yml` configuration file
  ```
  Location: backend/src/main/resources/application-local.yml
  Include: MySQL, Redis, JWT, OAuth2 (Google/Kakao), AWS SES 설정
  ```
  **Test**: 로컬 환경에서 애플리케이션 실행 성공 ✅

- [x] **T003** [P] [Setup] Create Docker Compose file for local dev environment
  ```
  Location: backend/docker-compose.yml
  Services: MySQL 8.0, Redis 7
  ```
  **Test**: `docker-compose up -d` 실행 후 MySQL/Redis 접속 확인 ✅

- [x] **T004** [Setup] Run Flyway migration scripts (V1__add_social_login_to_users.sql)
  ```
  Location: backend/src/main/resources/db/migration/V1__add_social_login_to_users.sql
  Creates: users 테이블 수정 (provider, providerId 추가)
           login_histories, notifications, notification_settings 테이블 생성
  ```
  **Test**: Migration 스크립트 생성 완료 ✅ (실행은 Docker 시작 후)

---

## Phase 1: Foundational (기반 구조)

### Domain Layer - Enums & Value Objects

- [x] **T005** [P] [Foundation] Create `AuthProvider` enum
  ```
  Location: backend/src/main/java/com/zipduck/domain/user/AuthProvider.java
  Values: LOCAL, GOOGLE, KAKAO
  ```
  **Test**: Enum 값 생성 및 비교 테스트 ✅

- [x] **T006** [P] [Foundation] Create `UserStatus` enum
  ```
  Location: backend/src/main/java/com/zipduck/domain/user/UserStatus.java
  Values: ACTIVE, INACTIVE, SUSPENDED
  ```
  **Already exists in User.java** ✅

- [x] **T007** [P] [Foundation] Create `NotificationType`, `NotificationMethod`, `NotificationStatus` enums
  ```
  Location: backend/src/main/java/com/zipduck/domain/notification/
  ```
  ✅

### Domain Layer - Entities (Extend Existing)

- [x] **T008** [Foundation] Extend `User` entity with social login fields
  ```
  Location: backend/src/main/java/com/zipduck/domain/user/User.java
  Add: provider, providerId, isSocialLogin(), canLoginWithPassword()
  Modify: password nullable
  ```
  **Test**: UserRepositoryTest - 소셜 로그인 사용자 저장/조회 ✅

- [x] **T009** [P] [Foundation] Create `LoginHistory` entity
  ```
  Location: backend/src/main/java/com/zipduck/domain/user/LoginHistory.java
  Repository: LoginHistoryRepository
  ```
  **Test**: 로그인 기록 저장 및 조회 테스트 ✅

- [x] **T010** [P] [Foundation] Create `Notification` entity
  ```
  Location: backend/src/main/java/com/zipduck/domain/notification/Notification.java
  Repository: NotificationRepository
  ```
  **Test**: 알림 생성 및 상태 변경 테스트 ✅

- [x] **T011** [P] [Foundation] Create `NotificationSetting` entity
  ```
  Location: backend/src/main/java/com/zipduck/domain/notification/NotificationSetting.java
  Repository: NotificationSettingRepository
  Method: shouldSendNotification(type, method)
  ```
  **Test**: 알림 설정에 따른 발송 여부 결정 테스트 ✅

### Infrastructure Layer - Redis

- [x] **T012** [P] [Foundation] Configure Redis connection
  ```
  Location: backend/src/main/java/com/zipduck/infrastructure/config/RedisConfig.java
  Create: RedisTemplate<String, Object>, StringRedisTemplate
  ```
  **Test**: Redis 연결 테스트, PING-PONG 확인 ✅

- [x] **T013** [P] [Foundation] Create `RefreshToken` Redis entity
  ```
  Location: backend/src/main/java/com/zipduck/domain/auth/RefreshToken.java
  TTL: 7 days (604800 seconds)
  Methods: isValid()
  ```
  **Test**: Redis에 토큰 저장/조회/TTL 확인 ✅

- [x] **T014** [P] [Foundation] Create `LoginAttempt` Redis entity
  ```
  Location: backend/src/main/java/com/zipduck/domain/auth/LoginAttempt.java
  TTL: 30 minutes (1800 seconds)
  Methods: increment(), isLocked()
  ```
  **Test**: 로그인 시도 횟수 증가 및 잠금 확인 ✅

---

## Phase 2: User Story 1 - 이메일/비밀번호 인증 (Priority: P1)

### US1: Domain Services

- [x] **T015** [P] [US1] Create `PasswordEncoder` bean (BCrypt)
  ```
  Location: backend/src/main/java/com/zipduck/infrastructure/config/SecurityConfig.java
  Bean: PasswordEncoder (BCryptPasswordEncoder)
  ```
  **Test**: 비밀번호 해시 및 검증 테스트

- [x] **T016** [US1] Implement `AuthenticationService` - 회원가입
  ```
  Location: backend/src/main/java/com/zipduck/domain/auth/AuthenticationService.java
  Method: signup(email, password, username) → User
  Validation: 이메일 중복 확인, 비밀번호 강도 검증
  ```
  **Test**: 유효한 회원가입, 이메일 중복 에러, 약한 비밀번호 에러

- [x] **T017** [US1] Implement `AuthenticationService` - 로그인
  ```
  Method: login(email, password) → AuthResponse
  Features: LoginAttempt 증가, 5회 실패 시 계정 잠금, LoginHistory 기록
  ```
  **Test**: 성공 로그인, 잘못된 비밀번호, 계정 잠금

- [x] **T018** [P] [US1] Create `JwtTokenProvider` service
  ```
  Location: backend/src/main/java/com/zipduck/domain/auth/JwtTokenProvider.java
  Methods: generateAccessToken(userId), validateToken(token), getUserIdFromToken(token)
  ```
  **Test**: 토큰 생성, 검증, 파싱, 만료 처리

### US1: Application Layer

- [x] **T019** [P] [US1] Create `SignupRequest`, `LoginRequest`, `AuthResponse` DTOs
  ```
  Location: backend/src/main/java/com/zipduck/api/auth/dto/
  Validation: @Email, @NotBlank, @Size, @Pattern (비밀번호 정규식)
  ```
  **Test**: Validation 어노테이션 동작 확인

- [x] **T020** [US1] Implement `AuthController` - 회원가입/로그인 엔드포인트
  ```
  Location: backend/src/main/java/com/zipduck/api/auth/AuthController.java
  POST /api/v1/auth/signup
  POST /api/v1/auth/login
  ```
  **Test**: MockMvc 통합 테스트 (성공/실패 시나리오)

### US1: Security Configuration

- [x] **T021** [US1] Create `JwtAuthenticationFilter`
  ```
  Location: backend/src/main/java/com/zipduck/infrastructure/security/JwtAuthenticationFilter.java
  Extract: Authorization 헤더에서 JWT 토큰 추출 및 검증
  ```
  **Test**: 유효한 토큰, 만료된 토큰, 없는 토큰 처리

- [x] **T022** [US1] Configure Spring Security filter chain
  ```
  Location: backend/src/main/java/com/zipduck/infrastructure/config/SecurityConfig.java
  Permit: /api/v1/auth/**, /swagger-ui/**, /v3/api-docs/**
  Authenticate: 나머지 모든 엔드포인트
  ```
  **Test**: 인증 필요한 엔드포인트 접근 제어 확인

---

## Phase 3: User Story 3 - 토큰 관리 (Priority: P1)

### US3: Token Refresh & Rotation

- [x] **T023** [P] [US3] Implement `RefreshTokenService`
  ```
  Location: backend/src/main/java/com/zipduck/domain/auth/RefreshTokenService.java
  Methods:
    - issueRefreshToken(userId) → RefreshToken
    - rotateRefreshToken(oldToken) → RefreshToken
    - revokeRefreshToken(token)
    - revokeAllUserTokens(userId)
  ```
  **Test**: 토큰 발급, Rotation, 재사용 감지, 전체 무효화 ✅

- [x] **T024** [US3] Implement `/api/v1/auth/refresh` endpoint
  ```
  Location: backend/src/main/java/com/zipduck/api/auth/AuthController.java
  POST /api/v1/auth/refresh
  Request: RefreshTokenRequest (refreshToken)
  Response: TokenResponse (new accessToken, new refreshToken)
  ```
  **Test**: 토큰 갱신 성공, 만료된 토큰, 재사용 감지 ✅

- [x] **T025** [US3] Implement `/api/v1/auth/logout` endpoint
  ```
  POST /api/v1/auth/logout
  Security: @PreAuthorize("isAuthenticated()")
  Action: Refresh Token 무효화
  ```
  **Test**: 로그아웃 후 토큰 재사용 불가 확인 ✅

- [x] **T026** [P] [US3] Implement `/api/v1/auth/me` endpoint
  ```
  GET /api/v1/auth/me
  Response: UserResponse (현재 로그인한 사용자 정보)
  ```
  **Test**: 인증된 사용자 정보 조회 ✅

### US3: Security Enhancements

- [x] **T027** [P] [US3] Create `GlobalExceptionHandler` for auth errors
  ```
  Location: backend/src/main/java/com/zipduck/api/exception/GlobalExceptionHandler.java
  Handle: InvalidCredentialsException, AccountLockedException, TokenExpiredException
  Response: ErrorResponse { code, message, details }
  ```
  **Test**: 각 예외 발생 시 표준화된 에러 응답 확인 ✅

---

## Phase 4: User Story 2 - 소셜 로그인 (Priority: P2)

### US2: OAuth2 Configuration

- [x] **T028** [P] [US2] Configure OAuth2 providers in `application.yml`
  ```
  Location: application-local.yml, application-prod.yml
  Providers: Google, Kakao
  Include: client-id, client-secret, redirect-uri, scope
  ```
  **Test**: OAuth2 설정 파싱 확인 ✅ (이미 application-local.yml에 설정됨)

- [x] **T029** [P] [US2] Create `OAuth2UserInfo` interface & implementations
  ```
  Location: backend/src/main/java/com/zipduck/infrastructure/oauth2/
  Classes: GoogleOAuth2UserInfo, KakaoOAuth2UserInfo
  Methods: getEmail(), getName(), getProviderId()
  ```
  **Test**: OAuth2 응답 파싱 테스트 ✅

### US2: OAuth2 Service

- [x] **T030** [US2] Implement `OAuth2Service` - 사용자 정보 처리
  ```
  Location: backend/src/main/java/com/zipduck/domain/auth/OAuth2Service.java
  Methods:
    - processOAuthUser(provider, oauthUser) → AuthResponse or LinkRequiredResponse
    - linkOAuthAccount(tempToken, confirm) → AuthResponse
  Logic: 기존 이메일 확인 → 연동 확인 → 계정 생성/연동
  ```
  **Test**: 신규 사용자, 기존 LOCAL 계정 연동, 연동 거부 ✅

- [x] **T031** [US2] Create `OAuth2Controller` - 인증 시작 및 콜백
  ```
  Location: backend/src/main/java/com/zipduck/api/auth/OAuth2Controller.java
  GET /api/v1/oauth2/authorize/{provider}
  GET /api/v1/oauth2/callback/{provider}
  POST /api/v1/oauth2/link
  ```
  **Test**: OAuth2 플로우 시뮬레이션 (MockOAuth2Server 사용) ✅

### US2: Account Linking

- [x] **T032** [P] [US2] Create temporary token for account linking
  ```
  Location: backend/src/main/java/com/zipduck/domain/auth/LinkToken.java
  Redis TTL: 5 minutes
  Store: email, provider, providerId
  ```
  **Test**: 임시 토큰 생성/검증/만료 ✅

- [x] **T033** [US2] Create `LinkAccountRequest`, `LinkRequiredResponse` DTOs
  ```
  Location: backend/src/main/java/com/zipduck/api/auth/dto/
  ```
  ✅

---

## Phase 5: User Story 4 - 청약 알림 (Priority: P2)

### US4: Domain Events

- [ ] **T034** [P] [US4] Create `SubscriptionCreatedEvent` class
  ```
  Location: backend/src/main/java/com/zipduck/application/event/SubscriptionCreatedEvent.java
  Fields: subscriptionId, occurredAt
  ```

- [ ] **T035** [P] [US4] Create `SubscriptionExpiringEvent` class
  ```
  Location: backend/src/main/java/com/zipduck/application/event/SubscriptionExpiringEvent.java
  Fields: subscriptionId, expiresAt
  ```

- [ ] **T036** [US4] Publish `SubscriptionCreatedEvent` in existing `SubscriptionCommandService`
  ```
  Location: backend/src/main/java/com/zipduck/application/subscription/SubscriptionCommandService.java
  Add: eventPublisher.publishEvent(new SubscriptionCreatedEvent(subscription.getId()))
  ```
  **Test**: Event 발행 확인 (ApplicationEventPublisher mock)

### US4: Email Service

- [ ] **T037** [P] [US4] Configure AWS SES client
  ```
  Location: backend/src/main/java/com/zipduck/infrastructure/config/AwsSesConfig.java
  Bean: AmazonSimpleEmailService
  ```
  **Test**: SES 연결 테스트 (로컬: Sandbox 모드)

- [ ] **T038** [P] [US4] Create Thymeleaf email templates
  ```
  Location: backend/src/main/resources/templates/email/
  Files: new-subscription.html, expiring-subscription.html
  ```
  **Test**: 템플릿 렌더링 테스트

- [ ] **T039** [US4] Implement `EmailNotificationService`
  ```
  Location: backend/src/main/java/com/zipduck/infrastructure/notification/EmailNotificationService.java
  Methods:
    - sendEmail(to, subject, templateName, variables)
    - @Retryable(maxAttempts=3)
  ```
  **Test**: 이메일 발송 성공/실패, 재시도 로직

### US4: Notification Service

- [ ] **T040** [US4] Implement `NotificationService`
  ```
  Location: backend/src/main/java/com/zipduck/domain/notification/NotificationService.java
  Methods:
    - createNotification(userId, type, subscriptionId)
    - sendNotification(notificationId)
    - retryFailedNotifications()
  ```
  **Test**: 알림 생성, 발송, 재시도

- [ ] **T041** [US4] Create `NotificationEventListener`
  ```
  Location: backend/src/main/java/com/zipduck/application/event/NotificationEventListener.java
  @Async
  @TransactionalEventListener(phase = AFTER_COMMIT)
  handleSubscriptionCreated(event)
  ```
  **Test**: Event 수신 후 알림 발송 확인

### US4: Notification API

- [ ] **T042** [P] [US4] Create `NotificationSettingRequest/Response` DTOs
  ```
  Location: backend/src/main/java/com/zipduck/api/notification/dto/
  ```

- [ ] **T043** [US4] Implement `NotificationController`
  ```
  Location: backend/src/main/java/com/zipduck/api/notification/NotificationController.java
  GET /api/v1/notifications/settings
  PUT /api/v1/notifications/settings
  GET /api/v1/notifications (페이징)
  ```
  **Test**: 알림 설정 조회/변경, 알림 이력 조회

### US4: Batch Job (마감 임박 알림)

- [ ] **T044** [P] [US4] Create scheduled task for expiring subscriptions
  ```
  Location: backend/src/main/java/com/zipduck/infrastructure/scheduler/SubscriptionExpiryScheduler.java
  @Scheduled(cron = "0 0 9 * * *")  // 매일 오전 9시
  Logic: 24시간 이내 마감 청약 조회 → SubscriptionExpiringEvent 발행
  ```
  **Test**: 스케줄러 실행 테스트 (고정 시간 설정)

---

## Phase 6: Polish (마무리 & 통합 테스트)

### Integration Tests

- [ ] **T045** [P] [Polish] Write end-to-end test for signup → login → refresh flow
  ```
  Location: backend/src/test/java/com/zipduck/integration/AuthIntegrationTest.java
  Scenario: 회원가입 → 로그인 → Access Token으로 /me 조회 → Refresh → 로그아웃
  Use: @SpringBootTest, TestContainers (MySQL, Redis)
  ```

- [ ] **T046** [P] [Polish] Write end-to-end test for OAuth2 login flow
  ```
  Location: backend/src/test/java/com/zipduck/integration/OAuth2IntegrationTest.java
  Scenario: Google 로그인 → 신규 사용자 생성 → 기존 계정 연동 플로우
  Use: MockOAuth2Server
  ```

- [ ] **T047** [P] [Polish] Write integration test for notification system
  ```
  Location: backend/src/test/java/com/zipduck/integration/NotificationIntegrationTest.java
  Scenario: 청약 생성 → Event 발행 → 알림 생성 → 이메일 발송
  Use: @MockBean for EmailNotificationService
  ```

### Documentation

- [ ] **T048** [P] [Polish] Update Swagger/OpenAPI annotations
  ```
  Location: All @RestController classes
  Add: @Operation, @ApiResponse, @Schema descriptions
  ```
  **Test**: Swagger UI 접속하여 API 문서 확인

- [ ] **T049** [P] [Polish] Create Postman collection for API testing
  ```
  Location: specs/003-auth-system-enhancements/postman/
  Include: 회원가입, 로그인, 토큰 갱신, OAuth2, 알림 설정 요청
  ```

### Performance & Security

- [ ] **T050** [P] [Polish] Add rate limiting for login endpoint
  ```
  Location: SecurityConfig 또는 RateLimitFilter
  Limit: IP당 분당 10회 로그인 시도
  ```
  **Test**: 연속 로그인 시도 시 429 응답 확인

- [ ] **T051** [P] [Polish] Add CORS configuration
  ```
  Location: backend/src/main/java/com/zipduck/infrastructure/config/CorsConfig.java
  Allow: http://localhost:3000 (프론트엔드)
  ```
  **Test**: 프론트엔드에서 API 호출 가능 확인

- [ ] **T052** [P] [Polish] Review and fix security vulnerabilities
  ```
  Action: OWASP Top 10 체크리스트 검토
  - SQL Injection 방지 (JPA 사용)
  - XSS 방지 (Response 인코딩)
  - CSRF 방지 (Stateless JWT)
  - 민감 정보 로깅 방지
  ```

### Cleanup

- [ ] **T053** [Polish] Remove unused code and optimize imports
  ```
  Action: IntelliJ "Optimize Imports", "Remove Unused Code" 실행
  ```

- [ ] **T054** [Polish] Run full test suite and ensure 80%+ coverage
  ```
  Command: ./gradlew test jacocoTestReport
  Verify: JaCoCo report 확인
  ```

---

## Summary

**총 작업 수**: 54개 태스크
**예상 소요 시간**: 3-5일 (1명 개발자 기준)

**작업 우선순위**:
1. **Setup & Foundational** (T001-T014): 모든 작업의 기반
2. **User Story 1 (P1)** (T015-T022): 기본 인증 시스템
3. **User Story 3 (P1)** (T023-T027): 토큰 관리 및 보안
4. **User Story 2 (P2)** (T028-T033): 소셜 로그인
5. **User Story 4 (P2)** (T034-T044): 알림 시스템
6. **Polish** (T045-T054): 통합 테스트 및 마무리

**병렬 처리 가능 태스크** (37개 `[P]` 태스크):
- 독립적인 DTO, Enum, Entity, Service 생성 작업
- 테스트 작성
- 문서화

**Critical Path** (순차 실행 필수):
- T004 (Migration) → T008 (User entity) → T016-T017 (AuthService) → T020 (Controller)
- T036 (Event publish) → T041 (Event listener) → T047 (Integration test)

---

## Next Steps

1. ✅ `/speckit.tasks` 완료
2. ⏸️ `/speckit.implement` 실행하여 구현 시작
3. ⏸️ TDD 방식으로 테스트 먼저 작성
4. ⏸️ Phase별로 체크리스트 업데이트

**문제 발생 시**: GitHub Issues 또는 팀 Slack 채널에 문의
