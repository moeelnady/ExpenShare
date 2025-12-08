package com.example.service;

import com.example.event.KafkaProducer;
import com.example.event.model.EventMessage;
import com.example.exception.ValidationException;
import com.example.model.dto.expense.CreateExpenseRequest;
import com.example.model.dto.expense.ExpenseDto;
import com.example.model.dto.expense.ShareDto;
import com.example.model.dto.expense.ShareRequest;
import com.example.model.entity.*;
import com.example.model.mapper.ExpenseMapper;
import com.example.repository.facade.ExpenseRepositoryFacade;
import com.example.repository.facade.GroupRepositoryFacade;
import com.example.repository.facade.UserRepositoryFacade;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MicronautTest(transactional = false)
class ExpenseServiceTest {

    @Inject
    private ExpenseService expenseService;

    @Inject
    private ExpenseRepositoryFacade expenseRepositoryFacade;

    @Inject
    private GroupRepositoryFacade groupRepositoryFacade;

    @Inject
    private UserRepositoryFacade userRepositoryFacade;

    @Inject
    private ExpenseMapper expenseMapper;

    @Inject
    private KafkaProducer kafkaProducer;

    private GroupEntity testGroup;
    private UserEntity testUser1;
    private UserEntity testUser2;
    private UserEntity testUser3;

    @BeforeEach
    void setUp() {
        testGroup = GroupEntity.builder()
                .id(1L)
                .name("Test Group")
                .build();

        testUser1 = UserEntity.builder().id(1L).name("User 1").build();
        testUser2 = UserEntity.builder().id(2L).name("User 2").build();
        testUser3 = UserEntity.builder().id(3L).name("User 3").build();
    }

    @MockBean(ExpenseRepositoryFacade.class)
    ExpenseRepositoryFacade expenseRepositoryFacade() {
        return mock(ExpenseRepositoryFacade.class);
    }

    @MockBean(GroupRepositoryFacade.class)
    GroupRepositoryFacade groupRepositoryFacade() {
        return mock(GroupRepositoryFacade.class);
    }

    @MockBean(UserRepositoryFacade.class)
    UserRepositoryFacade userRepositoryFacade() {
        return mock(UserRepositoryFacade.class);
    }

    @MockBean(ExpenseMapper.class)
    ExpenseMapper expenseMapper() {
        return mock(ExpenseMapper.class);
    }

    @MockBean(KafkaProducer.class)
    @Replaces(KafkaProducer.class)
    KafkaProducer kafkaProducer() {
        KafkaProducer mock = mock(KafkaProducer.class);
        doNothing().when(mock).publishExpenseAdded(any(EventMessage.class));
        doNothing().when(mock).publishBalanceReminder(any(EventMessage.class));
        doNothing().when(mock).publishSettlementConfirmed(any(EventMessage.class));
        return mock;
    }

