package com.example.model.dto.user;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;

@Serdeable
@Data
@Builder
public class UserDto {
    private Long userId;
    private String name;
    private String email;
    private String mobileNumber;
    private AddressDto address;
    private LocalDateTime createdAt;
}
