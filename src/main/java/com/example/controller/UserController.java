package com.example.controller;

import com.example.model.dto.user.CreateUserRequest;
import com.example.model.entity.UserEntity;
import com.example.model.mapper.UserMapper;
import com.example.service.UserService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/api")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Endpoints for managing users")
public class UserController {
    private final UserService userService;
    @Operation(summary = "Create a new user", description = "Adds a user to the system")
    @Post("/user")
    public HttpResponse<?> createUser(@Body @Valid CreateUserRequest userRequest){
        return HttpResponse.created(userService.createUser(userRequest));
    }
    @Operation(summary = "Get user by ID", description = "Fetch user details by ID")
    @Get("/users/{id}")
    public HttpResponse<?> getUser(Long id){
        return HttpResponse.ok(userService.getUserById(id));
    }
}
