package com.zipduck.domain.auth;

import com.zipduck.domain.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 인증 도메인 서비스
 * 회원가입, 로그인 비즈니스 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    // 비밀번호 정규식: 최소 8자, 영문+숫자+특수문자 포함
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$");

    /**
     * 회원가입
     */
    @Transactional
    public AuthResponse signup(String email, String password, String username) {
        // 이메일 중복 확인
        if (userRepository.existsByEmailAndProvider(email, AuthProvider.LOCAL)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다");
        }

        // 비밀번호 강도 검증
        if (!isValidPassword(password)) {
            throw new IllegalArgumentException(
                    "비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 포함해야 합니다"
            );
        }

        // 사용자 생성
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .username(username)
                .provider(AuthProvider.LOCAL)
                .status(User.UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);

        // 토큰 발급
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = issueRefreshToken(user.getId());

        log.info("User signed up successfully: {}", email);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600) // 1시간
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .provider(user.getProvider())
                .status(user.getStatus())
                .build();
    }

    /**
     * 로그인
     */
    @Transactional
    public AuthResponse login(String email, String password, String ipAddress, String userAgent) {
        // 로그인 시도 횟수 확인
        LoginAttempt loginAttempt = loginAttemptRepository.findById(email)
                .orElse(null);

        if (loginAttempt != null && loginAttempt.isLocked()) {
            throw new AccountLockedException(
                    "비밀번호를 5회 이상 틀렸습니다. 30분 후 다시 시도하거나 비밀번호 찾기를 이용하세요"
            );
        }

        // 사용자 조회
        User user = userRepository.findByEmailAndProvider(email, AuthProvider.LOCAL)
                .orElseThrow(() -> {
                    recordFailedLogin(email, ipAddress, userAgent, "사용자를 찾을 수 없음");
                    return new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
                });

        // 비밀번호 검증
        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordFailedLogin(email, ipAddress, userAgent, "비밀번호 불일치");
            incrementLoginAttempt(email);
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        // 계정 상태 확인
        if (user.getStatus() == User.UserStatus.SUSPENDED) {
            throw new AccountLockedException("계정이 일시 정지되었습니다");
        }

        // 로그인 성공 - 시도 횟수 초기화
        if (loginAttempt != null) {
            loginAttemptRepository.deleteById(email);
        }

        // 로그인 성공 기록
        LoginHistory successHistory = LoginHistory.success(user, AuthProvider.LOCAL, ipAddress, userAgent);
        loginHistoryRepository.save(successHistory);

        // 토큰 발급
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = issueRefreshToken(user.getId());

        log.info("User logged in successfully: {}", email);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600)
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .provider(user.getProvider())
                .status(user.getStatus())
                .build();
    }

    /**
     * Refresh Token 발급
     */
    private String issueRefreshToken(Long userId) {
        String tokenValue = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.create(tokenValue, userId, 604800); // 7일
        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }

    /**
     * 로그인 시도 횟수 증가
     */
    private void incrementLoginAttempt(String email) {
        LoginAttempt attempt = loginAttemptRepository.findById(email)
                .orElse(LoginAttempt.createNew(email));

        if (attempt.getCount() == 0) {
            attempt = LoginAttempt.createNew(email);
        } else {
            attempt.increment();
        }

        loginAttemptRepository.save(attempt);
    }

    /**
     * 로그인 실패 기록
     */
    private void recordFailedLogin(String email, String ipAddress, String userAgent, String reason) {
        // 사용자가 존재하는 경우에만 기록
        userRepository.findByEmailAndProvider(email, AuthProvider.LOCAL)
                .ifPresent(user -> {
                    LoginHistory failureHistory = LoginHistory.failure(
                            user, AuthProvider.LOCAL, ipAddress, userAgent, reason
                    );
                    loginHistoryRepository.save(failureHistory);
                });
    }

    /**
     * 비밀번호 유효성 검증
     */
    private boolean isValidPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    /**
     * 인증 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Integer expiresIn;
        private Long userId;
        private String email;
        private String username;
        private AuthProvider provider;
        private User.UserStatus status;
    }

    /**
     * 잘못된 인증 정보 예외
     */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }

    /**
     * 계정 잠금 예외
     */
    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String message) {
            super(message);
        }
    }
}
