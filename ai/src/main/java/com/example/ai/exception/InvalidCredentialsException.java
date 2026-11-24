package com.example.ai.exception;

/** Wrong username or password at login; mapped to 401 by {@link GlobalExceptionHandler}. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
