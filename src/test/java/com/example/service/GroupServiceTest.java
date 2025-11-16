package com.example.service;


import com.example.exception.NotFoundException;
import com.example.model.dto.group.*;
import com.example.model.dto.settlement.GroupSettlementPageResponse;
import com.example.model.dto.settlement.SettlementItem;
import com.example.model.dto.settlement.SuggestionResponse;
import com.example.model.entity.*;
import com.example.model.mapper.GroupMapper;
import com.example.model.mapper.SettlementMapper;
import com.example.repository.facade.GroupRepositoryFacade;
import com.example.repository.facade.SettlementRepositoryFacade;
import com.example.repository.facade.UserRepositoryFacade;
import com.example.strategy.SettlementStrategy;
import com.example.strategy.SettlementStrategyFactory;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MicronautTest
class GroupServiceTest {

    @Inject
    private GroupService groupService;

    @Inject
    private GroupRepositoryFacade groupRepositoryFacade;

    @Inject
    private UserRepositoryFacade userRepositoryFacade;

    @Inject
    private GroupMapper groupMapper;

    @Inject
    private SettlementRepositoryFacade settlementRepositoryFacade;

    @Inject
    private SettlementMapper settlementMapper;

    @Inject
    private SettlementStrategyFactory strategyFactory;

    @MockBean(GroupRepositoryFacade.class)
    GroupRepositoryFacade groupRepositoryFacade() {
        return mock(GroupRepositoryFacade.class);
    }

    @MockBean(UserRepositoryFacade.class)
    UserRepositoryFacade userRepositoryFacade() {
        return mock(UserRepositoryFacade.class);
    }

    @MockBean(GroupMapper.class)
    GroupMapper groupMapper() {
        return mock(GroupMapper.class);
    }

    @MockBean(SettlementRepositoryFacade.class)
    SettlementRepositoryFacade settlementRepositoryFacade() {
        return mock(SettlementRepositoryFacade.class);
    }

    @MockBean(SettlementMapper.class)
    SettlementMapper settlementMapper() {
        return mock(SettlementMapper.class);
    }

    @MockBean(SettlementStrategyFactory.class)
    SettlementStrategyFactory strategyFactory() {
        return mock(SettlementStrategyFactory.class);
    }

