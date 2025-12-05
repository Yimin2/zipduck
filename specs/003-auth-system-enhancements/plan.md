# Implementation Plan: 인증/인가 시스템 및 알림 기능 구현

**Branch**: `003-auth-system-enhancements` | **Date**: 2025-11-26 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-auth-system-enhancements/spec.md`

## Summary

DDD(Domain-Driven Design) 중심 설계로 전환하면서 기존 User 및 Subscription 도메인을 확장하고, Authentication을 새로운 Bounded Context로 분리합니다. 주요 기능은 다음과 같습니다:

1. **인증/인가 시스템**: 이메일/비밀번호 기반 회원가입, 로그인, 로그아웃, JWT 토큰 발급/갱신 (Access + Refresh Token)
2. **소셜 로그인**: Google, Kakao OAuth 2.0 통합
3. **알림 시스템**: 이메일 알림 (신규 청약, 마감 임박), Domain Event 기반 통합
4. **기존 코드 통합**: User 엔티티 확장, CQRS 패턴 준수, JWT 인프라 활용

**기술 접근 방식**:
- 기존 User/UserProfile 엔티티를 확장하여 소셜 로그인 지원 (provider, providerId 추가)
- Redis에 Refresh Token 저장 (7일 TTL)
- Domain Event 패턴으로 Subscription과 Notification 느슨한 결합
- 이메일 알림만 MVP로 구현, 푸시는 인터페이스만 정의

## Technical Context

**Language/Version**: Java 17, Spring Boot 3.x
**Primary Dependencies**:
- Spring Security 6 (기존 활용)
- Spring Data JPA (기존 활용)
- Spring Data Redis (기존 활용)
- JJWT (기존 JWT 인프라 활용)
- BCrypt (기존 활용)
- Google OAuth2 Client (신규)
- Kakao OAuth2 Client (신규)
- Spring Mail / SendGrid / AWS SES (이메일 발송, 신규)
- Spring ApplicationEventPublisher (Domain Event, 기존 Spring 기능)

**Storage**:
- MySQL 8.0+ (User, UserProfile, Notification, LoginHistory 등 영구 데이터)
- Redis 6.0+ (Refresh Token, 로그인 시도 횟수)
- AWS S3 (프로필 이미지, 청약 첨부파일)

**Testing**: JUnit 5, Mockito, Spring Boot Test, TestContainers (MySQL, Redis)
**Target Platform**: Linux server (Spring Boot embedded Tomcat)
**Project Type**: Web (backend + frontend 분리, 기존 구조 유지)
**Performance Goals**:
- 로그인 요청 응답 시간 평균 500ms 이하
- 동시 1,000명 사용자 로그인 처리
- 신규 청약 알림 30분 이내 발송

**Constraints**:
- 기존 layered 아키텍처 유지하면서 DDD 요소 점진적 도입
- User 엔티티 스키마 변경으로 인한 마이그레이션 필요
- 기존 JWT 인프라(24시간 토큰)를 1시간 Access Token + 7일 Refresh Token으로 전환
- 기존 UserProfile.notificationsEnabled 필드 확장하여 알림 시스템 구축

**Scale/Scope**:
- 예상 사용자 수: 10,000명
- 신규 엔티티: 4개 (AuthToken은 Redis, Notification, NotificationSetting, LoginHistory)
- 신규 API 엔드포인트: 약 10개 (인증 5개, 소셜 로그인 2개, 알림 3개)
- 기존 User 엔티티 확장 (2개 필드 추가)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Note**: Constitution 파일이 템플릿 상태이므로, 기존 코드베이스의 아키텍처 패턴과 원칙을 준수합니다.

### 기존 코드베이스 원칙 (발견된 패턴)

✅ **Layered Architecture**: API → Application → Domain → Infrastructure 계층 분리 유지
✅ **CQRS Pattern**: Command/Query Service 분리 패턴 준수
✅ **Repository Pattern**: Spring Data JPA Repository 활용
✅ **DTO Pattern**: Request/Response DTO 분리
✅ **Exception Handling**: GlobalExceptionHandler를 통한 중앙 집중식 에러 처리
✅ **Auditing**: BaseEntity의 createdAt, updatedAt 자동 관리
✅ **Security**: Spring Security + JWT 기반 인증
✅ **Async Processing**: @Async 및 ThreadPoolTaskExecutor 활용

### 이번 기능에서 준수할 원칙

✅ **기존 구조 준수**: 기존 layered 구조와 CQRS 패턴 유지
✅ **점진적 DDD 도입**: 기존 코드를 리팩토링하지 않고 새 기능에만 DDD 요소 적용
✅ **느슨한 결합**: Domain Event로 Subscription-Notification 결합도 최소화
✅ **테스트 가능성**: 각 레이어별 단위 테스트 및 통합 테스트
✅ **확장 가능성**: 푸시 알림 인터페이스 정의, 향후 확장 용이

### Complexity Justification

이 기능은 복잡도 증가 없이 기존 패턴을 따릅니다. 새로운 요소는 다음과 같이 정당화됩니다:

| 요소 | 이유 | 대안 거부 사유 |
|------|------|---------------|
| Domain Event 패턴 | Subscription과 Notification 도메인 간 느슨한 결합 필요 | 직접 호출은 강한 결합 야기, 배치 작업은 실시간성 부족 |
| Redis Token 저장 | TTL 자동 관리, 빠른 조회, 7일 후 자동 삭제 | MySQL은 배치 정리 필요, 성능 낮음 |
| User 엔티티 확장 | 기존 엔티티 재사용, 데이터 일관성 유지 | 별도 테이블은 조인 오버헤드, 복잡도 증가 |

## Project Structure

### Documentation (this feature)

```text
specs/003-auth-system-enhancements/
├── spec.md              # Feature specification
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   ├── auth-api.yaml            # 인증 API OpenAPI 스펙
│   ├── oauth-api.yaml           # 소셜 로그인 API OpenAPI 스펙
│   └── notification-api.yaml    # 알림 API OpenAPI 스펙
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/zipduck/
│   ├── api/                     # HTTP Layer
│   │   ├── controller/
│   │   │   ├── AuthController.java          # NEW: 인증 API
│   │   │   ├── OAuthController.java         # NEW: 소셜 로그인 API
│   │   │   ├── NotificationController.java  # NEW: 알림 API
│   │   │   └── UserController.java          # EXISTING: 프로필 관리 (확장)
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── SignupRequest.java       # NEW
│   │   │   │   ├── LoginRequest.java        # NEW
│   │   │   │   ├── RefreshTokenRequest.java # NEW
│   │   │   │   └── NotificationSettingRequest.java # NEW
│   │   │   └── response/
│   │   │       ├── AuthResponse.java        # NEW: 토큰 응답
│   │   │       ├── UserResponse.java        # NEW: 사용자 정보 응답
│   │   │       └── NotificationResponse.java # NEW
│   │   └── exception/
│   │       ├── AuthenticationException.java  # NEW
│   │       ├── OAuth2Exception.java          # NEW
│   │       └── GlobalExceptionHandler.java  # EXISTING: 확장
│   │
│   ├── application/              # Application Layer
│   │   └── event/
│   │       ├── SubscriptionCreatedEvent.java    # NEW: Domain Event
│   │       ├── SubscriptionExpiringEvent.java   # NEW: Domain Event
│   │       └── NotificationEventListener.java   # NEW: Event Handler
│   │
│   ├── domain/                   # Domain Layer
│   │   ├── auth/                             # NEW: Authentication Bounded Context
│   │   │   ├── AuthenticationService.java    # NEW: 인증 도메인 서비스
│   │   │   ├── TokenService.java             # NEW: 토큰 관리 서비스
│   │   │   ├── OAuth2Service.java            # NEW: 소셜 로그인 서비스
│   │   │   ├── RefreshToken.java             # NEW: Value Object (Redis)
│   │   │   └── LoginAttemptTracker.java      # NEW: 로그인 시도 추적
│   │   │
│   │   ├── user/                             # EXISTING: User Domain (확장)
│   │   │   ├── User.java                     # MODIFY: provider, providerId 추가
│   │   │   ├── UserProfile.java              # MODIFY: 알림 설정 확장
│   │   │   ├── UserCommandService.java       # MODIFY: 소셜 로그인 지원
│   │   │   ├── UserQueryService.java         # EXISTING
│   │   │   ├── UserRepository.java           # MODIFY: 소셜 로그인 조회
│   │   │   ├── LoginHistory.java             # NEW: Entity
│   │   │   └── LoginHistoryRepository.java   # NEW
│   │   │
│   │   ├── notification/                     # NEW: Notification Bounded Context
│   │   │   ├── Notification.java             # NEW: Entity
│   │   │   ├── NotificationSetting.java      # NEW: Entity
│   │   │   ├── NotificationCommandService.java # NEW
│   │   │   ├── NotificationQueryService.java # NEW
│   │   │   ├── NotificationRepository.java   # NEW
│   │   │   ├── NotificationSettingRepository.java # NEW
│   │   │   └── EmailNotificationService.java # NEW: 이메일 발송
│   │   │
│   │   ├── subscription/                     # EXISTING: Subscription Domain (확장)
│   │   │   ├── Subscription.java             # MODIFY: Event 발행 추가
│   │   │   ├── SubscriptionCommandService.java # MODIFY: Event 발행
│   │   │   ├── SubscriptionQueryService.java # EXISTING
│   │   │   └── SubscriptionRepository.java   # EXISTING
│   │   │
│   │   └── BaseEntity.java                   # EXISTING: 모든 엔티티 Base
│   │
│   └── infrastructure/           # Infrastructure Layer
│       ├── config/
│       │   ├── SecurityConfig.java           # MODIFY: 인증 엔드포인트 추가
│       │   ├── OAuth2Config.java             # NEW: OAuth2 설정
│       │   ├── RedisConfig.java              # NEW: Redis 설정
│       │   ├── EmailConfig.java              # NEW: 이메일 설정
│       │   ├── AsyncConfig.java              # EXISTING: 알림 비동기 처리
│       │   └── JpaConfig.java                # EXISTING
│       │
│       ├── security/
│       │   ├── JwtService.java               # MODIFY: Access/Refresh Token 분리
│       │   ├── JwtAuthenticationFilter.java  # EXISTING
│       │   ├── OAuth2UserInfo.java           # NEW: OAuth2 사용자 정보 추상화
│       │   ├── GoogleOAuth2UserInfo.java     # NEW
│       │   └── KakaoOAuth2UserInfo.java      # NEW
│       │
│       ├── persistence/
│       │   └── redis/
│       │       ├── RedisTokenRepository.java  # NEW: Refresh Token 저장소
│       │       └── RedisLoginAttemptRepository.java # NEW
│       │
│       └── external/
│           └── email/
│               ├── EmailClient.java           # NEW: 이메일 발송 클라이언트
│               └── EmailTemplate.java         # NEW: 이메일 템플릿
│
└── src/test/java/com/zipduck/
    ├── api/controller/
    │   ├── AuthControllerTest.java            # NEW
    │   ├── OAuthControllerTest.java           # NEW
    │   └── NotificationControllerTest.java    # NEW
    ├── domain/
    │   ├── auth/
    │   │   ├── AuthenticationServiceTest.java # NEW
    │   │   └── TokenServiceTest.java          # NEW
    │   └── notification/
    │       └── NotificationCommandServiceTest.java # NEW
    └── integration/
        ├── AuthIntegrationTest.java           # NEW
        ├── OAuth2IntegrationTest.java         # NEW
        └── NotificationIntegrationTest.java   # NEW

