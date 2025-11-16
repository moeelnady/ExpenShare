package com.example.controller;
import com.example.event.KafkaProducer;
import com.example.event.model.EventMessage;
import com.example.model.dto.expense.ShareDto;
import com.example.model.dto.group.*;
import com.example.model.dto.settlement.GroupSettlementPageResponse;
import com.example.model.dto.settlement.SuggestionRequest;
import com.example.model.dto.settlement.SuggestionResponse;
import com.example.model.entity.SettlementStrategyType;
import com.example.service.GroupService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MicronautTest
class GroupControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    private GroupService groupService;

    @Inject
    private KafkaProducer kafkaProducer;

    @MockBean(GroupService.class)
    GroupService groupService() {
        return mock(GroupService.class);
    }

    @MockBean(KafkaProducer.class)
    KafkaProducer kafkaProducer() {
        return mock(KafkaProducer.class);
    }

    private CreateGroupRequest createValidGroupRequest() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Test Group");
        request.setMembers(Arrays.asList(1L, 2L, 3L));
        return request;
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
    void createGroup_ShouldReturnCreated_WhenValidRequest() {
        // Arrange
        CreateGroupRequest request = createValidGroupRequest();
        GroupDto responseDto = createGroupDto();

        when(groupService.createGroup(any(CreateGroupRequest.class))).thenReturn(responseDto);

        // Act
        var response = client.toBlocking().exchange(
                HttpRequest.POST("/api/groups", request),
                GroupDto.class
        );

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatus());
        assertEquals(1L, response.getBody().get().getGroupId());
        verify(groupService, times(1)).createGroup(any(CreateGroupRequest.class));

        // Verify Kafka event
        verify(kafkaProducer, times(1)).publishGroupCreated(any(EventMessage.class));
    }

    @Test
    void createGroup_ShouldReturnBadRequest_WhenInvalidRequest() {
        // Arrange
        CreateGroupRequest invalidRequest = new CreateGroupRequest(); // Missing required fields

        // Act & Assert
        HttpClientResponseException exception = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                        HttpRequest.POST("/api/groups", invalidRequest)
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(groupService, never()).createGroup(any());
    }

    @Test
    void getGroup_ShouldReturnOk_WhenGroupExists() {
        // Arrange
        Long groupId = 1L;
        GroupDto groupDto = createGroupDto();

        when(groupService.getGroup(groupId)).thenReturn(groupDto);

        // Act
        var response = client.toBlocking().exchange("/api/groups/" + groupId, GroupDto.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(groupId, response.getBody().get().getGroupId());
        verify(groupService, times(1)).getGroup(groupId);
    }

    @Test
    void getGroup_ShouldReturnNotFound_WhenGroupDoesNotExist() {
        // Arrange
        Long groupId = 999L;
        when(groupService.getGroup(groupId))
                .thenThrow(new com.example.exception.NotFoundException("Group not found"));

        // Act & Assert
        HttpClientResponseException exception = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange("/api/groups/" + groupId, GroupDto.class)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(groupService, times(1)).getGroup(groupId);
    }

    @Test
    void addMembers_ShouldReturnOk_WhenValidRequest() {
        // Arrange
        Long groupId = 1L;
        AddMembersRequest addRequest = new AddMembersRequest();
        addRequest.setMembers(Arrays.asList(4L, 5L));

        AddMembersResponse addResponse = new AddMembersResponse(groupId, Arrays.asList(4L, 5L), 5);

        when(groupService.addMembers(groupId, addRequest.getMembers())).thenReturn(addResponse);

        // Act
        var response = client.toBlocking().exchange(
                HttpRequest.POST("/api/groups/" + groupId + "/members", addRequest),
                AddMembersResponse.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(2, response.getBody().get().getMembersAdded().size());
        verify(groupService, times(1)).addMembers(groupId, addRequest.getMembers());
    }

    @Test
    void addMembers_ShouldReturnBadRequest_WhenEmptyMembersList() {
        // Arrange
        Long groupId = 1L;
        AddMembersRequest invalidRequest = new AddMembersRequest();
        invalidRequest.setMembers(Arrays.asList()); // Empty list

        // Act & Assert
        HttpClientResponseException exception = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                        HttpRequest.POST("/api/groups/" + groupId + "/members", invalidRequest)
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(groupService, never()).addMembers(anyLong(), anyList());
    }

    @Test
    void getGroupBalances_ShouldReturnBalances_WhenGroupExists() {
        // Arrange
        Long groupId = 1L;
        GroupBalanceResponse balanceResponse = GroupBalanceResponse.builder()
                .groupId(groupId)
                .balances(Arrays.asList(
                        new ShareDto(1L, new BigDecimal("50.00")),
                        new ShareDto(2L, new BigDecimal("-30.00")),
                        new ShareDto(3L, new BigDecimal("-20.00"))
                ))
                .calculatedAt(Instant.now())
                .build();

        when(groupService.getGroupBalances(eq(groupId), any(Instant.class))).thenReturn(balanceResponse);

        // Act
        var response = client.toBlocking().exchange("/api/groups/" + groupId + "/balances", GroupBalanceResponse.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(groupId, response.getBody().get().getGroupId());
        assertEquals(3, response.getBody().get().getBalances().size());
        verify(groupService, times(1)).getGroupBalances(eq(groupId), any(Instant.class));
    }

    @Test
    void listGroupSettlements_ShouldReturnSettlements_WhenGroupExists() {
        // Arrange
        Long groupId = 1L;
        GroupSettlementPageResponse settlementsResponse = GroupSettlementPageResponse.builder()
                .groupId(groupId)
                .items(Arrays.asList())
                .page(0)
                .size(20)
                .total(0)
                .build();

        when(groupService.listGroupSettlements(eq(groupId), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(settlementsResponse);

        // Act
        var response = client.toBlocking().exchange("/api/groups/" + groupId + "/settlements", GroupSettlementPageResponse.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(groupId, response.getBody().get().getGroupId());
        verify(groupService, times(1)).listGroupSettlements(eq(groupId), any(), any(), any(), eq(0), eq(20));
    }

    @Test
    void suggestSettlements_ShouldReturnSuggestions_WhenValidRequest() {
        // Arrange
        Long groupId = 1L;
        SuggestionRequest suggestionRequest = new SuggestionRequest();
        suggestionRequest.setStrategy(SettlementStrategyType.GREEDY_MIN_TRANSFERS);
        suggestionRequest.setRoundTo(new BigDecimal("1.00"));

        SuggestionResponse suggestionResponse = new SuggestionResponse(groupId, Arrays.asList(), 0, SettlementStrategyType.GREEDY_MIN_TRANSFERS);

        when(groupService.suggest(eq(groupId), eq(SettlementStrategyType.GREEDY_MIN_TRANSFERS), any(BigDecimal.class)))
                .thenReturn(suggestionResponse);

        // Act
        var response = client.toBlocking().exchange(
                HttpRequest.POST("/api/groups/" + groupId + "/settlements/suggest", suggestionRequest),
                SuggestionResponse.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(groupId, response.getBody().get().getGroupId());
        verify(groupService, times(1)).suggest(eq(groupId), eq(SettlementStrategyType.GREEDY_MIN_TRANSFERS), any(BigDecimal.class));
    }
}
