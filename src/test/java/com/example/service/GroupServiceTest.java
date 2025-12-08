package com.example.service;


import com.example.exception.NotFoundException;
import com.example.model.dto.group.*;
import com.example.model.dto.settlement.GroupSettlementPageResponse;
import com.example.model.dto.settlement.SettlementItem;
import com.example.model.dto.settlement.SettlementSuggestion;
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
import java.util.stream.Collectors;

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
    void suggest_ShouldReturnSuggestions_WhenValidGroupAndStrategy() {
        // Arrange
        Long groupId = 1L;
        SettlementStrategyType strategyType = SettlementStrategyType.GREEDY_MIN_TRANSFERS;
        BigDecimal roundTo = new BigDecimal("1.00");

        GroupEntity group = createGroupEntity();

        // Mock balances
        Map<Long, BigDecimal> balances = new HashMap<>();
        balances.put(1L, new BigDecimal("50.00"));  // User 1 is owed 50
        balances.put(2L, new BigDecimal("-30.00")); // User 2 owes 30
        balances.put(3L, new BigDecimal("-20.00")); // User 3 owes 20

        // Mock strategy
        SettlementStrategy mockStrategy = mock(SettlementStrategy.class);
        List<SettlementSuggestion> expectedSuggestions = Arrays.asList(
                new SettlementSuggestion(2L, 1L, new BigDecimal("30.00")),
                new SettlementSuggestion(3L, 1L, new BigDecimal("20.00"))
        );

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(group);
        when(strategyFactory.getStrategy(strategyType)).thenReturn(mockStrategy);
        when(mockStrategy.suggestSettlements(anyList(), eq(roundTo))).thenReturn(expectedSuggestions);

        // Act
        SuggestionResponse result = groupService.suggest(groupId, strategyType, roundTo);

        // Assert
        assertNotNull(result);
        assertEquals(groupId, result.getGroupId());
        assertEquals(strategyType, result.getStrategy());
        assertEquals(2, result.getSuggestions().size());
        assertEquals(2, result.getTotalTransfers());

        // Verify suggestions
        assertEquals(2L, result.getSuggestions().getFirst().getFromUserId());
        assertEquals(1L, result.getSuggestions().get(0).getToUserId());
        assertEquals(new BigDecimal("30.00"), result.getSuggestions().get(0).getAmount());

        assertEquals(3L, result.getSuggestions().get(1).getFromUserId());
        assertEquals(1L, result.getSuggestions().get(1).getToUserId());
        assertEquals(new BigDecimal("20.00"), result.getSuggestions().get(1).getAmount());

        verify(groupRepositoryFacade, times(1)).getGroupOrThrow(groupId);
        verify(strategyFactory, times(1)).getStrategy(strategyType);
        verify(mockStrategy, times(1)).suggestSettlements(anyList(), eq(roundTo));
    }

    @Test
    void suggest_ShouldHandleZeroRoundTo_WhenRoundToIsNull() {
        // Arrange
        Long groupId = 1L;
        SettlementStrategyType strategyType = SettlementStrategyType.SMALLEST_AMOUNTS_FIRST;

        GroupEntity group = createGroupEntity();

        Map<Long, BigDecimal> balances = new HashMap<>();
        balances.put(1L, new BigDecimal("75.50"));
        balances.put(2L, new BigDecimal("-75.50"));

        SettlementStrategy mockStrategy = mock(SettlementStrategy.class);
        List<SettlementSuggestion> expectedSuggestions = List.of(
                new SettlementSuggestion(2L, 1L, new BigDecimal("75.50"))
        );

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(group);
        when(strategyFactory.getStrategy(strategyType)).thenReturn(mockStrategy);
        when(mockStrategy.suggestSettlements(anyList(), any())).thenReturn(expectedSuggestions);

        // Act - Pass null for roundTo
        SuggestionResponse result = groupService.suggest(groupId, strategyType, null);

        // Assert
        assertNotNull(result);
        verify(mockStrategy, times(1)).suggestSettlements(anyList(), any());
    }

    @Test
    void suggest_ShouldHandleComplexBalanceScenario() {
        // Arrange
        Long groupId = 1L;
        SettlementStrategyType strategyType = SettlementStrategyType.GREEDY_MIN_TRANSFERS;
        BigDecimal roundTo = new BigDecimal("5.00");

        GroupEntity group = createGroupEntity();

        // Complex balance scenario with multiple creditors and debtors
        Map<Long, BigDecimal> balances = new HashMap<>();
        balances.put(1L, new BigDecimal("150.00"));  // Creditor 1
        balances.put(2L, new BigDecimal("-80.00"));  // Debtor 1
        balances.put(3L, new BigDecimal("-40.00"));  // Debtor 2
        balances.put(4L, new BigDecimal("-30.00"));  // Debtor 3

        SettlementStrategy mockStrategy = mock(SettlementStrategy.class);
        List<SettlementSuggestion> expectedSuggestions = Arrays.asList(
                new SettlementSuggestion(2L, 1L, new BigDecimal("80.00")),
                new SettlementSuggestion(3L, 1L, new BigDecimal("40.00")),
                new SettlementSuggestion(4L, 1L, new BigDecimal("30.00"))
        );

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(group);
        when(strategyFactory.getStrategy(strategyType)).thenReturn(mockStrategy);
        when(mockStrategy.suggestSettlements(anyList(), eq(roundTo))).thenReturn(expectedSuggestions);

        // Act
        SuggestionResponse result = groupService.suggest(groupId, strategyType, roundTo);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getTotalTransfers());
        assertEquals(3, result.getSuggestions().size());
        verify(mockStrategy, times(1)).suggestSettlements(anyList(), eq(roundTo));
    }

    @Test
    void suggest_ShouldHandleZeroBalances() {
        // Arrange
        Long groupId = 1L;
        SettlementStrategyType strategyType = SettlementStrategyType.GREEDY_MIN_TRANSFERS;
        BigDecimal roundTo = new BigDecimal("1.00");

        GroupEntity group = createGroupEntity();

        // All balances are zero (no debts)
        Map<Long, BigDecimal> balances = new HashMap<>();
        balances.put(1L, BigDecimal.ZERO);
        balances.put(2L, BigDecimal.ZERO);
        balances.put(3L, BigDecimal.ZERO);

        SettlementStrategy mockStrategy = mock(SettlementStrategy.class);
        List<SettlementSuggestion> expectedSuggestions = Collections.emptyList();

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(group);
        when(strategyFactory.getStrategy(strategyType)).thenReturn(mockStrategy);
        when(mockStrategy.suggestSettlements(anyList(), eq(roundTo))).thenReturn(expectedSuggestions);

        // Act
        SuggestionResponse result = groupService.suggest(groupId, strategyType, roundTo);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalTransfers());
        assertTrue(result.getSuggestions().isEmpty());
        verify(mockStrategy, times(1)).suggestSettlements(anyList(), eq(roundTo));
    }

    @Test
    void suggest_ShouldHandleNegativeRoundTo() {
        // Arrange
        Long groupId = 1L;
        SettlementStrategyType strategyType = SettlementStrategyType.GREEDY_MIN_TRANSFERS;
        BigDecimal negativeRoundTo = new BigDecimal("-1.00"); // Should be handled gracefully

        GroupEntity group = createGroupEntity();

        Map<Long, BigDecimal> balances = new HashMap<>();
        balances.put(1L, new BigDecimal("50.00"));
        balances.put(2L, new BigDecimal("-50.00"));

        SettlementStrategy mockStrategy = mock(SettlementStrategy.class);
        List<SettlementSuggestion> expectedSuggestions = Arrays.asList(
                new SettlementSuggestion(2L, 1L, new BigDecimal("50.00"))
        );

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(group);
        when(strategyFactory.getStrategy(strategyType)).thenReturn(mockStrategy);
        when(mockStrategy.suggestSettlements(anyList(), eq(negativeRoundTo))).thenReturn(expectedSuggestions);

        // Act
        SuggestionResponse result = groupService.suggest(groupId, strategyType, negativeRoundTo);

        // Assert
        assertNotNull(result);
        verify(mockStrategy, times(1)).suggestSettlements(anyList(), eq(negativeRoundTo));
    }

    @Test
    void suggest_ShouldHandleDifferentStrategyTypes() {
        // Test all available strategy types
        SettlementStrategyType[] strategyTypes = SettlementStrategyType.values();

        for (SettlementStrategyType strategyType : strategyTypes) {
            // Arrange
            Long groupId = 1L;
            BigDecimal roundTo = new BigDecimal("1.00");

            GroupEntity group = createGroupEntity();

            Map<Long, BigDecimal> balances = new HashMap<>();
            balances.put(1L, new BigDecimal("100.00"));
            balances.put(2L, new BigDecimal("-100.00"));

            SettlementStrategy mockStrategy = mock(SettlementStrategy.class);
            List<SettlementSuggestion> expectedSuggestions = Arrays.asList(
                    new SettlementSuggestion(2L, 1L, new BigDecimal("100.00"))
            );

            when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(group);
            when(strategyFactory.getStrategy(strategyType)).thenReturn(mockStrategy);
            when(mockStrategy.suggestSettlements(anyList(), eq(roundTo))).thenReturn(expectedSuggestions);

            // Act
            SuggestionResponse result = groupService.suggest(groupId, strategyType, roundTo);

            // Assert
            assertNotNull(result);
            assertEquals(strategyType, result.getStrategy());

            // Reset mocks for next iteration
            reset(groupRepositoryFacade, strategyFactory, mockStrategy);
        }
    }

    @Test
    void suggest_ShouldProperlyConvertBalancesToUserBalanceList() {
        // Arrange
        Long groupId = 1L;
        SettlementStrategyType strategyType = SettlementStrategyType.GREEDY_MIN_TRANSFERS;
        BigDecimal roundTo = new BigDecimal("1.00");

        GroupEntity group = createGroupEntity();

        // Add expenses and shares to the group so getBalancesByGroupId returns correct balances
        List<ExpenseEntity> expenses = new ArrayList<>();

        // Create an expense that results in the desired balances
        ExpenseEntity expense1 = ExpenseEntity.builder()
                .id(1L)
                .amount(new BigDecimal("150.75"))
                .paidBy(UserEntity.builder().id(1L).build())
                .shares(new ArrayList<>())
                .build();

        expense1.getShares().add(ExpenseShareEntity.builder()
                .user(UserEntity.builder().id(1L).build())
                .shareAmount(new BigDecimal("150.75"))  // Positive for creditor
                .build());
        expense1.getShares().add(ExpenseShareEntity.builder()
                .user(UserEntity.builder().id(2L).build())
                .shareAmount(new BigDecimal("-80.25"))  // Negative for debtor
                .build());
        expense1.getShares().add(ExpenseShareEntity.builder()
                .user(UserEntity.builder().id(3L).build())
                .shareAmount(new BigDecimal("-70.50"))  // Negative for debtor
                .build());

        expenses.add(expense1);
        group.setExpenses(expenses);

        SettlementStrategy mockStrategy = mock(SettlementStrategy.class);

        // Capture the UserBalance list passed to the strategy
        List<UserBalance> capturedUserBalances = new ArrayList<>();
        when(mockStrategy.suggestSettlements(anyList(), eq(roundTo)))
                .thenAnswer(invocation -> {
                    List<UserBalance> userBalances = invocation.getArgument(0);
                    capturedUserBalances.addAll(userBalances);
                    return Arrays.asList(
                            new SettlementSuggestion(2L, 1L, new BigDecimal("80.25")),
                            new SettlementSuggestion(3L, 1L, new BigDecimal("70.50"))
                    );
                });

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(group);
        when(strategyFactory.getStrategy(strategyType)).thenReturn(mockStrategy);

        // Act
        SuggestionResponse result = groupService.suggest(groupId, strategyType, roundTo);

        // Assert
        assertNotNull(result);

        // Verify UserBalance conversion
        assertEquals(3, capturedUserBalances.size());

        // Find each user's balance
        Map<Long, BigDecimal> capturedMap = capturedUserBalances.stream()
                .collect(Collectors.toMap(
                        UserBalance::getUserId,
                        UserBalance::getBalance
                ));

        assertEquals(new BigDecimal("150.75"), capturedMap.get(1L));
        assertEquals(new BigDecimal("-80.25"), capturedMap.get(2L));
        assertEquals(new BigDecimal("-70.50"), capturedMap.get(3L));

        verify(mockStrategy, times(1)).suggestSettlements(anyList(), eq(roundTo));
    }
    @Test
    void suggest_ShouldHandleGroupWithNoExpenses() {
        // Arrange
        Long groupId = 1L;
        SettlementStrategyType strategyType = SettlementStrategyType.GREEDY_MIN_TRANSFERS;
        BigDecimal roundTo = new BigDecimal("1.00");

        GroupEntity group = createGroupEntity();
        group.setExpenses(Collections.emptyList()); // No expenses

        // When there are no expenses, balances should be empty
        Map<Long, BigDecimal> balances = Collections.emptyMap();

        SettlementStrategy mockStrategy = mock(SettlementStrategy.class);
        List<SettlementSuggestion> expectedSuggestions = Collections.emptyList();

        when(groupRepositoryFacade.getGroupOrThrow(groupId)).thenReturn(group);
        when(strategyFactory.getStrategy(strategyType)).thenReturn(mockStrategy);
        when(mockStrategy.suggestSettlements(anyList(), eq(roundTo))).thenReturn(expectedSuggestions);

        // Act
        SuggestionResponse result = groupService.suggest(groupId, strategyType, roundTo);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalTransfers());
        assertTrue(result.getSuggestions().isEmpty());
        verify(mockStrategy, times(1)).suggestSettlements(anyList(), eq(roundTo));
    }

    @Test
    void suggest_ShouldThrowException_WhenGroupNotFound() {
        // Arrange
        Long nonExistentGroupId = 999L;
        SettlementStrategyType strategyType = SettlementStrategyType.GREEDY_MIN_TRANSFERS;
        BigDecimal roundTo = new BigDecimal("1.00");

        when(groupRepositoryFacade.getGroupOrThrow(nonExistentGroupId))
                .thenThrow(new NotFoundException("Group not found"));

        // Act & Assert
        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> groupService.suggest(nonExistentGroupId, strategyType, roundTo)
        );

        assertEquals("Group not found", exception.getMessage());
        verify(strategyFactory, never()).getStrategy(any());
    }
}