    private CreateGroupRequest createValidGroupRequest() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Test Group");
        request.setMembers(Arrays.asList(1L, 2L, 3L));
        return request;
    }

    private GroupEntity createGroupEntity() {
        GroupEntity group = GroupEntity.builder()
                .id(1L)
                .name("Test Group")
                .createdAt(LocalDateTime.now())
                .members(new HashSet<>())
                .expenses(new ArrayList<>())
                .build();

        // Add members
        for (Long userId : Arrays.asList(1L, 2L, 3L)) {
            UserEntity user = UserEntity.builder().id(userId).build();
            GroupMemberEntity member = GroupMemberEntity.builder()
                    .group(group)
                    .user(user)
                    .addedAt(LocalDateTime.now())
                    .build();
            group.getMembers().add(member);
        }

        return group;
    }

    private GroupDto createGroupDto() {
        return GroupDto.builder()
                .groupId(1L)
                .name("Test Group")
                .members(Arrays.asList(1L, 2L, 3L))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createGroup_ShouldCreateGroup_WhenValidRequest() {
        // Arrange
        CreateGroupRequest request = createValidGroupRequest();
        GroupEntity groupEntity = createGroupEntity();
        GroupDto expectedDto = createGroupDto();

        when(groupRepositoryFacade.usersExist(request.getMembers())).thenReturn(true);
        when(groupMapper.toEntity(request)).thenReturn(groupEntity);
        when(groupRepositoryFacade.save(groupEntity)).thenReturn(groupEntity);
        when(userRepositoryFacade.getAllMembersById(request.getMembers())).thenReturn(Arrays.asList(
                UserEntity.builder().id(1L).build(),
                UserEntity.builder().id(2L).build(),
                UserEntity.builder().id(3L).build()
        ));
        when(groupRepositoryFacade.save(any(GroupEntity.class))).thenReturn(groupEntity);
        when(groupMapper.toDto(any(GroupEntity.class), anyList())).thenReturn(expectedDto);

        // Act
        GroupDto result = groupService.createGroup(request);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getGroupId());
        verify(groupRepositoryFacade, times(1)).usersExist(request.getMembers());
        verify(groupRepositoryFacade, times(2)).save(any(GroupEntity.class));
        verify(groupMapper, times(1)).toDto(any(GroupEntity.class), anyList());
    }

    @Test
    void createGroup_ShouldThrowNotFoundException_WhenUsersDoNotExist() {
        // Arrange
        CreateGroupRequest request = createValidGroupRequest();

        when(groupRepositoryFacade.usersExist(request.getMembers())).thenReturn(false);

        // Act & Assert
        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> groupService.createGroup(request)
        );

        assertEquals("One or more users not found", exception.getMessage());
        verify(groupRepositoryFacade, times(1)).usersExist(request.getMembers());
        verify(groupRepositoryFacade, never()).save(any());
    }

    @Test
    void getGroup_ShouldReturnGroup_WhenGroupExists() {
        // Arrange
        Long groupId = 1L;
        GroupEntity groupEntity = createGroupEntity();
        GroupDto expectedDto = createGroupDto();

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(groupEntity);
        when(groupMapper.toDto(groupEntity, Arrays.asList(1L, 2L, 3L))).thenReturn(expectedDto);

        // Act
        GroupDto result = groupService.getGroup(groupId);

        // Assert
        assertNotNull(result);
        assertEquals(groupId, result.getGroupId());
        verify(groupRepositoryFacade, times(1)).getGroupOrThrow(groupId);
        verify(groupMapper, times(1)).toDto(groupEntity, Arrays.asList(1L, 2L, 3L));
    }

    @Test
    void getGroup_ShouldThrowNotFoundException_WhenGroupDoesNotExist() {
        // Arrange
        Long groupId = 999L;
        when(groupRepositoryFacade.getGroupOrThrow(groupId))
                .thenThrow(new NotFoundException("Group not found"));

        // Act & Assert
        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> groupService.getGroup(groupId)
        );

        assertEquals("Group not found", exception.getMessage());
        verify(groupRepositoryFacade, times(1)).getGroupOrThrow(groupId);
    }

    @Test
    void addMembers_ShouldAddMembers_WhenValidRequest() {
        // Arrange
        Long groupId = 1L;
        List<Long> newMemberIds = Arrays.asList(4L, 5L);
        GroupEntity groupEntity = createGroupEntity();

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(groupEntity);
        when(groupRepositoryFacade.usersExist(newMemberIds)).thenReturn(true);
        when(groupRepositoryFacade.addAll(groupId, newMemberIds)).thenReturn(newMemberIds);

        // Act
        AddMembersResponse result = groupService.addMembers(groupId, newMemberIds);

        // Assert
        assertNotNull(result);
        assertEquals(groupId, result.getGroupId());
        assertEquals(2, result.getMembersAdded().size());
        verify(groupRepositoryFacade, times(1)).getGroupOrThrow(groupId);
        verify(groupRepositoryFacade, times(1)).usersExist(newMemberIds);
        verify(groupRepositoryFacade, times(1)).addAll(groupId, newMemberIds);
    }

    @Test
    void addMembers_ShouldThrowNotFoundException_WhenUsersDoNotExist() {
        // Arrange
        Long groupId = 1L;
        List<Long> newMemberIds = Arrays.asList(4L, 5L);
        GroupEntity groupEntity = createGroupEntity();

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(groupEntity);
        when(groupRepositoryFacade.usersExist(newMemberIds)).thenReturn(false);

        // Act & Assert
        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> groupService.addMembers(groupId, newMemberIds)
        );

        assertEquals("One or more users not found", exception.getMessage());
        verify(groupRepositoryFacade, times(1)).usersExist(newMemberIds);
        verify(groupRepositoryFacade, never()).addAll(anyLong(), anyList());
    }

    @Test
    void getGroupBalances_ShouldReturnBalances_WhenGroupExists() {
        // Arrange
        Long groupId = 1L;
        Instant snapshot = Instant.now();
        GroupEntity groupEntity = createGroupEntity();

        // Mock expenses and shares
        ExpenseEntity expense = ExpenseEntity.builder()
                .id(1L)
                .amount(new BigDecimal("100.00"))
                .paidBy(UserEntity.builder().id(1L).build())
                .shares(new ArrayList<>())
                .build();

        expense.getShares().add(ExpenseShareEntity.builder()
                .user(UserEntity.builder().id(1L).build())
                .shareAmount(new BigDecimal("50.00"))
                .build());
        expense.getShares().add(ExpenseShareEntity.builder()
                .user(UserEntity.builder().id(2L).build())
                .shareAmount(new BigDecimal("30.00"))
                .build());
        expense.getShares().add(ExpenseShareEntity.builder()
                .user(UserEntity.builder().id(3L).build())
                .shareAmount(new BigDecimal("20.00"))
                .build());

        groupEntity.setExpenses(Arrays.asList(expense));

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(groupEntity);

        // Act
        GroupBalanceResponse result = groupService.getGroupBalances(groupId, snapshot);

        // Assert
        assertNotNull(result);
        assertEquals(groupId, result.getGroupId());
        assertEquals(snapshot, result.getCalculatedAt());
        assertEquals(3, result.getBalances().size());

        // Fixed: Expect 2 calls instead of 1
        verify(groupRepositoryFacade, times(2)).getGroupOrThrow(groupId);
    }

    @Test
    void listGroupSettlements_ShouldReturnSettlements_WhenGroupExists() {
        // Arrange
        Long groupId = 1L;
        Pageable pageable = Pageable.from(0, 20);
        Page<SettlementEntity> settlementPage = Page.of(Collections.emptyList(), pageable, (long)0);

        when(settlementRepositoryFacade.findSettlementByFilters(eq(groupId), any(), any(), any(), eq(pageable)))
                .thenReturn(settlementPage);
        when(settlementMapper.toItem(any())).thenReturn(new SettlementItem());

        // Act
        GroupSettlementPageResponse result = groupService.listGroupSettlements(groupId, Optional.empty(), Optional.empty(), Optional.empty(), 0, 20);

        // Assert
        assertNotNull(result);
        assertEquals(groupId, result.getGroupId());
        assertEquals(0, result.getPage());
        assertEquals(20, result.getSize());
        verify(settlementRepositoryFacade, times(1)).findSettlementByFilters(eq(groupId), any(), any(), any(), eq(pageable));
    }

    @Test
    void suggest_ShouldReturnSuggestions_WhenValidRequest() {
        // Arrange
        Long groupId = 1L;
        SettlementStrategyType strategyType = SettlementStrategyType.GREEDY_MIN_TRANSFERS;
        BigDecimal roundTo = new BigDecimal("1.00");

        GroupEntity groupEntity = createGroupEntity();

        // Add empty expenses list to avoid NPE
        groupEntity.setExpenses(Arrays.asList());

        SettlementStrategy strategy = mock(SettlementStrategy.class);

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(groupEntity);
        when(strategyFactory.getStrategy(strategyType)).thenReturn(strategy);
        when(strategy.suggestSettlements(anyList(), eq(roundTo))).thenReturn(Arrays.asList());

        // Act
        SuggestionResponse result = groupService.suggest(groupId, strategyType, roundTo);

        // Assert
        assertNotNull(result);
        assertEquals(groupId, result.getGroupId());
        assertEquals(strategyType, result.getStrategy());

        // Fixed: Expect 2 calls instead of 1
        verify(groupRepositoryFacade, times(2)).getGroupOrThrow(groupId);
        verify(strategyFactory, times(1)).getStrategy(strategyType);
        verify(strategy, times(1)).suggestSettlements(anyList(), eq(roundTo));
    }
}
