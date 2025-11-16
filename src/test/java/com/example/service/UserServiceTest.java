package com.example.service;

import com.example.event.KafkaProducer;
import com.example.event.model.EventMessage;
import com.example.exception.ConflictException;
import com.example.exception.NotFoundException;
import com.example.model.dto.user.AddressDto;
import com.example.model.dto.user.CreateUserRequest;
import com.example.model.dto.user.UserDto;
import com.example.model.entity.UserEntity;
import com.example.model.mapper.UserMapper;
import com.example.repository.facade.UserRepositoryFacade;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MicronautTest
class UserServiceTest {

    @Inject
    private UserService userService;

    @Inject
    private UserRepositoryFacade userRepositoryFacade;

    @Inject
    private UserMapper userMapper;

    @Inject
    private KafkaProducer kafkaProducer;

    @MockBean(UserRepositoryFacade.class)
    UserRepositoryFacade userRepositoryFacade() {
        return mock(UserRepositoryFacade.class);
    }

    @MockBean(UserMapper.class)
    UserMapper userMapper() {
        return mock(UserMapper.class);
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

    private UserDto createUserDto() {
        return UserDto.builder()
                .userId(1L)
                .name("John Doe")
                .email("john.doe@example.com")
                .mobileNumber("+201234567890")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createUser_ShouldCreateUser_WhenEmailDoesNotExist() {
        // Arrange
        CreateUserRequest request = createValidUserRequest();
        UserEntity entity = createUserEntity();
        UserDto expectedDto = createUserDto();

        when(userRepositoryFacade.existsByEmail("john.doe@example.com")).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(entity);
        when(userRepositoryFacade.create(entity)).thenReturn(entity);
        when(userMapper.toDto(entity)).thenReturn(expectedDto);

        // Act
        UserDto result = userService.createUser(request);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals("john.doe@example.com", result.getEmail());
        assertEquals("John Doe", result.getName());

        verify(userRepositoryFacade, times(1)).existsByEmail("john.doe@example.com");
        verify(userRepositoryFacade, times(1)).create(entity);
        verify(userMapper, times(1)).toDto(entity);

        // Verify Kafka message was sent
        ArgumentCaptor<EventMessage> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(kafkaProducer, times(1)).publishUserCreated(eventCaptor.capture());

        EventMessage capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals(1L, capturedEvent.getPayload().get("userId"));
    }

    @Test
    void createUser_ShouldThrowConflictException_WhenEmailExists() {
        // Arrange
        CreateUserRequest request = createValidUserRequest();

        when(userRepositoryFacade.existsByEmail("john.doe@example.com")).thenReturn(true);

        // Act & Assert
        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> userService.createUser(request)
        );

        assertEquals("Email already exists", exception.getMessage());
        verify(userRepositoryFacade, times(1)).existsByEmail("john.doe@example.com");
        verify(userRepositoryFacade, never()).create(any());
        verify(kafkaProducer, never()).publishUserCreated(any());
    }

    @Test
    void createUser_ShouldMapAllFieldsCorrectly() {
        // Arrange
        CreateUserRequest request = createValidUserRequest();
        UserEntity entity = createUserEntity();
        UserDto expectedDto = createUserDto();

        when(userRepositoryFacade.existsByEmail(anyString())).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(entity);
        when(userRepositoryFacade.create(entity)).thenReturn(entity);
        when(userMapper.toDto(entity)).thenReturn(expectedDto);

        // Act
        UserDto result = userService.createUser(request);

        // Assert
        assertNotNull(result);
        verify(userMapper, times(1)).toEntity(request);

        // Verify that the entity passed to repository has all fields set
        ArgumentCaptor<UserEntity> entityCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepositoryFacade, times(1)).create(entityCaptor.capture());

        UserEntity capturedEntity = entityCaptor.getValue();
        assertEquals("John Doe", capturedEntity.getName());
        assertEquals("john.doe@example.com", capturedEntity.getEmail());
        assertEquals("+201234567890", capturedEntity.getMobileNumber());
        assertEquals("123 Main St", capturedEntity.getAddrLine1());
        assertEquals("Cairo", capturedEntity.getAddrCity());
        assertEquals("EG", capturedEntity.getAddrCountry());
    }

