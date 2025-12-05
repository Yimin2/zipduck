# Data Model: 인증/인가 시스템 및 알림 기능

**Feature**: 003-auth-system-enhancements
**Date**: 2025-11-26
**Status**: Phase 1 Design

이 문서는 새로운 기능의 데이터 모델을 정의합니다. 기존 엔티티 확장과 신규 엔티티를 포함합니다.

---

## Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      User (MODIFIED)                         │
├─────────────────────────────────────────────────────────────┤
│ PK  id: Long                                                 │
│     username: String                                         │
│ UK  email: String                                            │
│     password: String (NULLABLE - 소셜 로그인 시 NULL)       │
│ NEW provider: Enum (LOCAL, GOOGLE, KAKAO)                   │
│ NEW providerI: String (nullable)                           │
│     status: Enum (ACTIVE, INACTIVE, SUSPENDED)              │
│     createdAt: LocalDateTime                                 │
│     updatedAt: LocalDateTime                                 │
└─────────────────────────────────────────────────────────────┘
       │ 1                    1 │                    1 │
       │                        │                      │
       │ 1:1                    │ 1:N                  │ 1:1
       ▼                        ▼                      ▼
┌──────────────────┐   ┌─────────────────┐   ┌──────────────────────┐
│  UserProfile     │   │  LoginHistory   │   │ NotificationSetting  │
│   (MODIFIED)     │   │     (NEW)       │   │       (NEW)          │
├──────────────────┤   ├─────────────────┤   ├──────────────────────┤
│ PK id: Long      │   │ PK id: Long     │   │ PK id: Long          │
│ FK userId: Long  │   │ FK userId: Long │   │ FK userId: Long      │
│ age: Integer     │   │ loginAt:        │   │ emailEnabled:        │
│ annualIncome:    │   │   LocalDateTime │   │   Boolean            │
│   Long           │   │ provider: Enum  │   │ pushEnabled:         │
│ householdMembers │   │ success: Boolean│   │   Boolean (향후용)   │
│ housingOwned     │   │ ipAddress: Str  │   │ newSubscription:     │
│ location         │   │ userAgent: Str  │   │   Enabled: Boolean   │
│   Preferences    │   │ failureReason   │   │ expiringSubscription │
│ notifications    │   │   : String      │   │   Enabled: Boolean   │
│   Enabled (기존) │   └─────────────────┘   │ createdAt            │
└──────────────────┘                         │ updatedAt            │
                                              └──────────────────────┘
       
                             1 │
                               │
                               │ N:1
                               ▼
                      ┌──────────────────────┐
                      │   Notification (NEW) │
                      ├──────────────────────┤
                      │ PK id: Long          │
                      │ FK userId: Long      │
                      │ FK subscriptionId    │
                      │ type: Enum (NEW_SUB, │
                      │         EXPIRING)    │
                      │ title: String        │
                      │ content: String      │
                      │ method: Enum (EMAIL, │
                      │           PUSH)      │
                      │ status: Enum (PENDING│
                      │    ,SENT, FAILED)    │
                      │ sentAt: LocalDateTime│
                      │ createdAt            │
                      │ updatedAt            │
                      └──────────────────────┘
                               │ N:1
                               │
                               ▼
                      ┌──────────────────────┐
                      │ Subscription (기존)  │
                      │   (Event 발행 추가)  │
                      └──────────────────────┘

Redis (별도 저장소):
┌───────────────────────────────────────┐
│ RefreshToken (Value Object)           │
├───────────────────────────────────────┤
│ Key: refresh_token:{tokenValue}       │
│ Value: userId (String)                │
│ TTL: 7 days                            │
└───────────────────────────────────────┘

┌───────────────────────────────────────┐
│ LoginAttempt (Value Object)           │
├───────────────────────────────────────┤
│ Key: login_attempts:{email}           │
│ Value: count (Integer)                │
│ TTL: 30 minutes                        │
└───────────────────────────────────────┘
```

---

## Entities

### 1. User (기존 엔티티 확장)

**Location**: `backend/src/main/java/com/zipduck/domain/user/User.java`

**Changes**:
- ✅ 기존 필드 유지: id, username, email, password, status
- ➕ 신규 필드 추가: provider, providerId
- 🔧 수정: password nullable로 변경

```java
@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(columnNames = {"email", "provider"}))
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String email;
    
    @Column(nullable = true)  // MODIFIED: 소셜 로그인 시 NULL
    private String password;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider = AuthProvider.LOCAL;  // NEW
    
    @Column(name = "provider_id")
    private String providerId;  // NEW: 소셜 로그인 제공자의 사용자 ID
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;
    
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserProfile profile;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<LoginHistory> loginHistories = new ArrayList<>();
    
    // 소셜 로그인 여부 확인
    public boolean isSocialLogin() {
        return provider != AuthProvider.LOCAL;
    }
    
    // 비밀번호 로그인 가능 여부
    public boolean canLoginWithPassword() {
        return provider == AuthProvider.LOCAL && password != null;
    }
}

