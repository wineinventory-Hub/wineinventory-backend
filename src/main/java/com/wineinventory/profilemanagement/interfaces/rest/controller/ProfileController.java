package com.wineinventory.profilemanagement.interfaces.rest.controller;

import com.wineinventory.profilemanagement.domain.services.ProfileCommandService;
import com.wineinventory.profilemanagement.domain.services.ProfileQueryService;
import com.wineinventory.profilemanagement.interfaces.rest.assembler.UpdateProfileCommandFromResourceAssembler;
import com.wineinventory.profilemanagement.interfaces.rest.resources.ProfileResource;
import com.wineinventory.profilemanagement.interfaces.rest.resources.UpdateProfileResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/profile", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Profile", description = "Endpoints for profiles")
public class ProfileController {

    private final ProfileQueryService profileQueryService;
    private final ProfileCommandService profileCommandService;

    public ProfileController(ProfileQueryService profileQueryService, ProfileCommandService profileCommandService) {
        this.profileQueryService = profileQueryService;
        this.profileCommandService = profileCommandService;
    }

    @GetMapping
    public ResponseEntity<ProfileResource> getProfile(Authentication authentication) {
        var profile = profileQueryService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Profile not found"));
        return ResponseEntity.ok(ProfileResource.from(profile));
    }

    @PutMapping(consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfileResource> updateProfile(Authentication authentication, @RequestBody UpdateProfileResource resource) {
        var command = UpdateProfileCommandFromResourceAssembler.toCommand(authentication.getName(), resource);
        var updatedProfile = profileCommandService.handle(command);
        return ResponseEntity.ok(ProfileResource.from(updatedProfile));
    }
}
