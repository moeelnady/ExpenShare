package com.example.repository.facade;

import com.example.exception.NotFoundException;
import com.example.model.entity.GroupEntity;
import com.example.model.entity.UserEntity;
import com.example.repository.GroupMemberRepository;
import com.example.repository.GroupRepository;
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
class GroupRepositoryFacadeTest {

    @Inject
    private GroupRepositoryFacade groupRepositoryFacade;

    @Inject
    private GroupRepository groupRepository;

    @Inject
    private GroupMemberRepository groupMemberRepository;

    @Inject
    private UserRepositoryFacade userRepositoryFacade;

    @MockBean(GroupRepository.class)
    GroupRepository groupRepository() {
        return mock(GroupRepository.class);
    }

    @MockBean(GroupMemberRepository.class)
    GroupMemberRepository groupMemberRepository() {
        return mock(GroupMemberRepository.class);
    }

    @MockBean(UserRepositoryFacade.class)
    UserRepositoryFacade userRepositoryFacade() {
        return mock(UserRepositoryFacade.class);
    }

    private GroupEntity createGroupEntity() {
        return GroupEntity.builder()
                .id(1L)
                .name("Test Group")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private UserEntity createUserEntity(Long id) {
        return UserEntity.builder()
                .id(id)
                .name("User " + id)
                .email("user" + id + "@example.com")
                .build();
    }

    @Test
    void save_ShouldSaveAndReturnGroup() {
        // Arrange
        GroupEntity group = createGroupEntity();
        when(groupRepository.save(group)).thenReturn(group);

        // Act
        GroupEntity result = groupRepositoryFacade.save(group);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test Group", result.getName());
        verify(groupRepository, times(1)).save(group);
    }

    @Test
    void usersExist_ShouldReturnTrue_WhenAllUsersExist() {
        // Arrange
        List<Long> userIds = Arrays.asList(1L, 2L, 3L);
        List<UserEntity> users = Arrays.asList(
                createUserEntity(1L),
                createUserEntity(2L),
                createUserEntity(3L)
        );

        when(userRepositoryFacade.getAllMembersById(userIds)).thenReturn(users);

        // Act
        boolean result = groupRepositoryFacade.usersExist(userIds);

        // Assert
        assertTrue(result);
        verify(userRepositoryFacade, times(1)).getAllMembersById(userIds);
    }

    @Test
    void usersExist_ShouldReturnFalse_WhenSomeUsersDoNotExist() {
        // Arrange
        List<Long> userIds = Arrays.asList(1L, 2L, 3L);
        List<UserEntity> users = Arrays.asList(
                createUserEntity(1L),
                createUserEntity(2L)
                // Missing user 3L
        );

        when(userRepositoryFacade.getAllMembersById(userIds)).thenReturn(users);

        // Act
        boolean result = groupRepositoryFacade.usersExist(userIds);

        // Assert
        assertFalse(result);
        verify(userRepositoryFacade, times(1)).getAllMembersById(userIds);
    }

    @Test
    void getGroupOrThrow_ShouldReturnGroup_WhenGroupExists() {
        // Arrange
        Long groupId = 1L;
        GroupEntity group = createGroupEntity();
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // Act
        GroupEntity result = groupRepositoryFacade.getGroupOrThrow(groupId);

        // Assert
        assertNotNull(result);
        assertEquals(groupId, result.getId());
        verify(groupRepository, times(1)).findById(groupId);
    }

    @Test
    void getGroupOrThrow_ShouldThrowNotFoundException_WhenGroupDoesNotExist() {
        // Arrange
        Long groupId = 999L;
        when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

        // Act & Assert
        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> groupRepositoryFacade.getGroupOrThrow(groupId)
        );

        assertEquals("Group not found", exception.getMessage());
        verify(groupRepository, times(1)).findById(groupId);
    }

    @Test
    void addAll_ShouldAddNewMembers_WhenMembersDoNotExist() {
        // Arrange
        Long groupId = 1L;
        List<Long> userIds = Arrays.asList(4L, 5L);
        GroupEntity group = createGroupEntity();
        List<UserEntity> users = Arrays.asList(
                createUserEntity(4L),
                createUserEntity(5L)
        );

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(userRepositoryFacade.getAllMembersById(userIds)).thenReturn(users);
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, 4L)).thenReturn(false);
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, 5L)).thenReturn(false);
        when(groupMemberRepository.saveAll(anyList())).thenReturn(Arrays.asList());

        // Act
        List<Long> result = groupRepositoryFacade.addAll(groupId, userIds);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(4L));
        assertTrue(result.contains(5L));
        verify(groupRepository, times(1)).findById(groupId);
        verify(userRepositoryFacade, times(1)).getAllMembersById(userIds);
        verify(groupMemberRepository, times(1)).saveAll(anyList());
    }

    @Test
    void addAll_ShouldSkipExistingMembers() {
        // Arrange
        Long groupId = 1L;
        List<Long> userIds = Arrays.asList(4L, 5L);
        GroupEntity group = createGroupEntity();
        List<UserEntity> users = Arrays.asList(
                createUserEntity(4L),
                createUserEntity(5L)
        );

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(userRepositoryFacade.getAllMembersById(userIds)).thenReturn(users);
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, 4L)).thenReturn(true); // Already exists
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, 5L)).thenReturn(false);

        // Act
        List<Long> result = groupRepositoryFacade.addAll(groupId, userIds);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.contains(5L)); // Only new member added
        verify(groupMemberRepository, times(1)).saveAll(anyList());
    }

    @Test
    void isMember_ShouldReturnTrue_WhenUserIsMember() {
        // Arrange
        Long groupId = 1L;
        Long userId = 1L;
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);

        // Act
        boolean result = groupRepositoryFacade.isMember(groupId, userId);

        // Assert
        assertTrue(result);
        verify(groupMemberRepository, times(1)).existsByGroupIdAndUserId(groupId, userId);
    }

    @Test
    void isMember_ShouldReturnFalse_WhenUserIsNotMember() {
        // Arrange
        Long groupId = 1L;
        Long userId = 999L;
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

        // Act
        boolean result = groupRepositoryFacade.isMember(groupId, userId);

        // Assert
        assertFalse(result);
        verify(groupMemberRepository, times(1)).existsByGroupIdAndUserId(groupId, userId);
    }
}
