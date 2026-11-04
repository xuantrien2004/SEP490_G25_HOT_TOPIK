package com.hottopik.service;

import com.hottopik.dto.UserDTO;
import com.hottopik.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationPersistenceTest {
    @Mock UserRepository users;
    @Mock UserSettingRepository settings;
    @Mock AuthTokenRepository tokens;
    @Mock EmailOutboxRepository outbox;
    @Mock AuthTokenService authTokens;
    @Mock EmailService email;
    @InjectMocks UserService service;

    @Test
    @SuppressWarnings("unchecked")
    void healthyDatabaseDoesNotAuthenticateAnUnpersistedFallbackAccount() {
        var fallback = new UserDTO(); fallback.setId(9006L); fallback.setEmail("sample@example.com"); fallback.setPassword("test-only");
        ((Map<Long,UserDTO>) ReflectionTestUtils.getField(service,"users")).put(fallback.getId(),fallback);
        when(users.findByEmail(fallback.getEmail())).thenReturn(Optional.empty());
        assertTrue(service.authenticate(fallback.getEmail(),"test-only").isEmpty());
        verify(users,never()).save(any());
    }

    @Test
    void persistedAccountKeepsItsDatabaseIdentityWhenAuthenticating() {
        var account=com.hottopik.entity.User.builder().id(7L).email("persisted@example.com")
                .passwordHash("test-only").displayName("Học viên đã đăng ký").role("STUDENT").status("ACTIVE").build();
        when(users.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        var authenticated=service.authenticate(account.getEmail(),"test-only").orElseThrow();
        assertEquals(7L,authenticated.getId());
        assertFalse(authenticated.isVip());
        assertNull(authenticated.getVipPackage());
        verify(users).save(account);
    }

    @Test
    void persistedVipAccountReadsItsActiveSubscriptionOnLogin() {
        var account=com.hottopik.entity.User.builder().id(8L).email("vip@example.com")
                .passwordHash("test-only").displayName("Học viên VIP").role("STUDENT").status("ACTIVE").build();
        when(users.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(users.countActiveSubscriptions(eq(8L),any(java.time.LocalDateTime.class))).thenReturn(1L);
        var authenticated=service.authenticate(account.getEmail(),"test-only").orElseThrow();
        assertEquals(8L,authenticated.getId());
        assertTrue(authenticated.isVip());
        assertEquals("VIP",authenticated.getVipPackage());
    }
}
