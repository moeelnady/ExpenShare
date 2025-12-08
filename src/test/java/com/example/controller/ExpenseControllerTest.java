package com.example.controller;

import com.example.event.KafkaProducer;
import com.example.event.model.EventMessage;
import com.example.model.dto.expense.CreateExpenseRequest;
import com.example.model.dto.expense.ExpenseDto;
import com.example.service.ExpenseService;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@MicronautTest(transactional = false)
class ExpenseControllerTest {

    @Inject
    private ExpenseService expenseService;

    @MockBean(ExpenseService.class)
    ExpenseService expenseService() {
        return mock(ExpenseService.class);
    }
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

    private CreateExpenseRequest createValidExpenseRequest() {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(1L);
        request.setPaidBy(1L);
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("Test Expense");
        request.setSplitType(com.example.model.entity.SplitType.EQUAL);
        request.setParticipants(Arrays.asList(1L, 2L, 3L));
        return request;
    }

    private ExpenseDto createExpenseDto() {
        return ExpenseDto.builder()
                .expenseId(1L)
                .groupId(1L)
                .paidBy(1L)
                .amount(new BigDecimal("100.00"))
                .description("Test Expense")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void addExpense_ShouldReturn201Created_WhenValidRequest() {
        // Arrange
        CreateExpenseRequest request = createValidExpenseRequest();
        ExpenseDto expectedDto = createExpenseDto();

        when(expenseService.addExpense(any(CreateExpenseRequest.class))).thenReturn(expectedDto);

        // Act - Direct controller invocation
        ExpenseController controller = new ExpenseController(expenseService);
        HttpResponse<?> response = controller.addExpense(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatus());
        assertNotNull(response.body());
        assertEquals(expectedDto, response.body());
        verify(expenseService, times(1)).addExpense(any(CreateExpenseRequest.class));
    }

    @Test
    void addExpense_ShouldPropagateException_WhenServiceThrows() {
        // Arrange
        CreateExpenseRequest request = createValidExpenseRequest();
        when(expenseService.addExpense(any(CreateExpenseRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        ExpenseController controller = new ExpenseController(expenseService);
        assertThrows(RuntimeException.class, () -> controller.addExpense(request));
        verify(expenseService, times(1)).addExpense(any(CreateExpenseRequest.class));
    }

    @Test
    void addExpense_ShouldCallServiceWithCorrectRequest() {
        // Arrange
        CreateExpenseRequest request = createValidExpenseRequest();
        ExpenseDto expectedDto = createExpenseDto();

        when(expenseService.addExpense(request)).thenReturn(expectedDto);

        // Act
        ExpenseController controller = new ExpenseController(expenseService);
        HttpResponse<?> response = controller.addExpense(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatus());
        verify(expenseService, times(1)).addExpense(request);
    }
}