    @Test
    void addExpense_ShouldCreateExpense_WhenValidEqualSplitRequest() {
        // Arrange
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(1L);
        request.setPaidBy(1L);
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("Test Expense");
        request.setSplitType(SplitType.EQUAL);
        request.setParticipants(Arrays.asList(1L, 2L, 3L));

        ExpenseEntity expenseEntity = ExpenseEntity.builder()
                .id(1L)
                .amount(new BigDecimal("100.00"))
                .description("Test Expense")
                .splitType(SplitType.EQUAL)
                .createdAt(LocalDateTime.now())
                .group(testGroup)
                .paidBy(testUser1)
                .build();

        ExpenseDto expectedDto = ExpenseDto.builder()
                .expenseId(1L)
                .groupId(1L)
                .paidBy(1L)
                .amount(new BigDecimal("100.00"))
                .description("Test Expense")
                .split(Arrays.asList(
                        new ShareDto(1L, new BigDecimal("33.33")),
                        new ShareDto(2L, new BigDecimal("33.33")),
                        new ShareDto(3L, new BigDecimal("33.34"))
                ))
                .createdAt(LocalDateTime.now())
                .build();

        when(groupRepositoryFacade.getGroupOrThrow(1L)).thenReturn(testGroup);
        when(userRepositoryFacade.getOrThrow(1L)).thenReturn(testUser1);
        when(userRepositoryFacade.getOrThrow(2L)).thenReturn(testUser2);
        when(userRepositoryFacade.getOrThrow(3L)).thenReturn(testUser3);
        when(groupRepositoryFacade.isMember(1L, 1L)).thenReturn(true);
        when(expenseMapper.toEntity(request)).thenReturn(expenseEntity);
        when(expenseRepositoryFacade.saveWithShares(any(ExpenseEntity.class), anyList()))
                .thenReturn(expenseEntity);
        when(expenseMapper.toDto(any(ExpenseEntity.class), anyList())).thenReturn(expectedDto);

        // Act
        ExpenseDto result = expenseService.addExpense(request);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getExpenseId());
        verify(groupRepositoryFacade, times(1)).getGroupOrThrow(1L);
        verify(userRepositoryFacade, times(4)).getOrThrow(anyLong());
        verify(groupRepositoryFacade, times(1)).isMember(1L, 1L);
        verify(expenseRepositoryFacade, times(1)).saveWithShares(any(ExpenseEntity.class), anyList());
        verify(kafkaProducer, times(1)).publishExpenseAdded(any(EventMessage.class));
    }

    @Test
    void addExpense_ShouldThrowValidationException_WhenPaidByUserNotMember() {
        // Arrange
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(1L);
        request.setPaidBy(1L);
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("Test Expense");
        request.setSplitType(SplitType.EQUAL);
        request.setParticipants(Arrays.asList(1L, 2L, 3L));

        when(groupRepositoryFacade.getGroupOrThrow(1L)).thenReturn(testGroup);
        when(userRepositoryFacade.getOrThrow(1L)).thenReturn(testUser1);
        when(groupRepositoryFacade.isMember(1L, 1L)).thenReturn(false);

        // Act & Assert
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> expenseService.addExpense(request)
        );

        assertEquals("PaidBy user is not a member of this group", exception.getMessage());
        verify(expenseRepositoryFacade, never()).saveWithShares(any(), any());
        verify(kafkaProducer, never()).publishExpenseAdded(any());
    }

    @Test
    void addExpense_ShouldThrowValidationException_WhenExactSplitSumDoesNotMatchTotal() {
        // Arrange
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(1L);
        request.setPaidBy(1L);
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("Test Expense");
        request.setSplitType(SplitType.EXACT);
        // Invalid sum: 50 + 40 = 90, should be 100
        request.setShares(Arrays.asList(
                new ShareRequest(1L, new BigDecimal("50.00"), 0),
                new ShareRequest(2L, new BigDecimal("40.00"), 0)
        ));

        when(groupRepositoryFacade.getGroupOrThrow(1L)).thenReturn(testGroup);
        when(userRepositoryFacade.getOrThrow(1L)).thenReturn(testUser1);
        when(groupRepositoryFacade.isMember(1L, 1L)).thenReturn(true);
        ExpenseEntity expenseEntity = ExpenseEntity.builder()
                .id(1L)
                .amount(new BigDecimal("100.00"))
                .splitType(SplitType.EXACT)
                .build();
        when(expenseMapper.toEntity(request)).thenReturn(expenseEntity);

        // Act & Assert
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> expenseService.addExpense(request)
        );

        assertEquals("Split amounts must total 100.00", exception.getMessage());
        verify(expenseRepositoryFacade, never()).saveWithShares(any(), any());
        verify(kafkaProducer, never()).publishExpenseAdded(any());
    }

    @Test
    void addExpense_ShouldThrowValidationException_WhenPercentSplitSumNot100() {
        // Arrange
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(1L);
        request.setPaidBy(1L);
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("Test Expense");
        request.setSplitType(SplitType.PERCENT);
        // Invalid percentages: 60 + 30 = 90, should be 100
        request.setShares(Arrays.asList(
                new ShareRequest(1L, null, 60),
                new ShareRequest(2L, null, 30)
        ));

        when(groupRepositoryFacade.getGroupOrThrow(1L)).thenReturn(testGroup);
        when(userRepositoryFacade.getOrThrow(1L)).thenReturn(testUser1);
        when(groupRepositoryFacade.isMember(1L, 1L)).thenReturn(true);
        ExpenseEntity expenseEntity = ExpenseEntity.builder()
                .id(1L)
                .amount(new BigDecimal("100.00"))
                .splitType(SplitType.PERCENT)
                .build();
        when(expenseMapper.toEntity(request)).thenReturn(expenseEntity);

        // Act & Assert
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> expenseService.addExpense(request)
        );

        assertEquals("Split percentages must total 100", exception.getMessage());
        verify(expenseRepositoryFacade, never()).saveWithShares(any(), any());
        verify(kafkaProducer, never()).publishExpenseAdded(any());
    }

    @Test
    void addExpense_ShouldPublishKafkaEvent_WhenExpenseCreated() {
        // Arrange
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(1L);
        request.setPaidBy(1L);
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("Test Expense");
        request.setSplitType(SplitType.EQUAL);
        request.setParticipants(Arrays.asList(1L, 2L, 3L));

        ExpenseEntity expenseEntity = ExpenseEntity.builder()
                .id(1L)
                .amount(new BigDecimal("100.00"))
                .description("Test Expense")
                .splitType(SplitType.EQUAL)
                .createdAt(LocalDateTime.now())
                .group(testGroup)
                .paidBy(testUser1)
                .build();

        ExpenseDto expectedDto = ExpenseDto.builder()
                .expenseId(1L)
                .groupId(1L)
                .paidBy(1L)
                .amount(new BigDecimal("100.00"))
                .description("Test Expense")
                .split(Arrays.asList(
                        new ShareDto(1L, new BigDecimal("33.33")),
                        new ShareDto(2L, new BigDecimal("33.33")),
                        new ShareDto(3L, new BigDecimal("33.34"))
                ))
                .createdAt(LocalDateTime.now())
                .build();

        when(groupRepositoryFacade.getGroupOrThrow(1L)).thenReturn(testGroup);
        when(userRepositoryFacade.getOrThrow(1L)).thenReturn(testUser1);
        when(userRepositoryFacade.getOrThrow(2L)).thenReturn(testUser2);
        when(userRepositoryFacade.getOrThrow(3L)).thenReturn(testUser3);
        when(groupRepositoryFacade.isMember(1L, 1L)).thenReturn(true);
        when(expenseMapper.toEntity(request)).thenReturn(expenseEntity);
        when(expenseRepositoryFacade.saveWithShares(any(ExpenseEntity.class), anyList()))
                .thenReturn(expenseEntity);
        when(expenseMapper.toDto(any(ExpenseEntity.class), anyList())).thenReturn(expectedDto);

        // Act
        expenseService.addExpense(request);

        // Assert
        verify(kafkaProducer, times(1)).publishExpenseAdded(any(EventMessage.class));
    }

    @Test
    void addExpense_ExactSplit_WithNullAmount_ShouldThrowValidationException() {
        // Arrange
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(1L);
        request.setPaidBy(1L);
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("Test");
        request.setSplitType(SplitType.EXACT);
        // One null amount, one valid amount
        request.setShares(Arrays.asList(
                new ShareRequest(1L, new BigDecimal("50.00"), 0),
                new ShareRequest(2L, null, 0) // Null amount - will be filtered out
        ));

        when(groupRepositoryFacade.getGroupOrThrow(1L)).thenReturn(testGroup);
        when(userRepositoryFacade.getOrThrow(1L)).thenReturn(testUser1);
        when(groupRepositoryFacade.isMember(1L, 1L)).thenReturn(true);
        ExpenseEntity expenseEntity = ExpenseEntity.builder()
                .id(1L)
                .amount(new BigDecimal("100.00"))
                .splitType(SplitType.EXACT)
                .build();
        when(expenseMapper.toEntity(request)).thenReturn(expenseEntity);

        // Act & Assert - With filter(Objects::nonNull), null amount is filtered out
        // So sum = 50.00, total = 100.00 → ValidationException
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> expenseService.addExpense(request)
        );

        assertEquals("Split amounts must total 100.00", exception.getMessage());
    }

    @Test
    void addExpense_ShouldHandleSingleParticipant_ForEqualSplit() {
        // Arrange
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(1L);
        request.setPaidBy(1L);
        request.setAmount(new BigDecimal("50.00"));
        request.setDescription("Single Participant");
        request.setSplitType(SplitType.EQUAL);
        request.setParticipants(Collections.singletonList(1L)); // Only payer

        ExpenseEntity expenseEntity = ExpenseEntity.builder()
                .id(1L)
                .amount(new BigDecimal("50.00"))
                .description("Single Participant")
                .splitType(SplitType.EQUAL)
                .createdAt(LocalDateTime.now())
                .group(testGroup)
                .paidBy(testUser1)
                .build();

        ExpenseDto expectedDto = ExpenseDto.builder()
                .expenseId(1L)
                .groupId(1L)
                .paidBy(1L)
                .amount(new BigDecimal("50.00"))
                .description("Single Participant")
                .split(Collections.singletonList(new ShareDto(1L, new BigDecimal("-50.00"))))
                .createdAt(LocalDateTime.now())
                .build();

        when(groupRepositoryFacade.getGroupOrThrow(1L)).thenReturn(testGroup);
        when(userRepositoryFacade.getOrThrow(1L)).thenReturn(testUser1);
        when(groupRepositoryFacade.isMember(1L, 1L)).thenReturn(true);
        when(expenseMapper.toEntity(request)).thenReturn(expenseEntity);
        when(expenseRepositoryFacade.saveWithShares(any(ExpenseEntity.class), anyList()))
                .thenReturn(expenseEntity);
        when(expenseMapper.toDto(any(ExpenseEntity.class), anyList())).thenReturn(expectedDto);

        // Act
        ExpenseDto result = expenseService.addExpense(request);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getExpenseId());
    }

    @Test
    void addExpense_WithEqualSplit_ShouldUseAllGroupMembers_WhenParticipantsNotSpecified() {
        // Arrange
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(1L);
        request.setPaidBy(1L);
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("Test Expense");
        request.setSplitType(SplitType.EQUAL);
        request.setParticipants(null); // Will use all group members

        ExpenseEntity expenseEntity = ExpenseEntity.builder()
                .id(1L)
                .amount(new BigDecimal("100.00"))
                .description("Test Expense")
                .splitType(SplitType.EQUAL)
                .createdAt(LocalDateTime.now())
                .group(testGroup)
                .paidBy(testUser1)
                .build();

        ExpenseDto expectedDto = ExpenseDto.builder()
                .expenseId(1L)
                .groupId(1L)
                .paidBy(1L)
                .amount(new BigDecimal("100.00"))
                .description("Test Expense")
                .split(Arrays.asList(
                        new ShareDto(1L, new BigDecimal("25.00")),
                        new ShareDto(2L, new BigDecimal("25.00")),
                        new ShareDto(3L, new BigDecimal("25.00")),
                        new ShareDto(4L, new BigDecimal("25.00"))
                ))
                .createdAt(LocalDateTime.now())
                .build();

        List<Long> allGroupMembers = Arrays.asList(1L, 2L, 3L, 4L);
        UserEntity user4 = UserEntity.builder().id(4L).name("User 4").build();

        when(groupRepositoryFacade.getGroupOrThrow(1L)).thenReturn(testGroup);
        when(userRepositoryFacade.getOrThrow(1L)).thenReturn(testUser1);
        when(userRepositoryFacade.getOrThrow(2L)).thenReturn(testUser2);
        when(userRepositoryFacade.getOrThrow(3L)).thenReturn(testUser3);
        when(userRepositoryFacade.getOrThrow(4L)).thenReturn(user4);
        when(groupRepositoryFacade.isMember(1L, 1L)).thenReturn(true);
        when(groupRepositoryFacade.findUserIdsByGroupId(1L)).thenReturn(allGroupMembers);
        when(expenseMapper.toEntity(request)).thenReturn(expenseEntity);
        when(expenseRepositoryFacade.saveWithShares(any(ExpenseEntity.class), anyList()))
                .thenReturn(expenseEntity);
        when(expenseMapper.toDto(any(ExpenseEntity.class), anyList())).thenReturn(expectedDto);

        // Act
        ExpenseDto result = expenseService.addExpense(request);

        // Assert
        assertNotNull(result);
        verify(groupRepositoryFacade, times(1)).findUserIdsByGroupId(1L);
        verify(userRepositoryFacade, times(5)).getOrThrow(anyLong()); // paidBy + 4 group members
    }
}