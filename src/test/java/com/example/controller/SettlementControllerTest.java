package com.example.controller;

import com.example.event.KafkaProducer;
import com.example.event.model.EventMessage;
import com.example.model.dto.settlement.CreateSettlementRequest;
import com.example.model.dto.settlement.SettlementDto;
import com.example.model.mapper.SettlementMapper;
import com.example.repository.facade.GroupRepositoryFacade;
import com.example.repository.facade.SettlementRepositoryFacade;
import com.example.repository.facade.UserRepositoryFacade;
import com.example.service.SettlementService;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@MicronautTest
class SettlementControllerTest {
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

    // FIX: Create a proper mock for KafkaProducer interface
    @MockBean(KafkaProducer.class)
    @Replaces(KafkaProducer.class)
    KafkaProducer kafkaProducer() {
        // Mock all methods of KafkaProducer interface
        KafkaProducer mock = mock(KafkaProducer.class);
        // Mock the method mentioned in the error
        doNothing().when(mock).publishBalanceReminder(any(EventMessage.class));
        // Mock other methods if they exist
        doNothing().when(mock).publishSettlementConfirmed(any(EventMessage.class));
        return mock;
    }
    @MockBean(SettlementService.class)
    SettlementService settlementService() {
        return mock(SettlementService.class);
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

    private SettlementDto createSettlementDto() {
        return SettlementDto.builder()
                .settlementId(1L)
                .amount(new BigDecimal("100.00"))
                .fromUserId(1L)
                .toUserId(2L)
                .groupId(1L)
                .build();
    }

    @Test
    void addSettlement_ShouldReturn201_WhenValidRequest() {
        // Arrange
        CreateSettlementRequest request = createValidSettlementRequest();
        SettlementDto expectedDto = createSettlementDto();

        when(settlementService.addSettlement(any(CreateSettlementRequest.class))).thenReturn(expectedDto);

        // Act
        // Direct controller test without HttpClient
        SettlementController controller = new SettlementController(settlementService);
        HttpResponse<?> response = controller.addSettlement(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatus());
        assertNotNull(response.body());
        assertEquals(expectedDto, response.body());
        verify(settlementService, times(1)).addSettlement(any(CreateSettlementRequest.class));
    }

    @Test
    void confirmSettlement_ShouldReturn200_WhenValidSettlementId() {
        // Arrange
        Long settlementId = 1L;
        SettlementDto expectedDto = createSettlementDto();

        when(settlementService.confirmSettlement(settlementId)).thenReturn(expectedDto);

        // Act
        SettlementController controller = new SettlementController(settlementService);
        HttpResponse<SettlementDto> response = controller.confirmSettlement(settlementId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.body());
        assertEquals(expectedDto, response.body());
        verify(settlementService, times(1)).confirmSettlement(settlementId);
    }

    @Test
    void cancelSettlement_ShouldReturn200_WhenValidSettlementId() {
        // Arrange
        Long settlementId = 1L;
        SettlementDto expectedDto = createSettlementDto();

        when(settlementService.cancelSettlement(settlementId)).thenReturn(expectedDto);

        // Act
        SettlementController controller = new SettlementController(settlementService);
        HttpResponse<SettlementDto> response = controller.cancelSettlement(settlementId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.body());
        assertEquals(expectedDto, response.body());
        verify(settlementService, times(1)).cancelSettlement(settlementId);
    }

    @Test
    void addSettlement_ShouldPropagateException_WhenServiceThrows() {
        // Arrange
        CreateSettlementRequest request = createValidSettlementRequest();
        when(settlementService.addSettlement(any(CreateSettlementRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        SettlementController controller = new SettlementController(settlementService);
        assertThrows(RuntimeException.class, () -> controller.addSettlement(request));
        verify(settlementService, times(1)).addSettlement(any(CreateSettlementRequest.class));
    }
}