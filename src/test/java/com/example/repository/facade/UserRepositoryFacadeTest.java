package com.example.repository.facade;

import com.example.exception.NotFoundException;
import com.example.model.entity.UserEntity;
import com.example.repository.UserRepository;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MicronautTest
class UserRepositoryFacadeTest {

    @Inject
    private UserRepositoryFacade userRepositoryFacade;

    @Inject
    private UserRepository userRepository;

    @MockBean(UserRepository.class)
    UserRepository userRepository() {
        return mock(UserRepository.class);
    }

    private UserEntity createUserEntity() {
        return UserEntity.builder()
                .id(1L)
                .name("John Doe")
                .email("john.doe@example.com")
                .mobileNumber("+201234567890")
                .addrLine1("123 Main St")
                .addrCity("Cairo")
                .addrState("Cairo")
                .addrPostal("12345")
                .addrCountry("EG")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void create_ShouldSaveAndReturnUser() {
        // Arrange
        UserEntity user = createUserEntity();
        when(userRepository.save(user)).thenReturn(user);

        // Act
        UserEntity result = userRepositoryFacade.create(user);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("john.doe@example.com", result.getEmail());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void create_ShouldSetCreatedAtTimestamp() {
        // Arrange
        UserEntity user = createUserEntity();
        user.setCreatedAt(null); // Ensure createdAt is null before save

        UserEntity savedUser = createUserEntity();
        savedUser.setCreatedAt(LocalDateTime.now()); // Should be set by @PrePersist

        when(userRepository.save(user)).thenReturn(savedUser);

        // Act
        UserEntity result = userRepositoryFacade.create(user);

        // Assert
        assertNotNull(result.getCreatedAt());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void existsByEmail_ShouldReturnTrue_WhenEmailExists() {
        // Arrange
        String email = "existing@example.com";
        when(userRepository.existsByEmail(email)).thenReturn(true);

        // Act
        boolean result = userRepositoryFacade.existsByEmail(email);

        // Assert
        assertTrue(result);
        verify(userRepository, times(1)).existsByEmail(email);
    }

    @Test
    void existsByEmail_ShouldReturnFalse_WhenEmailDoesNotExist() {
        // Arrange
        String email = "nonexistent@example.com";
        when(userRepository.existsByEmail(email)).thenReturn(false);

        // Act
        boolean result = userRepositoryFacade.existsByEmail(email);

        // Assert
        assertFalse(result);
        verify(userRepository, times(1)).existsByEmail(email);
    }

    @Test
    void existsByEmail_ShouldPassCorrectEmailToRepository() {
        // Arrange
        String email = "test@example.com";
        when(userRepository.existsByEmail(email)).thenReturn(true);

        // Act
        userRepositoryFacade.existsByEmail(email);

        // Assert
        verify(userRepository, times(1)).existsByEmail(email);
    }

    @Test
    void getOrThrow_ShouldReturnUser_WhenUserExists() {
        // Arrange
        Long userId = 1L;
        UserEntity user = createUserEntity();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        UserEntity result = userRepositoryFacade.getOrThrow(userId);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("john.doe@example.com", result.getEmail());
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    void getOrThrow_ShouldThrowNotFoundException_WhenUserDoesNotExist() {
        // Arrange
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> userRepositoryFacade.getOrThrow(userId)
        );

        assertNotNull(exception.getMessage());
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    void getOrThrow_ShouldPassCorrectIdToRepository() {
        // Arrange
        Long userId = 123L;
        UserEntity user = createUserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        UserEntity result = userRepositoryFacade.getOrThrow(userId);

        // Assert
        assertEquals(userId, result.getId());
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    void getAllMembersById_ShouldReturnUsers_WhenUsersExist() {
        // Arrange
        List<Long> memberIds = Arrays.asList(1L, 2L, 3L);

        UserEntity user1 = createUserEntity();
        user1.setId(1L);
        UserEntity user2 = createUserEntity();
        user2.setId(2L);
        user2.setEmail("jane.doe@example.com");

        List<UserEntity> expectedUsers = Arrays.asList(user1, user2);

        when(userRepository.findByIdIn(memberIds)).thenReturn(expectedUsers);

        // Act
        List<UserEntity> result = userRepositoryFacade.getAllMembersById(memberIds);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        verify(userRepository, times(1)).findByIdIn(memberIds);
    }

    @Test
    void getAllMembersById_ShouldReturnEmptyList_WhenNoUsersFound() {
        // Arrange
        List<Long> memberIds = Arrays.asList(1L, 2L, 3L);
        when(userRepository.findByIdIn(memberIds)).thenReturn(Arrays.asList());

        // Act
        List<UserEntity> result = userRepositoryFacade.getAllMembersById(memberIds);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userRepository, times(1)).findByIdIn(memberIds);
    }

    @Test
    void getAllMembersById_ShouldPassCorrectIdsToRepository() {
        // Arrange
        List<Long> memberIds = Arrays.asList(1L, 2L, 3L);
        when(userRepository.findByIdIn(memberIds)).thenReturn(Arrays.asList());

        // Act
        userRepositoryFacade.getAllMembersById(memberIds);

        // Assert
        verify(userRepository, times(1)).findByIdIn(memberIds);
    }
}