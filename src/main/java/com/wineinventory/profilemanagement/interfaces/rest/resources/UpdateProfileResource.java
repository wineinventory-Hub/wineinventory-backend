package com.wineinventory.profilemanagement.interfaces.rest.resources;

/**
 * Profile update request. Every field is optional.
 * The role is not part of the request, so a user cannot change its own role.
 */
public record UpdateProfileResource(
        String username,
        String email,
        String password,
        String validationPassword
) {
}
