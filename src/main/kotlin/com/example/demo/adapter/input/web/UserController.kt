package com.example.demo.adapter.input.web

import com.example.demo.adapter.input.web.dto.CreateUserRequest
import com.example.demo.adapter.input.web.dto.UserResponse
import com.example.demo.application.port.input.UserUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for User operations (Inbound Adapter).
 * This adapter converts HTTP requests to use case calls.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "User", description = "User management API")
class UserController(
    private val userUseCase: UserUseCase
) {

    @PostMapping
    @Operation(summary = "Create a new user", description = "Creates a new user with the provided information")
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<UserResponse> {
        val user = userUseCase.createUser(request.name, request.email)
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user))
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves a user by their ID")
    fun getUserById(@PathVariable id: Long): ResponseEntity<UserResponse> {
        val user = userUseCase.getUserById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(UserResponse.from(user))
    }

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieves all users")
    fun getAllUsers(): ResponseEntity<List<UserResponse>> {
        val users = userUseCase.getAllUsers().map { UserResponse.from(it) }
        return ResponseEntity.ok(users)
    }
}
