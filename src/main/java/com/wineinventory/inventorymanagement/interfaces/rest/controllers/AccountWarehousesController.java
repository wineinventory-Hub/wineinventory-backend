package com.wineinventory.inventorymanagement.interfaces.rest.controllers;

import com.wineinventory.inventorymanagement.domain.model.aggregates.Warehouse;
import com.wineinventory.inventorymanagement.domain.model.queries.GetAllWarehousesByAccountIdQuery;
import com.wineinventory.inventorymanagement.domain.model.valueobjects.AccountId;
import com.wineinventory.inventorymanagement.domain.services.WarehouseCommandService;
import com.wineinventory.inventorymanagement.domain.services.WarehouseQueryService;
import com.wineinventory.inventorymanagement.interfaces.rest.assemblers.CreateWarehouseCommandFromResourceAssembler;
import com.wineinventory.inventorymanagement.interfaces.rest.assemblers.WarehouseResourceFromEntityAssembler;
import com.wineinventory.inventorymanagement.interfaces.rest.resources.CreateWarehouseResource;
import com.wineinventory.inventorymanagement.interfaces.rest.resources.WarehouseResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.CREATED;

/**
 * REST controller for Account Warehouses.
 * @summary
 * This class provides REST endpoints for account warehouses.
 * @since 1.0.0
 */
@RestController
@RequestMapping(value = "api/v1/accounts/{accountId}/warehouses", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Accounts")
public class AccountWarehousesController {

    private final WarehouseQueryService warehouseQueryService;
    private final WarehouseCommandService warehouseCommandService;

    public AccountWarehousesController(WarehouseQueryService warehouseQueryService,
                                       WarehouseCommandService warehouseCommandService) {
        this.warehouseQueryService = warehouseQueryService;
        this.warehouseCommandService = warehouseCommandService;
    }

    /**
     * Create a new warehouse.
     * @param resource CreateWarehouseResource containing the details of the warehouse to be created
     * @return ResponseEntity containing the created WarehouseResource or a bad request if the resource is invalid
     * @see CreateWarehouseResource
     * @see WarehouseResource
     *
     * @since 1.0.0
     */
    @Operation(
            summary = "Create a new warehouse",
            description = "Creates a new warehouse with the provided details."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Warehouse created successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ownership.isAccountOwner(authentication, #accountId)")
    public ResponseEntity<WarehouseResource> createWarehouse(@ModelAttribute CreateWarehouseResource resource, @PathVariable Long accountId) {
        Optional<Warehouse> warehouse = warehouseCommandService.handle(CreateWarehouseCommandFromResourceAssembler.toCommandFromResource(resource, accountId));

        return warehouse.map(source ->
                        new ResponseEntity<>(WarehouseResourceFromEntityAssembler.toResourceFromEntity(source), CREATED))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @GetMapping
    @Operation(summary = "Get all warehouses by account ID",
            description = "Retrieves all warehouses associated with a specific account ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Warehouses retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Not found - invalid account ID")
    })
    @PreAuthorize("@ownership.isAccountOwner(authentication, #accountId)")
    public ResponseEntity<List<WarehouseResource>> getAllWarehousesByAccountId(@PathVariable Long accountId) {

        var targetAccountId = new AccountId(accountId);
        var getAllWarehousesByAccountIdQuery = new GetAllWarehousesByAccountIdQuery(targetAccountId);
        var warehouses = warehouseQueryService.handle(getAllWarehousesByAccountIdQuery);
        if (warehouses.isEmpty()) return ResponseEntity.notFound().build();
        var warehousesEntities = warehouses.stream().map(WarehouseResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(warehousesEntities);
    }
}