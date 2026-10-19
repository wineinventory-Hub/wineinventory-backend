package com.wineinventory.profilemanagement.interfaces.rest.resources;

import com.wineinventory.authorization.domain.model.aggregates.User;
import com.wineinventory.authorization.domain.model.valueobjects.UserRoles;

/**
 * Profile resource returned by the API. It never includes the password.
 */
public record ProfileResource(
        String username,
        String email,
        UserRoles role
) {

    public static ProfileResource from(User user) {
        return new ProfileResource(
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }
}
