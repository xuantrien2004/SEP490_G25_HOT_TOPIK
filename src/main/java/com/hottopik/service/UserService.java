package com.hottopik.service;

import com.hottopik.dto.UserDTO;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final Map<Long, UserDTO> users = new ConcurrentHashMap<>();

    @PostConstruct
    public void initSeedAccounts() {
        // Exact 5 seed accounts from SEP490.sql
        // 1. Administrator
        users.put(1L, new UserDTO(
                1L,
                "admin",
                "admin@hottopik.com",
                "admin@123",
                "Vũ Minh Quân",
                "ROLE_ADMIN",
                "Quản trị viên (Admin)",
                "ACTIVE",
                "https://api.dicebear.com/7.x/bottts/svg?seed=admin",
                "0912345678",
                "TOPIK_6",
                "TOPIK_6",
                false,
                null
        ));

        // 2. Content Manager
        users.put(2L, new UserDTO(
                2L,
                "content_manager",
                "content@hottopik.com",
                "content@123",
                "Lê Thu Hà",
                "ROLE_CONTENT_MANAGER",
                "Content Manager",
                "ACTIVE",
                "https://api.dicebear.com/7.x/bottts/svg?seed=content",
                "0923456789",
                "TOPIK_6",
                "TOPIK_5",
                false,
                null
        ));

        // 3. Operation Manager
        users.put(3L, new UserDTO(
                3L,
                "operation_manager",
                "operation@hottopik.com",
                "operation@123",
                "Trần Quốc Tuấn",
                "ROLE_OPERATION_MANAGER",
                "Operation Manager",
                "ACTIVE",
                "https://api.dicebear.com/7.x/bottts/svg?seed=operation",
                "0934567890",
                "TOPIK_4",
                "TOPIK_3",
                false,
                null
        ));

        // 4. VIP Learner
        users.put(4L, new UserDTO(
                4L,
                "vip_learner",
                "viplearner@hottopik.com",
                "vip@123",
                "Nguyễn Hoàng Nam",
                "ROLE_VIP",
                "Học viên VIP",
                "ACTIVE",
                "https://api.dicebear.com/7.x/bottts/svg?seed=viplearner",
                "0945678901",
                "TOPIK_5",
                "TOPIK_3",
                true,
                "VIP Trọn đời (Lifetime VIP)"
        ));

        // 5. Standard Learner
        users.put(5L, new UserDTO(
                5L,
                "learner",
                "learner@hottopik.com",
                "user@123",
                "Phạm Minh Anh",
                "ROLE_USER",
                "Học viên",
                "ACTIVE",
                "https://api.dicebear.com/7.x/bottts/svg?seed=learner",
                "0956789012",
                "TOPIK_2",
                "TOPIK_1",
                false,
                null
        ));

        // 6. Teacher / Mentor
        users.put(6L, new UserDTO(
                6L,
                "teacher",
                "teacher@hottopik.com",
                "teacher@123",
                "Cô Mai (Giáo viên TOPIK)",
                "ROLE_TEACHER",
                "Giáo viên (Mentor)",
                "ACTIVE",
                "https://api.dicebear.com/7.x/bottts/svg?seed=teacher",
                "0967890123",
                "TOPIK_6",
                "TOPIK_6",
                false,
                null
        ));
    }

    /**
     * Authenticate by either email or username with password
     */
    public Optional<UserDTO> authenticate(String identifier, String password) {
        if (identifier == null || password == null) {
            return Optional.empty();
        }
        String cleanId = identifier.trim().toLowerCase();
        String cleanPass = password.trim();

        return users.values().stream()
                .filter(u -> (u.getEmail().equalsIgnoreCase(cleanId) || u.getUsername().equalsIgnoreCase(cleanId))
                        && u.getPassword().equals(cleanPass))
                .findFirst();
    }

    public List<UserDTO> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    public List<UserDTO> getStaffUsers() {
        return users.values().stream()
                .filter(u -> u.getRole().equals("ROLE_ADMIN")
                        || u.getRole().equals("ROLE_CONTENT_MANAGER")
                        || u.getRole().equals("ROLE_OPERATION_MANAGER")
                        || u.getRole().equals("ROLE_TEACHER"))
                .collect(Collectors.toList());
    }

    public List<UserDTO> getLearners() {
        return users.values().stream()
                .filter(u -> u.getRole().equals("ROLE_VIP") || u.getRole().equals("ROLE_USER"))
                .collect(Collectors.toList());
    }

    public Optional<UserDTO> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }

    public Optional<UserDTO> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return users.values().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email.trim()))
                .findFirst();
    }

    public boolean toggleUserLock(Long id) {
        UserDTO user = users.get(id);
        if (user != null) {
            if ("ACTIVE".equalsIgnoreCase(user.getStatus())) {
                user.setStatus("LOCKED");
            } else {
                user.setStatus("ACTIVE");
            }
            return true;
        }
        return false;
    }
}
