package com.example.service;

import com.example.event.KafkaProducer;
import com.example.event.model.EventMessage;
import com.example.exception.ConflictException;
import com.example.exception.NotFoundException;
import com.example.exception.ValidationException;
import com.example.model.dto.settlement.CreateSettlementRequest;
import com.example.model.dto.settlement.SettlementDto;
import com.example.model.entity.*;
import com.example.model.mapper.SettlementMapper;
import com.example.repository.facade.GroupRepositoryFacade;
import com.example.repository.facade.SettlementRepositoryFacade;
import com.example.repository.facade.UserRepositoryFacade;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MicronautTest
class SettlementServiceTest {

    @Inject
    private SettlementService settlementService;

    @Inject
    private GroupRepositoryFacade groupRepositoryFacade;

    @Inject
    private UserRepositoryFacade userRepositoryFacade;

    @Inject
    private SettlementMapper settlementMapper;

    @Inject
    private SettlementRepositoryFacade settlementRepositoryFacade;

    @Inject
    private KafkaProducer kafkaProducer;

    @MockBean(GroupRepositoryFacade.class)
    GroupRepositoryFacade groupRepositoryFacade() {
        return mock(GroupRepositoryFacade.class);
    }

    @MockBean(UserRepositoryFacade.class)
    UserRepositoryFacade userRepositoryFacade() {
        return mock(UserRepositoryFacade.class);
    }

    @MockBean(SettlementMapper.class)
    SettlementMapper settlementMapper() {
        return mock(SettlementMapper.class);
    }

    @MockBean(SettlementRepositoryFacade.class)
    SettlementRepositoryFacade settlementRepositoryFacade() {
        return mock(SettlementRepositoryFacade.class);
    }

    @MockBean(KafkaProducer.class)
    KafkaProducer kafkaProducer() {
        return mock(KafkaProducer.class);
    }

    private CreateSettlementRequest createValidSettlementRequest() {
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setGroupId(1L);
        request.setFromUserId(1L);
        request.setToUserId(2L);
        request.setAmount(new BigDecimal("100.00"));
        request.setEnforceOwedLimit(true);
        return request;
    }

    private GroupEntity createGroupEntity() {
        return GroupEntity.builder()
                .id(1L)
                .name("Test Group")
                .members(new HashSet<>())
                .expenses(new ArrayList<>())
                .build();
    }

    private UserEntity createUserEntity(Long userId) {
        return UserEntity.builder()
                .id(userId)
                .name("User " + userId)
                .build();
    }

    private SettlementEntity createSettlementEntity(Long id, Status status) {
        return SettlementEntity.builder()
                .id(id)
                .amount(new BigDecimal("100.00"))
                .status(status)
                .fromUser(createUserEntity(1L))
                .toUser(createUserEntity(2L))
                .group(createGroupEntity())
                .build();
    }

    private SettlementDto createSettlementDto() {
        return SettlementDto.builder()
                .settlementId(1L)
                .amount(new BigDecimal("100.00"))
                .status(Status.CONFIRMED)
                .fromUserId(1L)
                .toUserId(2L)
                .groupId(1L)
                .build();
    }

