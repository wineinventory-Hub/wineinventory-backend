package com.wineinventory.profilemanagement.interfaces.rest.assembler;

import com.wineinventory.profilemanagement.domain.model.commands.UpdateProfileCommand;
import com.wineinventory.profilemanagement.interfaces.rest.resources.UpdateProfileResource;

public class UpdateProfileCommandFromResourceAssembler {

    private UpdateProfileCommandFromResourceAssembler() {
    }

    public static UpdateProfileCommand toCommand(String currentUsername, UpdateProfileResource resource) {
        return new UpdateProfileCommand(
                currentUsername,
                resource.username(),
                resource.email(),
                resource.password(),
                resource.validationPassword()
        );
    }
}
