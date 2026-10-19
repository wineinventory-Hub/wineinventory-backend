package com.wineinventory.profilemanagement.domain.model.commands;

public record UpdateProfileCommand(
        String currentUsername,
        String username,
        String email,
        String password,
        String validationPassword
) {
}
