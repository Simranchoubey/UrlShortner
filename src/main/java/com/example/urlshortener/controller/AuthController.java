package com.example.urlshortener.controller;

import com.example.urlshortener.dto.LoginRequest;
import com.example.urlshortener.dto.LoginResponse;
import com.example.urlshortener.dto.RegisterRequest;
import com.example.urlshortener.dto.RegisterResponse;
import com.example.urlshortener.exception.ApiError;
import com.example.urlshortener.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints (§1, §5 of the plan).
 *
 * <p>Thin controller: validates the request body and delegates to
 * {@link AuthService}. No JWT/authentication logic lives here.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Registers a new account → {@code 201} with id + email (never the hash). */
    @Operation(summary = "Register a new account",
            description = "Public. Creates an account and returns the new user's id and email. The password "
                    + "is BCrypt-hashed and never exposed. 400 on invalid input, 409 if the email is already "
                    + "registered.")
    @PostMapping("/register")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created",
                    content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid email/password",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    /** Logs in → {@code 200} with a JWT access token. */
    @Operation(summary = "Log in and receive a JWT",
            description = "Public. Validates email/password and returns a short-lived JWT access token. "
                    + "Send it back as 'Authorization: Bearer <token>' on protected /api/v1/urls endpoints. "
                    + "401 on unknown email or wrong password; 429 if the per-IP login limit for the window "
                    + "is exceeded.")
    @PostMapping("/login")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged in, JWT issued",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Unknown email or wrong password (generic)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}