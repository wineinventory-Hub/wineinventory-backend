package com.wineinventory.reportingandcareguide.interfaces.rest.controllers;

import com.wineinventory.inventorymanagement.application.internal.outboundservices.FileStorageService;
import com.wineinventory.inventorymanagement.domain.services.WarehouseCommandService;
import com.wineinventory.inventorymanagement.domain.services.WarehouseQueryService;
import com.wineinventory.reportingandcareguide.domain.model.commands.CreateCareGuideCommand;
import com.wineinventory.reportingandcareguide.domain.model.commands.DeleteCareGuideCommand;
import com.wineinventory.reportingandcareguide.domain.model.commands.UpdateCareGuideCommand;
import com.wineinventory.reportingandcareguide.domain.model.commands.CreateCareGuideWithoutProductCommand;

import com.wineinventory.reportingandcareguide.domain.model.queries.GetCareGuideByIdQuery;
import com.wineinventory.reportingandcareguide.domain.services.CareGuideCommandService;
import com.wineinventory.reportingandcareguide.domain.services.CareGuideQueryService;
import com.wineinventory.reportingandcareguide.interfaces.rest.assemblers.CareGuideResourceFromEntityAssembler;
import com.wineinventory.reportingandcareguide.interfaces.rest.resources.CareGuideResource;
import com.wineinventory.reportingandcareguide.interfaces.rest.resources.UpdateCareGuideResource;
import com.wineinventory.shared.infrastructure.security.ownership.ResourceOwnershipGuard;
import com.wineinventory.reportingandcareguide.domain.model.queries.GetAllCareGuidesByAccountIdQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * CareGuideController
 */
@RestController
@RequestMapping(value = "/api/v1/careguides", produces = APPLICATION_JSON_VALUE)
@Tag(name = "CareGuides",description = "Available Care Guide Endpoints.")
public class CareGuideController {
    private final WarehouseCommandService warehouseCommandService;
    private final WarehouseQueryService warehouseQueryService;
    private final FileStorageService fileStorageService;
    private final CareGuideCommandService careGuideCommandService;
    private final CareGuideQueryService careGuideQueryService;
    @Autowired
    public CareGuideController(WarehouseCommandService warehouseCommandService,
                               WarehouseQueryService warehouseQueryService,
                               FileStorageService fileStorageService,
                               CareGuideCommandService careGuideCommandService, CareGuideQueryService careGuideQueryService) {
        this.warehouseCommandService = warehouseCommandService;
        this.warehouseQueryService = warehouseQueryService;
        this.fileStorageService = fileStorageService;
        this.careGuideCommandService = careGuideCommandService;
        this.careGuideQueryService = careGuideQueryService;
    }