    @Test
    void addSettlement_ShouldCreateSettlement_WhenValidRequest() {
        // Arrange
        CreateSettlementRequest request = createValidSettlementRequest();
        GroupEntity group = createGroupEntity();
        UserEntity fromUser = createUserEntity(1L);
        UserEntity toUser = createUserEntity(2L);
        SettlementEntity settlementEntity = createSettlementEntity(1L, Status.PENDING);
        SettlementDto expectedDto = createSettlementDto();

        when(groupRepositoryFacade.getGroupOrThrow(request.getGroupId())).thenReturn(group);
        when(userRepositoryFacade.getOrThrow(request.getFromUserId())).thenReturn(fromUser);
        when(userRepositoryFacade.getOrThrow(request.getToUserId())).thenReturn(toUser);
        when(groupRepositoryFacade.isMember(request.getGroupId(), request.getFromUserId())).thenReturn(true);
        when(groupRepositoryFacade.isMember(request.getGroupId(), request.getToUserId())).thenReturn(true);
        when(settlementMapper.toEntity(request)).thenReturn(settlementEntity);
        when(settlementRepositoryFacade.saveSettlement(any(SettlementEntity.class))).thenReturn(settlementEntity);
        when(settlementMapper.toDto(settlementEntity)).thenReturn(expectedDto);

        // Mock calculateOwed to return amount greater than settlement amount
        group.setExpenses(createExpensesWithOwedAmount(new BigDecimal("150.00")));

        // Act
        SettlementDto result = settlementService.addSettlement(request);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getSettlementId());
        verify(groupRepositoryFacade, times(1)).getGroupOrThrow(request.getGroupId());
        verify(userRepositoryFacade, times(2)).getOrThrow(anyLong());
        verify(groupRepositoryFacade, times(2)).isMember(anyLong(), anyLong());
        verify(settlementRepositoryFacade, times(1)).saveSettlement(any(SettlementEntity.class));
        verify(settlementMapper, times(1)).toDto(any(SettlementEntity.class));
        verify(kafkaProducer, never()).publishSettlementConfirmed(any());
    }

    @Test
    void addSettlement_ShouldPublishEvent_WhenSettlementIsConfirmed() {
        // Arrange
        CreateSettlementRequest request = createValidSettlementRequest();
        request.setEnforceOwedLimit(false);
        GroupEntity group = createGroupEntity();
        UserEntity fromUser = createUserEntity(1L);
        UserEntity toUser = createUserEntity(2L);
        SettlementEntity settlementEntity = createSettlementEntity(1L, Status.CONFIRMED);
        SettlementDto expectedDto = createSettlementDto();

        when(groupRepositoryFacade.getGroupOrThrow(request.getGroupId())).thenReturn(group);
        when(userRepositoryFacade.getOrThrow(request.getFromUserId())).thenReturn(fromUser);
        when(userRepositoryFacade.getOrThrow(request.getToUserId())).thenReturn(toUser);
        when(groupRepositoryFacade.isMember(request.getGroupId(), request.getFromUserId())).thenReturn(true);
        when(groupRepositoryFacade.isMember(request.getGroupId(), request.getToUserId())).thenReturn(true);
        when(settlementMapper.toEntity(request)).thenReturn(settlementEntity);
        when(settlementRepositoryFacade.saveSettlement(any(SettlementEntity.class))).thenReturn(settlementEntity);
        when(settlementMapper.toDto(settlementEntity)).thenReturn(expectedDto);

        // Act
        SettlementDto result = settlementService.addSettlement(request);

        // Assert
        assertNotNull(result);
        verify(kafkaProducer, times(1)).publishSettlementConfirmed(any(EventMessage.class));
    }

    @Test
    void addSettlement_ShouldThrowValidationException_WhenSelfSettlement() {
        // Arrange
        CreateSettlementRequest request = createValidSettlementRequest();
        request.setFromUserId(1L);
        request.setToUserId(1L);

        when(groupRepositoryFacade.getGroupOrThrow(request.getGroupId())).thenReturn(createGroupEntity());
        when(userRepositoryFacade.getOrThrow(request.getFromUserId())).thenReturn(createUserEntity(1L));
        when(userRepositoryFacade.getOrThrow(request.getToUserId())).thenReturn(createUserEntity(1L));

        // Act & Assert
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> settlementService.addSettlement(request)
        );

        assertEquals("Can't make self settlement", exception.getMessage());
        verify(settlementRepositoryFacade, never()).saveSettlement(any());
    }

    @Test
    void addSettlement_ShouldThrowNotFoundException_WhenUserNotMember() {
        // Arrange
        CreateSettlementRequest request = createValidSettlementRequest();
        GroupEntity group = createGroupEntity();

        when(groupRepositoryFacade.getGroupOrThrow(request.getGroupId())).thenReturn(group);
        when(userRepositoryFacade.getOrThrow(request.getFromUserId())).thenReturn(createUserEntity(1L));
        when(userRepositoryFacade.getOrThrow(request.getToUserId())).thenReturn(createUserEntity(2L));
        when(groupRepositoryFacade.isMember(request.getGroupId(), request.getFromUserId())).thenReturn(false);
        when(groupRepositoryFacade.isMember(request.getGroupId(), request.getToUserId())).thenReturn(true);

        // Act & Assert
        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> settlementService.addSettlement(request)
        );

        assertEquals("one or two users are not members", exception.getMessage());
        verify(settlementRepositoryFacade, never()).saveSettlement(any());
    }

    @Test
    void addSettlement_ShouldThrowValidationException_WhenAmountExceedsOwed() {
        // Arrange
        CreateSettlementRequest request = createValidSettlementRequest();
        request.setAmount(new BigDecimal("200.00"));
        GroupEntity group = createGroupEntity();
        UserEntity fromUser = createUserEntity(1L);
        UserEntity toUser = createUserEntity(2L);

        when(groupRepositoryFacade.getGroupOrThrow(request.getGroupId())).thenReturn(group);
        when(userRepositoryFacade.getOrThrow(request.getFromUserId())).thenReturn(fromUser);
        when(userRepositoryFacade.getOrThrow(request.getToUserId())).thenReturn(toUser);
        when(groupRepositoryFacade.isMember(request.getGroupId(), request.getFromUserId())).thenReturn(true);
        when(groupRepositoryFacade.isMember(request.getGroupId(), request.getToUserId())).thenReturn(true);

        // Mock calculateOwed to return amount less than settlement amount
        group.setExpenses(createExpensesWithOwedAmount(new BigDecimal("150.00")));

        // Act & Assert
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> settlementService.addSettlement(request)
        );

        assertEquals("Cannot settle more than owed", exception.getMessage());
        verify(settlementRepositoryFacade, never()).saveSettlement(any());
    }

    @Test
    void confirmSettlement_ShouldConfirmSettlement_WhenPending() {
        // Arrange
        Long settlementId = 1L;
        SettlementEntity settlementEntity = createSettlementEntity(settlementId, Status.PENDING);
        SettlementDto expectedDto = createSettlementDto();

        when(settlementRepositoryFacade.getByIdOrThrow(settlementId)).thenReturn(settlementEntity);
        when(settlementRepositoryFacade.updateSettlment(any(SettlementEntity.class))).thenReturn(settlementEntity);
        when(settlementMapper.toDto(any(SettlementEntity.class))).thenReturn(expectedDto);

        // Act
        SettlementDto result = settlementService.confirmSettlement(settlementId);

        // Assert
        assertNotNull(result);
        assertEquals(Status.CONFIRMED, settlementEntity.getStatus());
        assertNotNull(settlementEntity.getConfirmedAt());
        verify(settlementRepositoryFacade, times(1)).updateSettlment(settlementEntity);
        verify(kafkaProducer, times(1)).publishSettlementConfirmed(any(EventMessage.class));
    }

    @Test
    void confirmSettlement_ShouldThrowConflictException_WhenAlreadyConfirmed() {
        // Arrange
        Long settlementId = 1L;
        SettlementEntity settlementEntity = createSettlementEntity(settlementId, Status.CONFIRMED);

        when(settlementRepositoryFacade.getByIdOrThrow(settlementId)).thenReturn(settlementEntity);

        // Act & Assert
        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> settlementService.confirmSettlement(settlementId)
        );

        assertEquals("Already confirmed", exception.getMessage());
        verify(settlementRepositoryFacade, never()).updateSettlment(any());
        verify(kafkaProducer, never()).publishSettlementConfirmed(any());
    }

    @Test
    void confirmSettlement_ShouldThrowConflictException_WhenCanceled() {
        // Arrange
        Long settlementId = 1L;
        SettlementEntity settlementEntity = createSettlementEntity(settlementId, Status.CANCELED);

        when(settlementRepositoryFacade.getByIdOrThrow(settlementId)).thenReturn(settlementEntity);

        // Act & Assert
        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> settlementService.confirmSettlement(settlementId)
        );

        assertEquals("Cannot confirm a canceled settlement", exception.getMessage());
        verify(settlementRepositoryFacade, never()).updateSettlment(any());
    }

    @Test
    void cancelSettlement_ShouldCancelSettlement_WhenPending() {
        // Arrange
        Long settlementId = 1L;
        SettlementEntity settlementEntity = createSettlementEntity(settlementId, Status.PENDING);
        SettlementDto expectedDto = createSettlementDto();

        when(settlementRepositoryFacade.getByIdOrThrow(settlementId)).thenReturn(settlementEntity);
        when(settlementRepositoryFacade.updateSettlment(any(SettlementEntity.class))).thenReturn(settlementEntity);
        when(settlementMapper.toDto(any(SettlementEntity.class))).thenReturn(expectedDto);

        // Act
        SettlementDto result = settlementService.cancelSettlement(settlementId);

        // Assert
        assertNotNull(result);
        assertEquals(Status.CANCELED, settlementEntity.getStatus());
        verify(settlementRepositoryFacade, times(1)).updateSettlment(settlementEntity);
    }

    @Test
    void cancelSettlement_ShouldThrowConflictException_WhenNotPending() {
        // Arrange
        Long settlementId = 1L;
        SettlementEntity settlementEntity = createSettlementEntity(settlementId, Status.CONFIRMED);

        when(settlementRepositoryFacade.getByIdOrThrow(settlementId)).thenReturn(settlementEntity);

        // Act & Assert
        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> settlementService.cancelSettlement(settlementId)
        );

        assertEquals("Only pending settlements can be canceled", exception.getMessage());
        verify(settlementRepositoryFacade, never()).updateSettlment(any());
    }

    private List<ExpenseEntity> createExpensesWithOwedAmount(BigDecimal owedAmount) {
        ExpenseEntity expense = ExpenseEntity.builder()
                .id(1L)
                .amount(new BigDecimal("200.00"))
                .paidBy(createUserEntity(2L))
                .shares(new ArrayList<>())
                .build();

        ExpenseShareEntity share = ExpenseShareEntity.builder()
                .id(1L)
                .user(createUserEntity(1L))
                .shareAmount(owedAmount)
                .expense(expense)
                .build();

        expense.getShares().add(share);
        return Arrays.asList(expense);
    }
}