public enum AuthProvider {
    LOCAL,
    GOOGLE,
    KAKAO
}

public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
```

**Validation Rules**:
- email: 이메일 형식, 필수
- password: 소셜 로그인 시 NULL 허용, 일반 로그인 시 8자 이상
- provider: 기본값 LOCAL
- (email, provider) 조합은 unique

**State Transitions**:
```
[회원가입] → ACTIVE
ACTIVE → SUSPENDED (비밀번호 5회 실패)
SUSPENDED → ACTIVE (30분 경과 또는 이메일 인증)
ACTIVE → INACTIVE (탈퇴)
```

---

### 2. LoginHistory (신규 엔티티)

**Location**: `backend/src/main/java/com/zipduck/domain/user/LoginHistory.java`

```java
@Entity
@Table(name = "login_histories",
       indexes = {
           @Index(name = "idx_user_id_login_at", columnList = "user_id,login_at DESC")
       })
public class LoginHistory extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private LocalDateTime loginAt;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;
    
    @Column(nullable = false)
    private Boolean success;
    
    @Column(length = 45)  // IPv6 최대 길이
    private String ipAddress;
    
    @Column(length = 500)
    private String userAgent;
    
    @Column(length = 255)
    private String failureReason;  // 실패 시 이유 (예: "Invalid password")
}
```

**Purpose**: 보안 감사, 이상 로그인 탐지, 사용자 로그인 이력 조회

**Indexes**:
- `(user_id, login_at DESC)`: 사용자별 최근 로그인 이력 조회 최적화

---

### 3. Notification (신규 엔티티)

**Location**: `backend/src/main/java/com/zipduck/domain/notification/Notification.java`

```java
@Entity
@Table(name = "notifications",
       indexes = {
           @Index(name = "idx_user_id_created_at", columnList = "user_id,created_at DESC"),
           @Index(name = "idx_status_created_at", columnList = "status,created_at")
       })
public class Notification extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "subscription_id")
    private Long subscriptionId;  // Subscription과 느슨한 결합
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(nullable = false, length = 1000)
    private String content;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationMethod method;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;
    
    @Column
    private LocalDateTime sentAt;
    
    @Column(length = 500)
    private String failureReason;  // 발송 실패 시 이유
}

public enum NotificationType {
    NEW_SUBSCRIPTION,      // 신규 청약 등록
    EXPIRING_SUBSCRIPTION  // 마감 임박 (24시간 이내)
}

public enum NotificationMethod {
    EMAIL,
    PUSH  // 향후 확장용
}

public enum NotificationStatus {
    PENDING,  // 발송 대기
    SENT,     // 발송 완료
    FAILED    // 발송 실패
}
```

**Validation Rules**:
- title: 최대 200자
- content: 최대 1000자
- subscriptionId: NULL 허용 (시스템 알림인 경우)

**State Transitions**:
```
[생성] → PENDING
PENDING → SENT (발송 성공)
PENDING → FAILED (발송 실패)
FAILED → PENDING (재시도 시)
```

**Indexes**:
- `(user_id, created_at DESC)`: 사용자별 알림 조회
- `(status, created_at)`: 재시도 대상 조회 (status=FAILED)

---

### 4. NotificationSetting (신규 엔티티)

**Location**: `backend/src/main/java/com/zipduck/domain/notification/NotificationSetting.java`

```java
@Entity
@Table(name = "notification_settings",
       uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class NotificationSetting extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private Boolean emailEnabled = true;  // 이메일 알림 활성화
    
    @Column(nullable = false)
    private Boolean pushEnabled = false;  // 푸시 알림 활성화 (향후용)
    
    @Column(nullable = false)
    private Boolean newSubscriptionEnabled = true;  // 신규 청약 알림
    
    @Column(nullable = false)
    private Boolean expiringSubscriptionEnabled = true;  // 마감 임박 알림
    
    // 사용자가 알림을 받을지 여부
    public boolean shouldSendNotification(NotificationType type, NotificationMethod method) {
        // 메소드별 활성화 확인
        if (method == NotificationMethod.EMAIL && !emailEnabled) return false;
        if (method == NotificationMethod.PUSH && !pushEnabled) return false;
        
        // 타입별 활성화 확인
        if (type == NotificationType.NEW_SUBSCRIPTION && !newSubscriptionEnabled) return false;
        if (type == NotificationType.EXPIRING_SUBSCRIPTION && !expiringSubscriptionEnabled) return false;
        
        return true;
    }
}
```

**Note**: 기존 `UserProfile.notificationsEnabled`를 확장하는 개념으로, 더 세밀한 알림 설정 제공

---

## Value Objects (Redis)

### 5. RefreshToken

**Location**: `backend/src/main/java/com/zipduck/domain/auth/RefreshToken.java`

```java
@RedisHash(value = "refresh_token", timeToLive = 604800) // 7일 = 604800초
public class RefreshToken {
    @Id
    private String token;  // UUID v4
    
