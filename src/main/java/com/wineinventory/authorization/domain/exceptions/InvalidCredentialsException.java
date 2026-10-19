package com.wineinventory.authorization.domain.exceptions;

/**
 * Thrown when the username or the password of a sign-in request are not valid.
 * The message is the same in both cases, so the API does not reveal which usernames exist.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
