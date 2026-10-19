package com.wineinventory.authorization.domain.model.commands;

import com.wineinventory.authorization.domain.model.valueobjects.UserRoles;

/**
 * Sign up command.
 * The validation password is only used to confirm the password, it is never stored.
 */
public record SignUpCommand(String username, String email, String password, String validationPassword, UserRoles role) {
    public SignUpCommand {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password is required");
        if (role == null) throw new IllegalArgumentException("Role is required");
    }
}
