package com.wineinventory.reportingandcareguide.interfaces.rest.controllers;

import com.wineinventory.inventorymanagement.application.internal.outboundservices.FileStorageService;
import com.wineinventory.reportingandcareguide.domain.model.commands.CreateCareGuideCommand;
import com.wineinventory.reportingandcareguide.domain.model.commands.CreateCareGuideWithoutProductCommand;
import com.wineinventory.reportingandcareguide.domain.model.queries.GetAllCareGuidesByAccountIdQuery;
import com.wineinventory.reportingandcareguide.domain.model.queries.GetCareGuideByIdQuery;
import com.wineinventory.reportingandcareguide.domain.services.CareGuideCommandService;
import com.wineinventory.reportingandcareguide.domain.services.CareGuideQueryService;
import com.wineinventory.reportingandcareguide.interfaces.rest.assemblers.CareGuideResourceFromEntityAssembler;
import com.wineinventory.reportingandcareguide.interfaces.rest.resources.CareGuideResource;
import com.wineinventory.reportingandcareguide.interfaces.rest.resources.CreateCareGuideResource;
import com.wineinventory.reportingandcareguide.interfaces.rest.resources.CreateCareGuideWithoutProductResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.security.access.prepost.PreAuthorize;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/care-guides")
@Tag(name = "Accounts", description = "Account Management")
public class AccountCareGuideController {

    private final CareGuideCommandService careGuideCommandService;
    private final CareGuideQueryService careGuideQueryService;
    private final FileStorageService fileStorageService;
    public AccountCareGuideController(
            CareGuideCommandService careGuideCommandService,
            CareGuideQueryService careGuideQueryService,
            FileStorageService fileStorageService) {
        this.careGuideCommandService = careGuideCommandService;
        this.careGuideQueryService = careGuideQueryService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{careGuideId}")
    @Operation(
            summary = "Get Care Guide by ID",
            description = "Retrieves a care guide by its unique identifier",
            operationId = "GetCareGuideById"
    )
    @ApiResponse(responseCode = "200", description = "Care Guide found")
    @ApiResponse(responseCode = "404", description = "Care Guide not found")
    @PreAuthorize("@ownership.isAccountOwner(authentication, #accountId) and @ownership.canAccessCareGuide(authentication, #careGuideId)")
    public ResponseEntity<CareGuideResource> getCareGuideById(
            @Parameter(description = "Account ID") @PathVariable String accountId,
            @Parameter(description = "Care Guide ID") @PathVariable Long careGuideId) {

        var query = new GetCareGuideByIdQuery(careGuideId);
        var careGuides = careGuideQueryService.handle(query);

        if (careGuides == null || careGuides.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                CareGuideResourceFromEntityAssembler.toResourceFromEntity(careGuides.get(0))
        );
    }

    @GetMapping
    @Operation(
            summary = "Get All Care Guides by Account ID",
            description = "Retrieves a list of care guides by a specific Account ID",
            operationId = "GetAllCareGuidesByAccountId"
    )
    @ApiResponse(responseCode = "200", description = "Care Guides found")
    @ApiResponse(responseCode = "404", description = "No Care Guides found for the given Account ID")
    @PreAuthorize("@ownership.isAccountOwner(authentication, #accountId)")
    public ResponseEntity<List<CareGuideResource>> getAllCareGuidesByAccountId(
            @Parameter(description = "Account ID") @PathVariable String accountId) {

        var query = new GetAllCareGuidesByAccountIdQuery(accountId);
        var careGuides = careGuideQueryService.handle(query);

        if (careGuides == null || careGuides.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var resources = careGuides.stream()
                .map(CareGuideResourceFromEntityAssembler::toResourceFromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(resources);
    }

    @PostMapping
    @Operation(
            summary = "Create a Care Guide Without a Product",
            description = "Create a Care Guide without assigning it to a specific product",
            operationId = "CreateCareGuideWithoutProduct"
    )
    @ApiResponse(responseCode = "201", description = "Care Guide created")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @PreAuthorize("hasRole('PRODUCER') and @ownership.isAccountOwner(authentication, #accountId)")
    public ResponseEntity<CareGuideResource> createCareGuideWithoutProduct(
            @Parameter(description = "Account ID") @PathVariable String accountId,
            @Valid @RequestBody CreateCareGuideWithoutProductResource resource) {

        var command = new CreateCareGuideWithoutProductCommand(
                resource.guideName(),
                resource.type(),
                resource.description(),
                resource.imageUrl(),
                accountId
        );

        var createdCareGuide = careGuideCommandService.handle(command);

        if (createdCareGuide.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        var resourceResponse = CareGuideResourceFromEntityAssembler
                .toResourceFromEntity(createdCareGuide.get());

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(resourceResponse.id())
                .toUri();

        return ResponseEntity.created(location).body(resourceResponse);
    }

    @PostMapping("/products/{productId}")
    @Operation(
            summary = "Create a Care Guide",
            description = "Create a Care Guide And Assign it to a specific product",
            operationId = "CreateCareGuide"
    )
    @ApiResponse(responseCode = "201", description = "Care Guide created and assigned")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @PreAuthorize("hasRole('PRODUCER') and @ownership.isAccountOwner(authentication, #accountId) and @ownership.canAccessProduct(authentication, #productId)")
    public ResponseEntity<CareGuideResource> createCareGuide(
            @Parameter(description = "Account ID") @PathVariable String accountId,
            @Parameter(description = "Product ID") @PathVariable Long productId,
            @Valid @RequestBody CreateCareGuideResource resource) {

        String imageUrl = null;
        if (resource.image() != null && !resource.image().isEmpty()) {
            try {
                imageUrl = fileStorageService.UploadImage(resource.image());
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new CareGuideResource(
                                null,
                                resource.guideName(),
                                resource.type(),
                                "Error uploading image: " + e.getMessage(),
                                null
                        ));
            }
        }

        var command = new CreateCareGuideCommand(
                resource.guideName(),
                resource.type(),
                resource.description(),
                imageUrl,
                accountId,
                productId
        );

        var createdCareGuide = careGuideCommandService.handle(command);

        if (createdCareGuide.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        var resourceResponse = CareGuideResourceFromEntityAssembler
                .toResourceFromEntity(createdCareGuide.get());

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(resourceResponse.id())
                .toUri();

        return ResponseEntity.created(location).body(resourceResponse);
    }
}