    @GetMapping
    @Operation(summary = "Get all care guides", description = "Retrieves all care guides of the authenticated account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Care guides retrieved successfully"),
            @ApiResponse(responseCode = "204", description = "No care guides available")
    })
    public ResponseEntity<List<CareGuideResource>> getAllCareGuides(Authentication authentication) {
        var accountId = ResourceOwnershipGuard.currentAccountId(authentication).orElseThrow();
        var careGuides = careGuideQueryService.handle(new GetAllCareGuidesByAccountIdQuery(accountId.toString()));

        if (careGuides == null || careGuides.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        var resources = careGuides.stream()
                .map(CareGuideResourceFromEntityAssembler::toResourceFromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(resources);
    }

    @PostMapping(value = "/{careGuideId}/careguide", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add a care guide with file upload")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Care guide added successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "Care guide not found"),
            @ApiResponse(responseCode = "500", description = "Error processing the request")
    })
    @PreAuthorize("hasRole('PRODUCER') and @ownership.isAccountOwner(authentication, #accountId) and @ownership.canAccessProduct(authentication, #productId)")
    public ResponseEntity<CareGuideResource> addCareGuide(
            @PathVariable Long careGuideId,
            @RequestParam("guideName") String guideName,
            @RequestParam("type") String type,
            @RequestParam("description") String description,
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {

        try {
            if (guideName == null || guideName.trim().isEmpty() ||
                    type == null || type.trim().isEmpty() ||
                    description == null || description.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            if (accountId == null || accountId.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            String imageUrl = null;
            if (imageFile != null && !imageFile.isEmpty()) {
                try {
                    // Upload image to Cloudinary
                    imageUrl = fileStorageService.UploadImage(imageFile);
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new CareGuideResource(
                                    careGuideId,
                                    guideName,
                                    type,
                                    "Error uploading image: " + e.getMessage(),
                                    ""
                            ));
                }
            }

            var normalizedImageUrl = imageUrl != null ? imageUrl : "";

            var careGuide = (productId != null && productId > 0)
                    ? careGuideCommandService.handle(new CreateCareGuideCommand(
                    guideName,
                    type,
                    description,
                    normalizedImageUrl,
                    accountId,
                    productId
            ))
                    : careGuideCommandService.handle(new CreateCareGuideWithoutProductCommand(
                    guideName,
                    type,
                    description,
                    normalizedImageUrl,
                    accountId
            ));

            if (careGuide.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new CareGuideResource(
                                careGuideId,
                                guideName,
                                type,
                                "Error creating care guide in the database",
                                ""
                        ));
            }

            // 5. Return the created resource
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(CareGuideResourceFromEntityAssembler.toResourceFromEntity(careGuide.get()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new CareGuideResource(
                            careGuideId,
                            guideName,
                            type,
                            "Error processing request: " + e.getMessage(),
                            null
                    ));
        }
    }
    @Operation(summary = "Get Care Guide by ID",
            description = "Retrieves a care guide by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Care Guide found"),
            @ApiResponse(responseCode = "404", description = "Care Guide not found")
    })
    @GetMapping("/{careGuideId}")
    @PreAuthorize("@ownership.canAccessCareGuide(authentication, #careGuideId)")
    public ResponseEntity<CareGuideResource> getCareGuideById(
            @Parameter(description = "ID of the care guide to be obtained")
            @PathVariable Long careGuideId) {

        var query = new GetCareGuideByIdQuery(careGuideId);
        var careGuide = careGuideQueryService.handle(query);

        if (careGuide == null || careGuide.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(CareGuideResourceFromEntityAssembler.toResourceFromEntity(careGuide.get(0)));
    }

    @Operation(summary = "Update Care Guide",
            description = "Update Recommendations for A Specific Care Guide")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Care Guide updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PutMapping("/{careGuideId}")
    @PreAuthorize("hasRole('PRODUCER') and @ownership.canAccessCareGuide(authentication, #careGuideId)")
    public ResponseEntity<CareGuideResource> updateCareGuide(
            @Parameter(description = "ID of the care guide to be updated")
            @PathVariable Long careGuideId,
            @Valid @RequestBody UpdateCareGuideResource resource) {

        var command = new UpdateCareGuideCommand(
                careGuideId,
                resource.guideName(),
                resource.type(),
                resource.description(),
                resource.imageUrl()
        );

        var updatedCareGuide = careGuideCommandService.handle(command);

        if (updatedCareGuide.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(CareGuideResourceFromEntityAssembler.toResourceFromEntity(updatedCareGuide.get()));
    }

    @Operation(summary = "Delete a Care Guide",
            description = "Delete a Specific Care Guide")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Care Guide deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Care Guide not found")
    })
    @DeleteMapping("/{careGuideId}")
    @PreAuthorize("hasRole('PRODUCER') and @ownership.canAccessCareGuide(authentication, #careGuideId)")
    public ResponseEntity<Void> deleteCareGuide(
            @Parameter(description = "ID of the care guide to be deleted")
            @PathVariable Long careGuideId) {

        try {
            careGuideCommandService.handle(new DeleteCareGuideCommand(careGuideId));
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Unassign a Care Guide",
            description = "Unassign a Care Guide From Its Current Product")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Care Guide unassigned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid operation")
    })
    @PutMapping("/{careGuideId}/deallocations")
    @PreAuthorize("hasRole('PRODUCER') and @ownership.canAccessCareGuide(authentication, #careGuideId)")
    public ResponseEntity<Map<String, String>> deallocateCareGuide(
            @Parameter(description = "ID of the care guide to be unassigned")
            @PathVariable Long careGuideId) {

        return ResponseEntity.ok(Map.of(
                "message",
                String.format("Care Guide with ID %d unassigned from its current product successfully", careGuideId)
        ));
    }

    @Operation(summary = "Assign a Care Guide",
            description = "Assign a Care Guide To a Specific Product")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Care Guide assigned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PutMapping("/{careGuideId}/allocations/{productId}")
    @PreAuthorize("hasRole('PRODUCER') and @ownership.canAccessCareGuide(authentication, #careGuideId) and @ownership.canAccessProduct(authentication, #productId)")
    public ResponseEntity<Map<String, String>> allocateCareGuideToProduct(
            @Parameter(description = "ID of the care guide to be assigned")
            @PathVariable Long careGuideId,
            @Parameter(description = "ID of the product to assign the care guide to")
            @PathVariable Long productId) {

        return ResponseEntity.ok(Map.of(
                "message",
                String.format("Care Guide with ID %d assigned to product with ID %d successfully", careGuideId, productId)
        ));
    }

}