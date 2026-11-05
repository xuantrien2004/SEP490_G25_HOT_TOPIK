package com.hottopik.service;

import com.hottopik.entity.User;
import com.hottopik.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ghi các tài khoản mẫu (admin/content/operation/mentor/student) vào bảng users của CSDL đang dùng
 * (SQL Server hoặc H2 profile local). UserService.authenticate() tra DB trước; nếu email không có
 * trong bảng users, nó trả rỗng ngay và không bao giờ rơi xuống fallback in-memory của UserService,
 * nên các tài khoản mẫu định nghĩa trong UserService.initSeedAccounts() (chỉ tồn tại trong RAM)
 * không thể đăng nhập được khi DB đang hoạt động. Seeder này tạo đúng các tài khoản đó trong DB,
 * chỉ khi email chưa tồn tại, để không ghi đè tài khoản đã có hoặc đã bị người dùng đổi mật khẩu.
 */
@Component
@Order(5)
public class UserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private record SeedUser(String email, String rawPassword, String displayName, String role,
                             Short currentLevel, Short targetLevel) {}

    private static final List<SeedUser> ACCOUNTS = List.of(
            new SeedUser("admin@hottopik.com", "admin@123", "Vũ Minh Quân", "ADMIN", (short) 6, (short) 6),
            new SeedUser("content@hottopik.com", "content@123", "Lê Thu Hà", "CONTENT_MANAGER", (short) 6, (short) 5),
            new SeedUser("operation@hottopik.com", "operation@123", "Trần Quốc Tuấn", "OPERATION_MANAGER", (short) 4, (short) 3),
            new SeedUser("teacher@hottopik.com", "teacher@123", "Cô Mai (Giáo viên)", "MENTOR", (short) 6, (short) 6),
            new SeedUser("learner@hottopik.com", "user@123", "Phạm Minh Anh", "STUDENT", (short) 1, (short) 2)
    );

    public UserSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (SeedUser seed : ACCOUNTS) {
            if (userRepository.existsByEmail(seed.email())) continue;
            User user = User.builder()
                    .email(seed.email())
                    .passwordHash(passwordEncoder.encode(seed.rawPassword()))
                    .displayName(seed.displayName())
                    .role(seed.role())
                    .status("ACTIVE")
                    .currentLevel(seed.currentLevel())
                    .targetLevel(seed.targetLevel())
                    .build();
            userRepository.save(user);
        }
    }
}
