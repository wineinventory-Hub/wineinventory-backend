package com.wineinventory.inventorymanagement.interfaces.rest.controllers;

import com.wineinventory.inventorymanagement.domain.model.commands.DeleteWarehouseCommand;
import com.wineinventory.inventorymanagement.domain.model.queries.GetWarehouseByIdQuery;
import com.wineinventory.inventorymanagement.domain.services.WarehouseCommandService;
import com.wineinventory.inventorymanagement.domain.services.WarehouseQueryService;
import com.wineinventory.inventorymanagement.interfaces.rest.assemblers.UpdateWarehouseCommandFromResourceAssembler;
import com.wineinventory.inventorymanagement.interfaces.rest.assemblers.WarehouseResourceFromEntityAssembler;
import com.wineinventory.inventorymanagement.interfaces.rest.resources.UpdateWarehouseResource;
import com.wineinventory.inventorymanagement.interfaces.rest.resources.WarehouseResource;
import com.wineinventory.shared.infrastructure.security.ownership.ResourceOwnershipGuard;
import com.wineinventory.inventorymanagement.domain.model.queries.GetAllWarehousesByAccountIdQuery;
import com.wineinventory.inventorymanagement.domain.model.valueobjects.AccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Warehouses.
 * @summary
 * This class provides REST endpoints for warehouses.
 * @since 1.0.0
 */
@RestController
@RequestMapping(value = "api/v1/warehouses", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Warehouses", description = "Endpoints for managing warehouses.")
public class WarehouseController {

    private final WarehouseCommandService warehouseCommandService;
    private final WarehouseQueryService warehouseQueryService;

    /**
     * Constructor for WarehouseController.
     * @param warehouseCommandService Warehouse command service
     * @param warehouseQueryService Warehouse source query service
     * @since 1.0.0
     * @see WarehouseCommandService
     * @see WarehouseQueryService
     */
    public WarehouseController(WarehouseCommandService warehouseCommandService, WarehouseQueryService warehouseQueryService) {
        this.warehouseCommandService = warehouseCommandService;
        this.warehouseQueryService = warehouseQueryService;
    }

    /**
     * Updates a warehouse by its ID.
     * @param warehouseId ID of the warehouse to update
     * @param updateWarehouseResource The {@link UpdateWarehouseResource} containing the updated details of the warehouse
     * @return ResponseEntity containing the WarehouseResource or a not found response if the warehouse does not exist
     * @see WarehouseResource
     *
     * @since 1.0.0
     */
    @Operation(summary = "Update an existing warehouse",
            description = "Updates the details of an existing warehouse identified by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Warehouse updated successfully"),
            @ApiResponse(responseCode = "400", description = "Warehouse could not be updated - invalid warehouse ID or resource")
    })
    @PutMapping(path = "/{warehouseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ownership.canAccessWarehouse(authentication, #warehouseId)")
    public ResponseEntity<WarehouseResource> updateWarehouse(@PathVariable Long warehouseId, @ModelAttribute UpdateWarehouseResource updateWarehouseResource) {
        var updateWarehouseCommand = UpdateWarehouseCommandFromResourceAssembler.toCommandFromResource(warehouseId, updateWarehouseResource);
        var updatedWarehouse = warehouseCommandService.handle(updateWarehouseCommand);
        if (updatedWarehouse.isEmpty()) return ResponseEntity.badRequest().build();
        var updatedWarehouseEntity = updatedWarehouse.get();
        var updatedWarehouseResource = WarehouseResourceFromEntityAssembler.toResourceFromEntity(updatedWarehouseEntity);
        return ResponseEntity.ok(updatedWarehouseResource);
    }

    /**
     * Get a warehouse by its ID.
     * @param warehouseId ID of the warehouse to retrieve
     * @return ResponseEntity containing the WarehouseResource or a bad request if the warehouse does not exist
     * @see WarehouseResource
     *
     * @since 1.0.0
     */
    @Operation(summary = "Get a warehouse by ID",
            description = "Retrieves the details of a warehouse identified by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Warehouse found successfully"),
            @ApiResponse(responseCode = "404", description = "Warehouse not found - invalid warehouse ID")
    })
    @GetMapping("/{warehouseId}")
    @PreAuthorize("@ownership.canAccessWarehouse(authentication, #warehouseId)")
    public ResponseEntity<WarehouseResource> getWarehouseById(@PathVariable Long warehouseId) {

        var getWarehouseById = new GetWarehouseByIdQuery(warehouseId);
        var warehouse = warehouseQueryService.handle(getWarehouseById);
        if (warehouse.isEmpty()) return ResponseEntity.notFound().build();
        var warehouseEntity = warehouse.get();
        var warehouseResource = WarehouseResourceFromEntityAssembler.toResourceFromEntity(warehouseEntity);
        return ResponseEntity.ok(warehouseResource);
    }

    /**
     * Get all warehouses associated with a specific account ID.
     * @return ResponseEntity containing a list of WarehouseResources
     * @see WarehouseResource
     *
     * @since 1.0.0
     */
    @Operation(summary = "Get all warehouses by account ID",
            description = "Retrieves all warehouses of the authenticated account.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Warehouses retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid profile ID")
    })
    @GetMapping
    public ResponseEntity<List<WarehouseResource>> getAllWarehouses(Authentication authentication)
    {
        var accountId = ResourceOwnershipGuard.currentAccountId(authentication).orElseThrow();
        var getAllWarehousesByAccountIdQuery = new GetAllWarehousesByAccountIdQuery(new AccountId(accountId));
        var warehouses = warehouseQueryService.handle(getAllWarehousesByAccountIdQuery);
        var warehouseResources = warehouses.stream().map(WarehouseResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(warehouseResources);
    }

    /**
     * Delete a warehouse by its ID.
     * @param warehouseId ID of the warehouse to delete
     * @return ResponseEntity indicating the result of the deletion operation
     * @since 1.0.0
     */
    @Operation(summary = "Delete a warehouse by ID",
            description = "Deletes a warehouse identified by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Warehouse deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid warehouse ID")
    })
    @DeleteMapping("/{warehouseId}")
    @PreAuthorize("@ownership.canAccessWarehouse(authentication, #warehouseId)")
    public ResponseEntity<Map<String, String>> deleteWarehouse(@PathVariable Long warehouseId) {
        var deleteWarehouseCommand = new DeleteWarehouseCommand(warehouseId);
        warehouseCommandService.handle(deleteWarehouseCommand);
        return ResponseEntity.ok(Map.of("message", "Warehouse with given Id deleted successfully."));
    }

}