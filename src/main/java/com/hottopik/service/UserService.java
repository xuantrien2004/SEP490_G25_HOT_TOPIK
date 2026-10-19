package com.hottopik.service;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.AuthToken;
import com.hottopik.entity.EmailOutbox;
import com.hottopik.entity.User;
import com.hottopik.entity.UserSetting;
import com.hottopik.repository.AuthTokenRepository;
import com.hottopik.repository.EmailOutboxRepository;
import com.hottopik.repository.UserRepository;
import com.hottopik.repository.UserSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserSettingRepository userSettingRepository;
    private final AuthTokenRepository authTokenRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final AuthTokenService authTokenService;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Lưu mã OTP in-memory fallback map (key: email, value: otpCode)
    private final Map<String, String> otpStore = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> otpExpiry = new ConcurrentHashMap<>();

    // In-memory fallback map cho mục đích demo / test khi DB chưa sẵn sàng
    private final Map<Long, UserDTO> users = new ConcurrentHashMap<>();

    @Autowired
    public UserService(UserRepository userRepository,
                       UserSettingRepository userSettingRepository,
                       AuthTokenRepository authTokenRepository,
                       EmailOutboxRepository emailOutboxRepository,
                       AuthTokenService authTokenService,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.userSettingRepository = userSettingRepository;
        this.authTokenRepository = authTokenRepository;
        this.emailOutboxRepository = emailOutboxRepository;
        this.authTokenService = authTokenService;
        this.emailService = emailService;
    }

    @PostConstruct
    public void initSeedAccounts() {
        // Tài khoản mẫu tương thích cả seed SEP490 cũ và HotTopik_2 mới
        // 1. Administrator
        users.put(1L, new UserDTO(
                1L, "admin", "admin@hottopik.vn", "admin@123",
                "Quân (Admin)", "ADMIN", "Quản trị viên (Admin)",
                "ACTIVE", "https://api.dicebear.com/7.x/bottts/svg?seed=admin",
                "0912345678", "TOPIK_6", "TOPIK_6", false, null
        ));
        users.put(101L, new UserDTO(
                101L, "admin_com", "admin@hottopik.com", "admin@123",
                "Vũ Minh Quân", "ADMIN", "Quản trị viên (Admin)",
                "ACTIVE", "https://api.dicebear.com/7.x/bottts/svg?seed=admin",
                "0912345678", "TOPIK_6", "TOPIK_6", false, null
        ));

        // 2. Content Manager
        users.put(2L, new UserDTO(
                2L, "content_manager", "content@hottopik.vn", "content@123",
                "Hà (Content)", "CONTENT_MANAGER", "Content Manager",
                "ACTIVE", "https://api.dicebear.com/7.x/bottts/svg?seed=content",
                "0923456789", "TOPIK_6", "TOPIK_5", false, null
        ));
        users.put(102L, new UserDTO(
                102L, "content_com", "content@hottopik.com", "content@123",
                "Lê Thu Hà", "CONTENT_MANAGER", "Content Manager",
                "ACTIVE", "https://api.dicebear.com/7.x/bottts/svg?seed=content",
                "0923456789", "TOPIK_6", "TOPIK_5", false, null
        ));

        // 3. Operation Manager
        users.put(3L, new UserDTO(
                3L, "operation_manager", "operation@hottopik.vn", "operation@123",
                "Tuấn (Operation)", "OPERATION_MANAGER", "Operation Manager",
                "ACTIVE", "https://api.dicebear.com/7.x/bottts/svg?seed=operation",
                "0934567890", "TOPIK_4", "TOPIK_3", false, null
        ));
        users.put(103L, new UserDTO(
                103L, "operation_com", "operation@hottopik.com", "operation@123",
                "Trần Quốc Tuấn", "OPERATION_MANAGER", "Operation Manager",
                "ACTIVE", "https://api.dicebear.com/7.x/bottts/svg?seed=operation",
                "0934567890", "TOPIK_4", "TOPIK_3", false, null
        ));

        // 4. Mentor / Teacher
        users.put(4L, new UserDTO(
                4L, "mentor", "mai.mentor@hottopik.vn", "teacher@123",
                "Cô Mai (Mentor)", "MENTOR", "Giáo viên (Mentor)",
                "ACTIVE", "https://api.dicebear.com/7.x/bottts/svg?seed=teacher",
                "0967890123", "TOPIK_6", "TOPIK_6", false, null
        ));
        users.put(104L, new UserDTO(
                104L, "teacher_com", "teacher@hottopik.com", "teacher@123",
                "Cô Mai (Giáo viên)", "MENTOR", "Giáo viên (Mentor)",
                "ACTIVE", "https://api.dicebear.com/7.x/bottts/svg?seed=teacher",
                "0967890123", "TOPIK_6", "TOPIK_6", false, null
        ));

        // 5. Learner (Student)
        users.put(5L, new UserDTO(
                5L, "learner", "learner@hottopik.com", "user@123",
                "Phạm Minh Anh", "STUDENT", "Học viên",
                "ACTIVE", "https://api.dicebear.com/7.x/bottts/svg?seed=learner",
                "0956789012", "TOPIK_2", "TOPIK_1", false, null
        ));

        // 6. VIP Learner
        users.put(6L, new UserDTO(
                6L, "vip_learner", "viplearner@hottopik.com", "vip@123",
                "Nguyễn Hoàng Nam", "STUDENT", "Học viên VIP",
                "ACTIVE", "https://api.dicebear.com/7.x/bottts/svg?seed=viplearner",
                "0945678901", "TOPIK_5", "TOPIK_3", true, "VIP Trọn đời (Lifetime VIP)"
        ));
    }

    /**
     * Sinh mã OTP 6 chữ số ngẫu nhiên
     */
    public String generateOtpCode() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }

    /**
     * Xác thực đăng nhập qua Email/Username và Mật khẩu
     */
    @Transactional
    public Optional<UserDTO> authenticate(String identifier, String password) {
        if (identifier == null || password == null) {
            return Optional.empty();
        }
        String cleanId = identifier.trim().toLowerCase();
        String cleanPass = password.trim();

        // 1. Thử xác thực trực tiếp từ Database SQL Server
        try {
            Optional<User> dbUserOpt = userRepository.findByEmail(cleanId);
            if (dbUserOpt.isPresent()) {
                User user = dbUserOpt.get();

                // Kiểm tra nếu tài khoản đang bị khóa tạm thời
                if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                    UserDTO lockedDto = mapToDTO(user);
                    lockedDto.setStatus("LOCKED");
                    return Optional.of(lockedDto);
                }

                // Kiểm tra mật khẩu (hỗ trợ cả băm BCrypt lẫn chuỗi demo/seed)
                boolean matches = false;
                String storedHash = user.getPasswordHash();
                if (storedHash != null) {
                    if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
                        matches = passwordEncoder.matches(cleanPass, storedHash);
                    } else {
                        matches = storedHash.equals(cleanPass) || storedHash.equals("<bcrypt-hash>");
                    }
                }

                if (matches) {
                    user.setFailedLoginCount((short) 0);
                    user.setLockedUntil(null);
                    user.setLastLoginAt(LocalDateTime.now());
                    userRepository.save(user);

                    return Optional.of(mapToDTO(user));
                } else {
                    short failed = (short) ((user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0) + 1);
                    user.setFailedLoginCount(failed);
                    if (failed >= 5) {
                        user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                        user.setLockReason("Đăng nhập sai quá 5 lần liên tiếp");
                        log.warn("Tài khoản {} bị tạm khóa 15 phút do nhập sai mật khẩu 5 lần", user.getEmail());
                    }
                    userRepository.save(user);
                    return Optional.empty();
                }
            }
        } catch (Exception ex) {
            log.warn("Không thể kết nối DB để xác thực, chuyển sang chế độ in-memory fallback: {}", ex.getMessage());
        }

        // 2. Fallback in-memory map
        return users.values().stream()
                .filter(u -> (u.getEmail().equalsIgnoreCase(cleanId) || (u.getUsername() != null && u.getUsername().equalsIgnoreCase(cleanId)))
                        && u.getPassword().equals(cleanPass))
                .findFirst();
    }

    /**
     * Đăng ký tài khoản học viên mới và GỬI MÃ OTP THẬT QUA EMAIL
     */
    @Transactional
    public boolean registerStudent(String displayName, String email, String rawPassword) {
        if (email == null || rawPassword == null || displayName == null) {
            return false;
        }
        String cleanEmail = email.trim().toLowerCase();
        String cleanName = displayName.trim();
        String otpCode = generateOtpCode();

        // Lưu OTP vào in-memory store (hạn 5 phút)
        otpStore.put(cleanEmail, otpCode);
        otpExpiry.put(cleanEmail, LocalDateTime.now().plusMinutes(5));

        // 1. Thử lưu vào DB
        try {
            Optional<User> existingUserOpt = userRepository.findByEmail(cleanEmail);
            User userToSave;

            if (existingUserOpt.isPresent()) {
                User existing = existingUserOpt.get();
                if ("ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                    return false; // Email đã kích hoạt, không cho đăng ký đè
                }
                // Nếu đang PENDING_VERIFY thì cho phép cập nhật lại thông tin và nhận OTP mới
                existing.setDisplayName(cleanName);
                existing.setPasswordHash(passwordEncoder.encode(rawPassword.trim()));
                userToSave = userRepository.save(existing);
            } else {
                User newUser = User.builder()
                        .email(cleanEmail)
                        .passwordHash(passwordEncoder.encode(rawPassword.trim()))
                        .displayName(cleanName)
                        .role("STUDENT")
                        .status("PENDING_VERIFY")
                        .dailyGoalMinutes((short) 20)
                        .coinBalance(0)
                        .studiedDays(0)
                        .failedLoginCount((short) 0)
                        .build();

                userToSave = userRepository.save(newUser);

                UserSetting setting = UserSetting.builder()
                        .userId(userToSave.getId())
                        .theme("LIGHT")
                        .fontSize("M")
                        .showRomanization(true)
                        .audioSpeed(new BigDecimal("1.00"))
                        .reminderEnabled(false)
                        .reminderEmail(false)
                        .browserPushEnabled(false)
                        .build();
                userSettingRepository.save(setting);
            }

            // Lưu mã OTP (băm SHA-256) vào dbo.auth_tokens (hạn 5 phút theo auth.otp_minutes)
            String tokenHash = authTokenService.hashToken(otpCode + "_" + cleanEmail);
            AuthToken authToken = AuthToken.builder()
                    .userId(userToSave.getId())
                    .type("VERIFY")
                    .tokenHash(tokenHash)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();
            authTokenRepository.save(authToken);

            // Lưu nhật ký email vào dbo.email_outbox
            EmailOutbox emailItem = EmailOutbox.builder()
                    .toEmail(cleanEmail)
                    .template("VERIFY_OTP")
                    .payload(String.format("{\"displayName\":\"%s\",\"otpCode\":\"%s\"}", cleanName, otpCode))
                    .status("PENDING")
                    .attempts((short) 0)
                    .build();
            emailOutboxRepository.save(emailItem);

            // Cập nhật fallback in-memory
            UserDTO dto = mapToDTO(userToSave);
            dto.setPassword(rawPassword);
            users.put(userToSave.getId(), dto);

        } catch (Exception ex) {
            log.warn("Lỗi lưu DB khi đăng ký ({}), lưu in-memory fallback: {}", cleanEmail, ex.getMessage());
            long newId = System.currentTimeMillis();
            UserDTO dto = new UserDTO(
                    newId, cleanEmail.split("@")[0], cleanEmail, rawPassword,
                    cleanName, "STUDENT", "Học viên", "PENDING_VERIFY",
                    "https://api.dicebear.com/7.x/bottts/svg?seed=" + cleanEmail,
                    "", "TOPIK_1", "0", false, null
            );
            users.put(newId, dto);
        }

        // 2. GỬI EMAIL THẬT CHỨA MÃ OTP
        emailService.sendRegistrationOtp(cleanEmail, cleanName, otpCode);
        return true;
    }

    /**
     * Xác thực tài khoản bằng mã OTP 6 số
     */
    @Transactional
    public Optional<UserDTO> verifyRegistrationOtp(String email, String otpCode) {
        if (email == null || otpCode == null) {
            return Optional.empty();
        }
        String cleanEmail = email.trim().toLowerCase();
        String cleanOtp = otpCode.trim();

        // 1. Kiểm tra qua DB
        try {
            String tokenHash = authTokenService.hashToken(cleanOtp + "_" + cleanEmail);
            Optional<AuthToken> tokenOpt = authTokenRepository.findByTokenHash(tokenHash);

            if (tokenOpt.isPresent()) {
                AuthToken token = tokenOpt.get();
                if ("VERIFY".equalsIgnoreCase(token.getType())
                        && token.getUsedAt() == null
                        && token.getExpiresAt().isAfter(LocalDateTime.now())) {

                    Optional<User> userOpt = userRepository.findById(token.getUserId());
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        user.setStatus("ACTIVE");
                        user.setEmailVerifiedAt(LocalDateTime.now());
                        userRepository.save(user);

                        token.setUsedAt(LocalDateTime.now());
                        authTokenRepository.save(token);

                        UserDTO dto = mapToDTO(user);
                        users.put(user.getId(), dto);

                        otpStore.remove(cleanEmail);
                        otpExpiry.remove(cleanEmail);

                        log.info(">>> [HOT TOPIK] Kích hoạt tài khoản thành công qua OTP cho email: {}", cleanEmail);
                        return Optional.of(dto);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Lỗi kiểm tra OTP qua DB: {}", ex.getMessage());
        }

        // 2. Fallback in-memory OTP store
        String storedOtp = otpStore.get(cleanEmail);
        LocalDateTime expiry = otpExpiry.get(cleanEmail);

        if (storedOtp != null && storedOtp.equals(cleanOtp) && expiry != null && expiry.isAfter(LocalDateTime.now())) {
            for (UserDTO u : users.values()) {
                if (u.getEmail().equalsIgnoreCase(cleanEmail)) {
                    u.setStatus("ACTIVE");
                    otpStore.remove(cleanEmail);
                    otpExpiry.remove(cleanEmail);
                    return Optional.of(u);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Gửi lại mã OTP kích hoạt tài khoản
     */
    public boolean resendRegistrationOtp(String email) {
        if (email == null) return false;
        String cleanEmail = email.trim().toLowerCase();

        String displayName = "Học viên";
        Optional<UserDTO> uOpt = findByEmail(cleanEmail);
        if (uOpt.isPresent()) {
            displayName = uOpt.get().getDisplayName();
        }

        String newOtp = generateOtpCode();
        otpStore.put(cleanEmail, newOtp);
        otpExpiry.put(cleanEmail, LocalDateTime.now().plusMinutes(5));

        try {
            Optional<User> dbUserOpt = userRepository.findByEmail(cleanEmail);
            if (dbUserOpt.isPresent()) {
                String tokenHash = authTokenService.hashToken(newOtp + "_" + cleanEmail);
                AuthToken authToken = AuthToken.builder()
                        .userId(dbUserOpt.get().getId())
                        .type("VERIFY")
                        .tokenHash(tokenHash)
                        .expiresAt(LocalDateTime.now().plusMinutes(5))
                        .build();
                authTokenRepository.save(authToken);
            }
        } catch (Exception ex) {
            log.warn("Lỗi lưu lại OTP mới: {}", ex.getMessage());
        }

        emailService.sendRegistrationOtp(cleanEmail, displayName, newOtp);
        return true;
    }

    /**
     * Quên mật khẩu: GỬI MÃ OTP 6 SỐ THẬT QUA EMAIL
     */
    @Transactional
    public boolean sendPasswordResetOtp(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        String cleanEmail = email.trim().toLowerCase();
        String otpCode = generateOtpCode();

        otpStore.put(cleanEmail, otpCode);
        otpExpiry.put(cleanEmail, LocalDateTime.now().plusMinutes(5));

        String displayName = "Bạn";

        try {
            Optional<User> userOpt = userRepository.findByEmail(cleanEmail);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                displayName = user.getDisplayName();

                String tokenHash = authTokenService.hashToken(otpCode + "_" + cleanEmail);
                AuthToken resetToken = AuthToken.builder()
                        .userId(user.getId())
                        .type("RESET")
                        .tokenHash(tokenHash)
                        .expiresAt(LocalDateTime.now().plusMinutes(5)) // Hạn 5 phút theo auth.otp_minutes
                        .build();
                authTokenRepository.save(resetToken);

                EmailOutbox emailItem = EmailOutbox.builder()
                        .toEmail(cleanEmail)
                        .template("RESET_OTP")
                        .payload(String.format("{\"displayName\":\"%s\",\"otpCode\":\"%s\"}", displayName, otpCode))
                        .status("PENDING")
                        .attempts((short) 0)
                        .build();
                emailOutboxRepository.save(emailItem);
            }
        } catch (Exception ex) {
            log.warn("Lỗi tạo reset OTP qua DB: {}", ex.getMessage());
        }

        // GỬI EMAIL THẬT QUA SMTP
        emailService.sendForgotPasswordOtp(cleanEmail, displayName, otpCode);
        return true;
    }

    /**
     * Đặt lại mật khẩu mới bằng mã OTP 6 số
     */
    @Transactional
    public boolean resetPasswordWithOtp(String email, String otpCode, String newPassword) {
        if (email == null || otpCode == null || newPassword == null || newPassword.trim().isEmpty()) {
            return false;
        }
        String cleanEmail = email.trim().toLowerCase();
        String cleanOtp = otpCode.trim();
        String cleanPass = newPassword.trim();

        // 1. Kiểm tra qua DB
        try {
            String tokenHash = authTokenService.hashToken(cleanOtp + "_" + cleanEmail);
            Optional<AuthToken> tokenOpt = authTokenRepository.findByTokenHash(tokenHash);

            if (tokenOpt.isPresent()) {
                AuthToken token = tokenOpt.get();
                if ("RESET".equalsIgnoreCase(token.getType())
                        && token.getUsedAt() == null
                        && token.getExpiresAt().isAfter(LocalDateTime.now())) {

                    Optional<User> userOpt = userRepository.findById(token.getUserId());
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        user.setPasswordHash(passwordEncoder.encode(cleanPass));
                        user.setFailedLoginCount((short) 0);
                        user.setLockedUntil(null);
                        userRepository.save(user);

                        token.setUsedAt(LocalDateTime.now());
                        authTokenRepository.save(token);

                        UserDTO memUser = users.get(user.getId());
                        if (memUser != null) {
                            memUser.setPassword(cleanPass);
                            memUser.setStatus("ACTIVE");
                        }

                        otpStore.remove(cleanEmail);
                        otpExpiry.remove(cleanEmail);

                        log.info(">>> [HOT TOPIK] Đổi mật khẩu thành công qua OTP cho tài khoản: {}", cleanEmail);
                        return true;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Lỗi đổi mật khẩu qua OTP trong DB: {}", ex.getMessage());
        }

        // 2. Fallback in-memory
        String storedOtp = otpStore.get(cleanEmail);
        LocalDateTime expiry = otpExpiry.get(cleanEmail);

        if (storedOtp != null && storedOtp.equals(cleanOtp) && expiry != null && expiry.isAfter(LocalDateTime.now())) {
            for (UserDTO u : users.values()) {
                if (u.getEmail().equalsIgnoreCase(cleanEmail)) {
                    u.setPassword(cleanPass);
                    u.setStatus("ACTIVE");
                    otpStore.remove(cleanEmail);
                    otpExpiry.remove(cleanEmail);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Hỗ trợ kích hoạt bằng token dài (nếu dùng link URL cũ)
     */
    @Transactional
    public boolean verifyEmailByToken(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return false;
        }
        String tokenHash = authTokenService.hashToken(rawToken.trim());
        try {
            Optional<AuthToken> tokenOpt = authTokenRepository.findByTokenHash(tokenHash);
            if (tokenOpt.isPresent()) {
                AuthToken token = tokenOpt.get();
                if ("VERIFY".equalsIgnoreCase(token.getType())
                        && token.getUsedAt() == null
                        && token.getExpiresAt().isAfter(LocalDateTime.now())) {
                    Optional<User> userOpt = userRepository.findById(token.getUserId());
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        user.setStatus("ACTIVE");
                        user.setEmailVerifiedAt(LocalDateTime.now());
                        userRepository.save(user);

                        token.setUsedAt(LocalDateTime.now());
                        authTokenRepository.save(token);
                        return true;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Lỗi xác thực email qua link token: {}", ex.getMessage());
        }
        return false;
    }

    private UserDTO mapToDTO(User u) {
        String roleName;
        switch (u.getRole()) {
            case "ADMIN":
                roleName = "Quản trị viên (Admin)";
                break;
            case "CONTENT_MANAGER":
                roleName = "Quản lý nội dung";
                break;
            case "OPERATION_MANAGER":
                roleName = "Quản lý vận hành";
                break;
            case "MENTOR":
                roleName = "Giáo viên (Mentor)";
                break;
            case "STUDENT":
            default:
                roleName = "Học viên";
                break;
        }

        String targetLvl = u.getTargetLevel() != null ? "TOPIK_" + u.getTargetLevel() : null;
        String currentLvl = u.getCurrentLevel() != null ? "TOPIK_" + u.getCurrentLevel() : null;

        UserDTO dto = new UserDTO(
                u.getId(),
                u.getEmail().split("@")[0],
                u.getEmail(),
                "",
                u.getDisplayName(),
                u.getRole(),
                roleName,
                u.getStatus(),
                u.getAvatarUrl() != null ? u.getAvatarUrl() : "https://api.dicebear.com/7.x/bottts/svg?seed=" + u.getEmail(),
                "",
                targetLvl,
                currentLvl,
                false,
                null
        );
        dto.setCoinBalance(u.getCoinBalance() != null ? u.getCoinBalance() : 0);
        dto.setStudiedDays(u.getStudiedDays() != null ? u.getStudiedDays() : 0);
        return dto;
    }

    public List<UserDTO> getAllUsers() {
        Map<String, UserDTO> merged = new LinkedHashMap<>();
        try {
            List<User> dbUsers = userRepository.findAll();
            for (User u : dbUsers) {
                if (u.getEmail() != null) {
                    UserDTO dto = mapToDTO(u);
                    merged.put(u.getEmail().trim().toLowerCase(), dto);
                }
            }
        } catch (Exception ex) {
            log.warn("Lỗi đọc danh sách users từ database: {}", ex.getMessage());
        }

        // Bổ sung các tài khoản mẫu in-memory nếu chưa có trong DB
        for (UserDTO u : users.values()) {
            if (u.getEmail() != null && !merged.containsKey(u.getEmail().trim().toLowerCase())) {
                merged.put(u.getEmail().trim().toLowerCase(), u);
            }
        }

        return new ArrayList<>(merged.values());
    }

    public List<UserDTO> getStaffUsers() {
        return getAllUsers().stream()
                .filter(u -> u.isAdmin() || u.isContentManager() || u.isOperationManager() || u.isMentor())
                .collect(Collectors.toList());
    }

    public List<UserDTO> getLearners() {
        return getAllUsers().stream()
                .filter(u -> !u.isAdmin() && !u.isContentManager() && !u.isOperationManager() && !u.isMentor())
                .collect(Collectors.toList());
    }

    public Optional<UserDTO> findById(Long id) {
        if (id == null) return Optional.empty();
        try {
            Optional<User> uOpt = userRepository.findById(id);
            if (uOpt.isPresent()) {
                return Optional.of(mapToDTO(uOpt.get()));
            }
        } catch (Exception ex) {
            log.warn("Lỗi tìm user qua DB: {}", ex.getMessage());
        }
        return Optional.ofNullable(users.get(id));
    }

    public Optional<UserDTO> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String cleanEmail = email.trim().toLowerCase();
        try {
            Optional<User> uOpt = userRepository.findByEmail(cleanEmail);
            if (uOpt.isPresent()) {
                return Optional.of(mapToDTO(uOpt.get()));
            }
        } catch (Exception ex) {
            log.warn("Lỗi tìm user theo email qua DB: {}", ex.getMessage());
        }
        return users.values().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(cleanEmail))
                .findFirst();
    }

    @Transactional
    public boolean toggleUserLock(Long id) {
        if (id == null) return false;
        boolean found = false;
        try {
            Optional<User> uOpt = userRepository.findById(id);
            if (uOpt.isPresent()) {
                User u = uOpt.get();
                if ("LOCKED".equalsIgnoreCase(u.getStatus())) {
                    u.setStatus("ACTIVE");
                    u.setLockedUntil(null);
                    u.setLockReason(null);
                    u.setFailedLoginCount((short) 0);
                } else {
                    u.setStatus("LOCKED");
                    u.setLockedUntil(LocalDateTime.now().plusYears(10));
                    u.setLockReason("Khóa bởi ban điều hành/quản trị viên");
                }
                userRepository.save(u);
                found = true;

                // Đồng bộ in-memory nếu tồn tại
                for (UserDTO mem : users.values()) {
                    if (mem.getEmail() != null && mem.getEmail().equalsIgnoreCase(u.getEmail())) {
                        mem.setStatus(u.getStatus());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Lỗi cập nhật trạng thái khóa user trong DB: {}", ex.getMessage());
        }

        UserDTO mem = users.get(id);
        if (mem != null) {
            if ("LOCKED".equalsIgnoreCase(mem.getStatus())) {
                mem.setStatus("ACTIVE");
            } else {
                mem.setStatus("LOCKED");
            }
            found = true;
        }

        return found;
    }

    /**
     * Đổi mật khẩu cho người dùng đang đăng nhập
     */
    @Transactional
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null || oldPassword == null || newPassword == null) return false;
        String cleanOld = oldPassword.trim();
        String cleanNew = newPassword.trim();

        try {
            Optional<User> uOpt = userRepository.findById(userId);
            if (uOpt.isPresent()) {
                User user = uOpt.get();
                boolean matches = false;
                String stored = user.getPasswordHash();
                if (stored != null) {
                    if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
                        matches = passwordEncoder.matches(cleanOld, stored);
                    } else {
                        matches = stored.equals(cleanOld) || stored.equals("<bcrypt-hash>");
                    }
                }
                if (matches) {
                    user.setPasswordHash(passwordEncoder.encode(cleanNew));
                    userRepository.save(user);

                    UserDTO mem = users.get(userId);
                    if (mem != null) {
                        mem.setPassword(cleanNew);
                    }
                    return true;
                }
            }
        } catch (Exception ex) {
            log.warn("Lỗi đổi mật khẩu trong DB: {}", ex.getMessage());
        }

        // Fallback in-memory
        UserDTO mem = users.get(userId);
        if (mem != null) {
            mem.setPassword(cleanNew);
            return true;
        }
        return false;
    }

    /**
     * Cập nhật tên hiển thị của người dùng
     */
    @Transactional
    public boolean updateDisplayName(Long userId, String newDisplayName) {
        if (userId == null || newDisplayName == null || newDisplayName.trim().isEmpty()) {
            return false;
        }
        String cleanName = newDisplayName.trim();
        try {
            Optional<User> uOpt = userRepository.findById(userId);
            if (uOpt.isPresent()) {
                User user = uOpt.get();
                user.setDisplayName(cleanName);
                userRepository.save(user);
            }
        } catch (Exception ex) {
            log.warn("Lỗi lưu displayName vào DB: {}", ex.getMessage());
        }

        UserDTO mem = users.get(userId);
        if (mem != null) {
            mem.setDisplayName(cleanName);
        }
        return true;
    }

    /**
     * Lưu thiết lập trình độ và mục tiêu lộ trình của học viên vào DB
     */
    @Transactional
    public boolean saveRoadmapSettings(Long userId, String currentLevelStr, String targetLevelStr, Integer dailyMinutes, String levelSource) {
        if (userId == null) return false;
        Short currentLevel = parseLevelToShort(currentLevelStr, (short) 1);
        Short targetLevel = parseLevelToShort(targetLevelStr, (short) (currentLevel + 1 <= 6 ? currentLevel + 1 : 6));
        Short minutes = (dailyMinutes != null && dailyMinutes > 0) ? dailyMinutes.shortValue() : (short) 20;

        try {
            Optional<User> uOpt = userRepository.findById(userId);
            if (uOpt.isPresent()) {
                User u = uOpt.get();
                u.setCurrentLevel(currentLevel);
                u.setTargetLevel(targetLevel);
                u.setLevelSource(levelSource != null ? levelSource : "SELF");
                u.setDailyGoalMinutes(minutes);
                userRepository.save(u);
            }
        } catch (Exception ex) {
            log.warn("Lỗi lưu roadmap settings vào DB: {}", ex.getMessage());
        }

        UserDTO mem = users.get(userId);
        if (mem != null) {
            mem.setCurrentLevel("TOPIK_" + currentLevel);
            mem.setTargetLevel("TOPIK_" + targetLevel);
        }
        return true;
    }

    private Short parseLevelToShort(String lvl, Short defaultVal) {
        if (lvl == null) return defaultVal;
        String clean = lvl.toUpperCase()
                .replace("TOPIK_", "")
                .replace("TOPIK ", "")
                .replace("TOPIK", "")
                .replace("CẤP ", "")
                .replace("CẤP", "")
                .trim();
        if ("BEGINNER".equals(clean) || "0".equals(clean)) return 0;
        try {
            short s = Short.parseShort(clean);
            if (s >= 0 && s <= 6) return s;
        } catch (NumberFormatException ignored) {}
        return defaultVal;
    }

    /**
     * Tìm hoặc tạo mới user từ thông tin Google Sign-In.
     * Nếu email đã tồn tại -> trả về user hiện tại (cập nhật avatar nếu chưa có).
     * Nếu chưa -> tạo tài khoản STUDENT mới với status ACTIVE (không cần OTP vì đã xác thực qua Google).
     */
    @Transactional
    public UserDTO findOrCreateGoogleUser(String email, String displayName, String avatarUrl) {
        if (email == null) return null;
        String cleanEmail = email.trim().toLowerCase();
        String cleanName = (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : cleanEmail.split("@")[0];

        // 1. Thử tìm trong DB
        try {
            Optional<User> existingOpt = userRepository.findByEmail(cleanEmail);
            if (existingOpt.isPresent()) {
                User existing = existingOpt.get();
                // Cập nhật avatar Google nếu chưa có
                if ((existing.getAvatarUrl() == null || existing.getAvatarUrl().contains("dicebear")) && avatarUrl != null) {
                    existing.setAvatarUrl(avatarUrl);
                }
                // Nếu tài khoản đang PENDING_VERIFY, tự động kích hoạt (vì Google đã xác thực email)
                if ("PENDING_VERIFY".equalsIgnoreCase(existing.getStatus())) {
                    existing.setStatus("ACTIVE");
                    existing.setEmailVerifiedAt(LocalDateTime.now());
                }
                existing.setLastLoginAt(LocalDateTime.now());
                userRepository.save(existing);

                UserDTO dto = mapToDTO(existing);
                users.put(existing.getId(), dto);
                return dto;
            } else {
                // Tạo tài khoản mới từ Google (không cần mật khẩu, đặt hash placeholder)
                User newUser = User.builder()
                        .email(cleanEmail)
                        .passwordHash("GOOGLE_OAUTH_NO_PASSWORD")
                        .displayName(cleanName)
                        .role("STUDENT")
                        .status("ACTIVE")
                        .avatarUrl(avatarUrl)
                        .emailVerifiedAt(LocalDateTime.now())
                        .lastLoginAt(LocalDateTime.now())
                        .dailyGoalMinutes((short) 20)
                        .coinBalance(0)
                        .studiedDays(0)
                        .failedLoginCount((short) 0)
                        .build();

                User saved = userRepository.save(newUser);

                UserSetting setting = UserSetting.builder()
                        .userId(saved.getId())
                        .theme("LIGHT")
                        .fontSize("M")
                        .showRomanization(true)
                        .audioSpeed(new java.math.BigDecimal("1.00"))
                        .reminderEnabled(false)
                        .reminderEmail(false)
                        .browserPushEnabled(false)
                        .build();
                userSettingRepository.save(setting);

                UserDTO dto = mapToDTO(saved);
                users.put(saved.getId(), dto);
                log.info(">>> [HOT TOPIK] Tạo tài khoản mới qua Google Sign-In: {}", cleanEmail);
                return dto;
            }
        } catch (Exception ex) {
            log.warn("Lỗi DB khi xử lý Google Sign-In ({}), fallback in-memory: {}", cleanEmail, ex.getMessage());
        }

        // 2. Fallback in-memory
        for (UserDTO u : users.values()) {
            if (u.getEmail() != null && u.getEmail().equalsIgnoreCase(cleanEmail)) {
                return u;
            }
        }

        // Tạo in-memory mới
        long newId = System.currentTimeMillis();
        UserDTO dto = new UserDTO(
                newId, cleanEmail.split("@")[0], cleanEmail, "",
                cleanName, "STUDENT", "Học viên", "ACTIVE",
                avatarUrl != null ? avatarUrl : "https://api.dicebear.com/7.x/bottts/svg?seed=" + cleanEmail,
                "", null, null, false, null
        );
        users.put(newId, dto);
        return dto;
    }
}