    @Indexed
    private Long userId;
    
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    
    // 토큰이 유효한지 확인
    public boolean isValid() {
        return LocalDateTime.now().isBefore(expiresAt);
    }
}
```

**Redis Key Structure**:
```
refresh_token:{tokenValue} → RefreshToken 객체
user:{userId}:refresh_tokens → Set<tokenValue>
```

---

### 6. LoginAttempt

**Location**: `backend/src/main/java/com/zipduck/domain/auth/LoginAttempt.java`

```java
@RedisHash(value = "login_attempt", timeToLive = 1800) // 30분 = 1800초
public class LoginAttempt {
    @Id
    private String email;
    
    private Integer count = 0;
    private LocalDateTime lastAttemptAt;
    
    public void increment() {
        this.count++;
        this.lastAttemptAt = LocalDateTime.now();
    }
    
    public boolean isLocked() {
        return count >= 5;
    }
}
```

**Redis Key Structure**:
```
login_attempt:{email} → LoginAttempt 객체
```

---

## Domain Events

### SubscriptionCreatedEvent

```java
public class SubscriptionCreatedEvent {
    private final Long subscriptionId;
    private final LocalDateTime occurredAt;
    
    public SubscriptionCreatedEvent(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
        this.occurredAt = LocalDateTime.now();
    }
}
```

### SubscriptionExpiringEvent

```java
public class SubscriptionExpiringEvent {
    private final Long subscriptionId;
    private final LocalDateTime expiresAt;
    
    public SubscriptionExpiringEvent(Long subscriptionId, LocalDateTime expiresAt) {
        this.subscriptionId = subscriptionId;
        this.expiresAt = expiresAt;
    }
}
```

---

## Database Migration Script

**File**: `resources/db/migration/V1__add_social_login_to_users.sql`

```sql
-- 1. provider 컬럼 추가
ALTER TABLE users 
ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

-- 2. provider_id 컬럼 추가
ALTER TABLE users 
ADD COLUMN provider_id VARCHAR(255) NULL;

-- 3. password nullable 변경
ALTER TABLE users 
MODIFY COLUMN password VARCHAR(255) NULL;

-- 4. unique constraint 수정
ALTER TABLE users 
DROP INDEX IF EXISTS uk_email;

ALTER TABLE users 
ADD CONSTRAINT uk_users_email_provider UNIQUE (email, provider);

-- 5. 인덱스 추가
CREATE INDEX idx_users_provider_id ON users(provider, provider_id);

-- 6. login_histories 테이블 생성
CREATE TABLE login_histories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    login_at DATETIME NOT NULL,
    provider VARCHAR(20) NOT NULL,
    success BOOLEAN NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    failure_reason VARCHAR(255),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id_login_at (user_id, login_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. notifications 테이블 생성
CREATE TABLE notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    subscription_id BIGINT,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at DATETIME,
    failure_reason VARCHAR(500),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id_created_at (user_id, created_at DESC),
    INDEX idx_status_created_at (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. notification_settings 테이블 생성
CREATE TABLE notification_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    new_subscription_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expiring_subscription_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Data Integrity Rules

1. **User-UserProfile**: 1:1 관계, CASCADE 삭제
2. **User-LoginHistory**: 1:N 관계, CASCADE 삭제
3. **User-NotificationSetting**: 1:1 관계, CASCADE 삭제
4. **User-Notification**: 1:N 관계, CASCADE 삭제
5. **Subscription-Notification**: 느슨한 결합 (FK 없음, subscriptionId만 저장)

**Orphan 방지**:
- User 삭제 시 관련된 모든 데이터 CASCADE 삭제
- Subscription 삭제 시 Notification은 유지 (이력 보존)

---

## Performance Considerations

1. **Indexes**:
   - `users(provider, provider_id)`: 소셜 로그인 조회
   - `login_histories(user_id, login_at)`: 최근 로그인 이력
   - `notifications(user_id, created_at)`: 사용자별 알림 조회
   - `notifications(status, created_at)`: 재시도 대상 조회

2. **Redis TTL**:
   - RefreshToken: 7일 (자동 만료)
   - LoginAttempt: 30분 (자동 해제)

3. **Lazy Loading**:
   - User-UserProfile: LAZY (필요 시만 조회)
   - User-LoginHistories: LAZY (대량 데이터)
   - Notification-User: LAZY

---

## Next Steps

- ✅ data-model.md 완성
- ⏸️ contracts/ API 스펙 작성
- ⏸️ quickstart.md 작성
