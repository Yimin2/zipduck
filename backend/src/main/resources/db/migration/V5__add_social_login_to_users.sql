-- Social Login Support
-- provider와 provider_id 컬럼은 이미 추가되어 있음
-- email unique 인덱스는 이미 제거됨

-- 1. password를 nullable로 변경 (소셜 로그인은 비밀번호 없음)
ALTER TABLE users
MODIFY COLUMN password VARCHAR(255) NULL COMMENT 'BCrypt 해시 비밀번호 (소셜 로그인 시 NULL)';

-- 2. email + provider 복합 unique 제약조건 추가
ALTER TABLE users
ADD CONSTRAINT uk_users_email_provider UNIQUE (email, provider);

-- 3. provider_id에 인덱스 추가 (소셜 로그인 조회 최적화)
CREATE INDEX idx_users_provider_id ON users(provider, provider_id);

-- 4. login_histories 테이블 생성
CREATE TABLE IF NOT EXISTS login_histories (
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

-- 5. notifications 테이블 생성
CREATE TABLE IF NOT EXISTS notifications (
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

-- 6. notification_settings 테이블 생성
CREATE TABLE IF NOT EXISTS notification_settings (
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