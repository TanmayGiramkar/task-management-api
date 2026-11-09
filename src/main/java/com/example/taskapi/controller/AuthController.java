package com.example.taskapi.controller;

import com.example.taskapi.config.OpenApiExamples;
import com.example.taskapi.dto.LoginRequest;
import com.example.taskapi.dto.RefreshTokenRequest;
import com.example.taskapi.dto.RegisterRequest;
import com.example.taskapi.dto.TokenResponse;
import com.example.taskapi.dto.UserResponse;
import com.example.taskapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Create an account and get tokens")
@SecurityRequirements // Public endpoints: no access token needed
@ApiResponse(responseCode = "500", description = "Unexpected server error. Details are logged, never returned",
        content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = OpenApiExamples.INTERNAL_ERROR)))
public class AuthController {

    private static final String TOO_MANY_REQUESTS_DESCRIPTION =
            "Too many requests from this client. Limits per IP: login 5/min, register 3/min, refresh 10/min";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account",
            description = "The email is stored lower-cased. The password is stored only as a BCrypt hash.")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed, or the request body is malformed",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.REGISTER_VALIDATION_FAILED)))
    @ApiResponse(responseCode = "409", description = "An account with this email already exists",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.EMAIL_TAKEN)))
    @ApiResponse(responseCode = "429", description = TOO_MANY_REQUESTS_DESCRIPTION,
            headers = @Header(name = "Retry-After", description = "Seconds to wait", schema = @Schema(type = "integer")),
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.TOO_MANY_REQUESTS)))
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in",
            description = """
                    Returns an access token (JWT, 15 minutes) to send as `Authorization: Bearer <token>`, \
                    and a refresh token (7 days) for getting new tokens without logging in again.""")
    @ApiResponse(responseCode = "200", description = "Logged in")
    @ApiResponse(responseCode = "400", description = "Email or password missing, or the request body is malformed",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.MALFORMED_BODY)))
    @ApiResponse(responseCode = "401", description = "Wrong email or password (the response does not say which)",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.INVALID_CREDENTIALS)))
    @ApiResponse(responseCode = "429", description = TOO_MANY_REQUESTS_DESCRIPTION,
            headers = @Header(name = "Retry-After", description = "Seconds to wait", schema = @Schema(type = "integer")),
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.TOO_MANY_REQUESTS)))
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Get new tokens",
            description = """
                    Exchanges a refresh token for a new access token and a new refresh token. \
                    The refresh token sent is revoked: each one works once. Sending an already-used \
                    refresh token is treated as theft and logs out every session from that login.""")
    @ApiResponse(responseCode = "200", description = "New tokens")
    @ApiResponse(responseCode = "400", description = "Refresh token missing, or the request body is malformed",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.MALFORMED_BODY)))
    @ApiResponse(responseCode = "401", description = "Refresh token unknown, expired or revoked",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.INVALID_REFRESH_TOKEN)))
    @ApiResponse(responseCode = "429", description = TOO_MANY_REQUESTS_DESCRIPTION,
            headers = @Header(name = "Retry-After", description = "Seconds to wait", schema = @Schema(type = "integer")),
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.TOO_MANY_REQUESTS)))
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Log out",
            description = """
                    Revokes the refresh token. Always returns 204, even for an unknown token. \
                    The access token keeps working until it expires (at most 15 minutes), so the client \
                    should discard it.""")
    @ApiResponse(responseCode = "204", description = "Logged out. No response body")
    @ApiResponse(responseCode = "400", description = "Refresh token missing, or the request body is malformed",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.MALFORMED_BODY)))
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }
}