frontend/
└── (기존 프론트엔드 구조 유지, 신규 페이지/컴포넌트 추가)
```

**Structure Decision**: 기존 layered 아키텍처를 유지하면서 DDD의 Bounded Context 개념을 도입합니다. 새로운 `auth`와 `notification` 도메인을 독립적인 패키지로 분리하되, 기존 `user`와 `subscription` 도메인은 최소한의 수정으로 확장합니다. Domain Event는 `application/event` 패키지에서 관리하여 도메인 간 느슨한 결합을 유지합니다.

## Phase 0: Research & Technical Decisions

**Status**: ✅ Completed

다음 research.md 파일에서 다음 항목들을 연구하고 결정합니다:

### Research Topics

1. **OAuth 2.0 통합 방식**
   - Spring Security OAuth2 Client vs 직접 구현
   - Google/Kakao Provider 설정
   - 계정 연동 플로우 구현 방식

2. **Refresh Token 관리 전략**
   - Redis 키 구조 설계 (user:{userId}:refresh_token)
   - Token Rotation 전략 (재사용 감지)
   - 로그아웃 시 토큰 무효화 방식

3. **Domain Event 구현 방식**
   - Spring ApplicationEventPublisher vs 커스텀 Event Bus
   - 이벤트 처리 트랜잭션 경계
   - 비동기 이벤트 처리 전략

4. **이메일 발송 인프라 선택**
   - Spring Mail (SMTP) vs SendGrid vs AWS SES
   - 템플릿 엔진 선택 (Thymeleaf vs FreeMarker)
   - 발송 실패 재시도 메커니즘

5. **Database Migration 전략**
   - User 테이블 스키마 변경 (provider, providerId 추가)
   - 기존 사용자 데이터 마이그레이션 방안
   - 롤백 시나리오

### Expected Outputs in research.md

각 주제에 대해:
- **Decision**: 선택한 기술/방식
- **Rationale**: 선택 이유
- **Alternatives Considered**: 고려한 대안들
- **Implementation Notes**: 구현 시 주의사항

## Phase 1: Design & Contracts

**Status**: ✅ Completed

### Expected Outputs

1. **data-model.md**: 엔티티 및 Value Object 정의
   - User (확장), LoginHistory, Notification, NotificationSetting
   - RefreshToken (Redis), LoginAttempt (Redis)
   - 관계도 및 상태 전이 다이어그램

2. **contracts/**: API 계약 정의
   - `auth-api.yaml`: 회원가입, 로그인, 로그아웃, 토큰 갱신
   - `oauth-api.yaml`: Google/Kakao 로그인 콜백
   - `notification-api.yaml`: 알림 설정 조회/변경

3. **quickstart.md**: 로컬 개발 환경 설정 가이드
   - MySQL, Redis 설정
   - OAuth2 Client ID/Secret 발급 방법
   - 이메일 발송 테스트 설정

## Next Steps

1. ✅ **Phase 0 완료**: research.md 작성 완료
2. ✅ **Phase 1 완료**: data-model.md, contracts/, quickstart.md 생성 완료
3. ⏭️ **Phase 2 진행**: `/speckit.tasks` 명령으로 tasks.md 생성

**Current Status**: Phase 0, Phase 1 완료 - 구현 준비 완료

---

**Note**: 이 계획은 `/speckit.plan` 명령으로 생성되었으며, Phase 0과 Phase 1이 완료되면 `/speckit.tasks` 명령으로 구체적인 작업 분해(tasks.md)를 생성할 수 있습니다.
