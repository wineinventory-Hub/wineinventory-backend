package com.wineinventory.authorization.domain.exceptions;

/**
 * Thrown when a user tries to sign up with a username that is already registered.
 */
public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException(String username) {
        super("Username " + username + " already exists");
    }
}
