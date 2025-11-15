package com.example.controller;

import com.example.event.KafkaProducer;
import com.example.model.dto.user.AddressDto;
import com.example.model.dto.user.CreateUserRequest;
import com.example.model.dto.user.UserDto;
import com.example.service.UserService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@MicronautTest
public class UserControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    private UserService userService;

    @MockBean(UserService.class)
    UserService userService() {
        return mock(UserService.class);
    }

    @MockBean(KafkaProducer.class)
    KafkaProducer kafkaProducer() {
        return mock(KafkaProducer.class);
    }

    private CreateUserRequest createValidUserRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("John Doe");
        request.setEmail("john.doe@example.com");
        request.setMobileNumber("+201234567890");

        AddressDto address = new AddressDto();
        address.setLine1("123 Main St");
        address.setCity("Cairo");
        address.setState("Cairo");
        address.setPostalCode("12345");
        address.setCountry("EG");
        request.setAddress(address);
        return request;
    }

    @Test
    void createUser_ShouldReturnCreated_WhenValidRequest() {
        // Arrange
        CreateUserRequest request = createValidUserRequest();

        UserDto responseDto = UserDto.builder()
                .userId(1L)
                .name("John Doe")
                .email("john.doe@example.com")
                .mobileNumber("+201234567890")
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(responseDto);

        // Act - Make only ONE HTTP call
        var response = client.toBlocking().exchange(
                HttpRequest.POST("/api/user", request),
                UserDto.class
        );

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatus());
        verify(userService, times(1)).createUser(any(CreateUserRequest.class));
    }
    @Test
    void getUser_ShouldReturnOk_WhenUserExists() {
        // Arrange
        Long userId = 1L;
        UserDto userDto = UserDto.builder()
                .userId(userId)
                .name("John Doe")
                .email("john.doe@example.com")
                .mobileNumber("+201234567890")
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.getUserById(userId)).thenReturn(userDto);

        // Act
        var response = client.toBlocking().exchange("/api/users/" + userId, UserDto.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(userId, response.getBody().get().getUserId());
        verify(userService, times(1)).getUserById(userId);
    }
    @Test
    void getUser_ShouldReturnNotFound_WhenUserDoesNotExist_Debug() {
        // Arrange
        Long userId = 999L;
        when(userService.getUserById(userId))
                .thenThrow(new com.example.exception.NotFoundException("User not found"));

        try {
            // Act
            var response = client.toBlocking().exchange("/api/users/" + userId, UserDto.class);

            // If we get here, let's see what we actually got
            System.out.println("Response Status: " + response.getStatus());
            System.out.println("Response Body: " + response.getBody());

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatus());

        } catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
            // If we get an exception, let's see what it is
            System.out.println("Exception Status: " + e.getStatus());
            System.out.println("Exception Message: " + e.getMessage());
            System.out.println("Exception Response Body: " + e.getResponse().getBody(String.class).orElse("No body"));

            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return;
            }
            throw e;
        }

        verify(userService, times(1)).getUserById(userId);
    }

    @Test
    void createUser_ShouldReturnBadRequest_WhenInvalidEmail() {
        CreateUserRequest invalidRequest = createValidUserRequest();
        invalidRequest.setEmail("invalid-email"); // Invalid email format

        HttpClientResponseException exception = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                        HttpRequest.POST("/api/user", invalidRequest)
                )
        );

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());

        String responseBody = exception.getResponse().getBody(String.class).orElse("");
        assertTrue(responseBody.contains("VALIDATION_ERROR") || responseBody.contains("Email must be valid"));

        verify(userService, never()).createUser(any());
    }

    @Test
    void createUser_ShouldReturnBadRequest_WhenMissingRequiredFields() {
        // Arrange
        CreateUserRequest invalidRequest = new CreateUserRequest(); // Missing name and email

        HttpClientResponseException exception = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                        HttpRequest.POST("/api/user", invalidRequest)
                )
        );

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());

        String responseBody = exception.getResponse().getBody(String.class).orElse("");
        assertTrue(responseBody.contains("VALIDATION_ERROR"));

        verify(userService, never()).createUser(any());
    }

    @Test
    void createUser_ShouldReturnConflict_WhenEmailAlreadyExists() {
        CreateUserRequest request = createValidUserRequest();

        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new com.example.exception.ConflictException("Email already exists"));

        HttpClientResponseException exception = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                        HttpRequest.POST("/api/user", request),
                        UserDto.class
                )
        );

        // Assert
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());

        String responseBody = exception.getResponse().getBody(String.class).orElse("");
        assertTrue(responseBody.contains("CONFLICT") || responseBody.contains("Email already exists"));

        verify(userService, times(1)).createUser(any(CreateUserRequest.class));
    }
}