    @Test
    void createUser_ShouldHandleNullAddressGracefully() {
        // Arrange
        CreateUserRequest request = createValidUserRequest();
        request.setAddress(null); // No address provided

        UserEntity entity = UserEntity.builder()
                .id(1L)
                .name("John Doe")
                .email("john.doe@example.com")
                .mobileNumber("+201234567890")
                .build();

        UserDto expectedDto = createUserDto();

        when(userRepositoryFacade.existsByEmail(anyString())).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(entity);
        when(userRepositoryFacade.create(entity)).thenReturn(entity);
        when(userMapper.toDto(entity)).thenReturn(expectedDto);

        // Act
        UserDto result = userService.createUser(request);

        // Assert
        assertNotNull(result);
        verify(userRepositoryFacade, times(1)).create(any(UserEntity.class));
    }

    @Test
    void getUserById_ShouldReturnUser_WhenUserExists() {
        // Arrange
        Long userId = 1L;
        UserEntity entity = createUserEntity();
        UserDto expectedDto = createUserDto();

        when(userRepositoryFacade.getOrThrow(userId)).thenReturn(entity);
        when(userMapper.toDto(entity)).thenReturn(expectedDto);

        // Act
        UserDto result = userService.getUserById(userId);

        // Assert - Fixed assertions to match test data
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("john.doe@example.com", result.getEmail()); // Fixed
        assertEquals("John Doe", result.getName()); // Fixed

        verify(userRepositoryFacade, times(1)).getOrThrow(userId);
        verify(userMapper, times(1)).toDto(entity);
    }

    @Test
    void getUserById_ShouldThrowNotFoundException_WhenUserDoesNotExist() {
        // Arrange
        Long userId = 999L;
        when(userRepositoryFacade.getOrThrow(userId))
                .thenThrow(new NotFoundException("User not found"));

        // Act & Assert
        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> userService.getUserById(userId)
        );

        assertEquals("User not found", exception.getMessage());
        verify(userRepositoryFacade, times(1)).getOrThrow(userId);
        verify(userMapper, never()).toDto(any());
    }

    @Test
    void getUserById_ShouldPassCorrectIdToRepository() {
        // Arrange
        Long userId = 123L;
        UserEntity entity = createUserEntity();
        entity.setId(userId);
        UserDto expectedDto = createUserDto();
        expectedDto.setUserId(userId);

        when(userRepositoryFacade.getOrThrow(userId)).thenReturn(entity);
        when(userMapper.toDto(entity)).thenReturn(expectedDto);

        // Act
        UserDto result = userService.getUserById(userId);

        // Assert
        assertEquals(userId, result.getUserId());

        // Verify the repository was called with the correct ID
        verify(userRepositoryFacade, times(1)).getOrThrow(userId);
    }

    @Test
    void getUserById_ShouldReturnMappedDto() {
        // Arrange
        Long userId = 1L;
        UserEntity entity = createUserEntity();
        UserDto expectedDto = UserDto.builder()
                .userId(userId)
                .name("Different Name")
                .email("different@example.com")
                .mobileNumber("+20111111111")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepositoryFacade.getOrThrow(userId)).thenReturn(entity);
        when(userMapper.toDto(entity)).thenReturn(expectedDto);

        // Act
        UserDto result = userService.getUserById(userId);

        // Assert - The result should be exactly what the mapper returns
        assertNotNull(result);
        assertEquals("Different Name", result.getName());
        assertEquals("different@example.com", result.getEmail());
        verify(userMapper, times(1)).toDto(entity);
    }
}