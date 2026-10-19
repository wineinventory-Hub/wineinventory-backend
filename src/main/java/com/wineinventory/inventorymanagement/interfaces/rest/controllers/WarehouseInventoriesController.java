package com.wineinventory.inventorymanagement.interfaces.rest.controllers;

import com.wineinventory.inventorymanagement.domain.model.aggregates.Inventory;
import com.wineinventory.inventorymanagement.domain.model.commands.DeleteProductFromWarehouseCommand;
import com.wineinventory.inventorymanagement.domain.model.queries.GetAllProductsByWarehouseIdQuery;
import com.wineinventory.inventorymanagement.domain.model.queries.GetInventoryByProductIdAndWarehouseIdAndBestBeforeDateQuery;
import com.wineinventory.inventorymanagement.domain.services.InventoryCommandService;
import com.wineinventory.inventorymanagement.domain.services.InventoryQueryService;
import com.wineinventory.inventorymanagement.interfaces.rest.assemblers.*;
import com.wineinventory.inventorymanagement.interfaces.rest.resources.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.CREATED;

/**
 * REST controller for Warehouse Inventories.
 * @summary
 * This class provides REST endpoints for warehouse inventories.
 * @since 1.0.0
 */
@RestController
@RequestMapping(value = "api/v1/warehouses/{warehouseId}/inventories", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Warehouses")
public class WarehouseInventoriesController {

    private final InventoryQueryService inventoryQueryService;
    private final InventoryCommandService inventoryCommandService;

    public WarehouseInventoriesController(InventoryQueryService inventoryQueryService, InventoryCommandService inventoryCommandService) {
        this.inventoryCommandService = inventoryCommandService;
        this.inventoryQueryService = inventoryQueryService;
    }

    /**
     * This method retrieves all products associated with a specific warehouse ID.
     * @param warehouseId - The ID of the warehouse for which products will be retrieved.
     * @return - ResponseEntity containing a list of ProductInventoryResource objects if products are found, or a not found response if no products are associated with the warehouse ID.
     */
    @GetMapping
    @Operation(summary = "Get all products by warehouse ID",
            description = "Retrieves all products associated with a specific warehouse ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Products retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Not Found - invalid warehouse ID")
    })
    @PreAuthorize("@ownership.canAccessWarehouse(authentication, #warehouseId)")
    public ResponseEntity<List<ProductInventoryResource>> getAllProductsByWarehouseId(@PathVariable Long warehouseId) {

        var getAllProductsByWarehouseIdQuery = new GetAllProductsByWarehouseIdQuery(warehouseId);
        var products = inventoryQueryService.handle(getAllProductsByWarehouseIdQuery);
        if (products.isEmpty()) return ResponseEntity.notFound().build();
        var productResources = products.stream()
                .map(ProductInventoryResourceFromEntityAssembler::toResourceFromEntity)
                .toList();

        return ResponseEntity.ok(productResources);
    }

    /**
     * This method retrieves an inventory by product ID and warehouse ID and best before date.
     * @param warehouseId - The ID of the warehouse from which the inventory will be retrieved.
     * @param expirationDate - The best before date of the product in the inventory.
     * @param productId - The ID of the product for which the inventory will be retrieved.
     * @return - ResponseEntity containing the InventoryResource if found, or a not found response if the inventory does not exist.
     */
    @GetMapping("products/{productId}/expiration-date/{expirationDate:date}")
    @Operation(summary = "Get a inventory by product and warehouse ID and best before date",
            description = "Retrieves the details of an inventory identified by its product and warehouse ID and best before date.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Inventory found successfully"),
            @ApiResponse(responseCode = "404", description = "Inventory not found - invalid product or warehouse ID")
    })
    @PreAuthorize("@ownership.canAccessWarehouse(authentication, #warehouseId) and @ownership.canAccessProduct(authentication, #productId)")
    public ResponseEntity<InventoryResource> getInventoryByProductIdAndWarehouseIdAndBestBeforeDate(
            @PathVariable Long warehouseId,
            @PathVariable LocalDate expirationDate,
            @PathVariable Long productId
    ) {
        var getInventoryByProductIdAndWarehouseIdAndBestBeforeDateQuery = new GetInventoryByProductIdAndWarehouseIdAndBestBeforeDateQuery(productId, warehouseId, expirationDate);
        var inventory = inventoryQueryService.handle(getInventoryByProductIdAndWarehouseIdAndBestBeforeDateQuery);
        if (inventory.isEmpty()) return ResponseEntity.notFound().build();
        var inventoryEntity = inventory.get();
        var inventoryResource = InventoryResourceFromEntityAssembler.toResourceFromEntity(inventoryEntity);
        return ResponseEntity.ok(inventoryResource);
    }

    /**
     * This method moves products from one warehouse to another by their product ID, source warehouse ID, and destination warehouse ID.
     * @param resource - The resource containing the details of the products to be moved.
     * @param productId - The ID of the product to be moved.
     * @param warehouseId - The ID of the source warehouse from which the products will be moved.
     * @return - ResponseEntity containing the updated InventoryResource or a bad request response if the stock could not be moved.
     */
    @PutMapping("products/{productId}/moves")
    @Operation(summary = "Move products to another warehouse",
            description = "Moves products from one warehouse to another by their product ID, source warehouse ID, and destination warehouse ID. Then creates a new inventory or updates the existing one.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Stock moved successfully!"),
            @ApiResponse(responseCode = "400", description = "Stock could not be moved - invalid product or warehouse ID or resource")
    })
    @PreAuthorize("@ownership.canAccessWarehouse(authentication, #warehouseId) and @ownership.canAccessProduct(authentication, #productId) and @ownership.canAccessWarehouse(authentication, #resource.newWarehouseId())")
    public ResponseEntity<InventoryResource> moveProductsToAnotherWarehouse(
            @RequestBody MoveProductStockToAnotherWarehouseResource resource,
            @PathVariable Long productId,
            @PathVariable Long warehouseId
    ) {
        var moveProductsToAnotherWarehouseCommand = MoveProductsToAnotherWarehouseCommandFromResourceAssembler.toCommandFromResource(resource, productId, warehouseId);
        var inventory = inventoryCommandService.handle(moveProductsToAnotherWarehouseCommand);
        if (inventory.isEmpty()) return ResponseEntity.badRequest().build();
        var updatedInventoryEntity = inventory.get();
        var updatedInventoryResource = InventoryResourceFromEntityAssembler.toResourceFromEntity(updatedInventoryEntity);
        return ResponseEntity.ok(updatedInventoryResource);
    }

    /**
     * This method adds stock to a product in a warehouse by its product ID, warehouse ID, and expiration date.
     * @param resource - The resource containing the details of the stock to be added.
     * @param productId - The ID of the product to which stock will be added.
     * @param warehouseId - The ID of the warehouse where the stock will be added.
     * @return - ResponseEntity containing the updated InventoryResource or a bad request response if the stock could not be added.
     */
    @PutMapping("products/{productId}/additions")
    @Operation(summary = "Add stock to a product in a warehouse",
            description = "Adds stock to a product in a warehouse by its product ID, warehouse ID, and expiration date")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Stock added successfully!"),
            @ApiResponse(responseCode = "400", description = "Stock could not be moved - invalid product or warehouse ID or resource")
    })
    @PreAuthorize("@ownership.canAccessWarehouse(authentication, #warehouseId) and @ownership.canAccessProduct(authentication, #productId)")
    public ResponseEntity<InventoryResource> addStockToProduct(
            @RequestBody AddStockToProductResource resource,
            @PathVariable Long productId,
            @PathVariable Long warehouseId
    ) {
        var addStockToProductCommand = AddStockToProductCommandFromResourceAssembler.toCommandFromResource(resource, productId, warehouseId);
        var inventory = inventoryCommandService.handle(addStockToProductCommand);
        if (inventory.isEmpty()) return ResponseEntity.badRequest().build();
        var updatedInventoryEntity = inventory.get();
        var updatedInventoryResource = InventoryResourceFromEntityAssembler.toResourceFromEntity(updatedInventoryEntity);
        return ResponseEntity.ok(updatedInventoryResource);
    }

    /**
     * This method decreases stock from a product in a warehouse by its product ID, warehouse ID, and expiration date.
     * @param resource - The resource containing the details of the stock to be decreased.
     * @param productId - The ID of the product from which stock will be decreased.
     * @param warehouseId - The ID of the warehouse from which stock will be decreased.
     * @return - ResponseEntity containing the updated InventoryResource or a bad request response if the stock could not be subtracted.
     */
    @PutMapping("products/{productId}/substractions")
    @Operation(summary = "Decrease stock from a product in a warehouse",
            description = "Decreases stock from a product in a warehouse by its product ID, warehouse ID, and expiration date.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Stock subtracted successfully!"),
            @ApiResponse(responseCode = "400", description = "Stock could not be subtracted - invalid product or warehouse ID or resource")
    })
    @PreAuthorize("@ownership.canAccessWarehouse(authentication, #warehouseId) and @ownership.canAccessProduct(authentication, #productId)")
    public ResponseEntity<InventoryResource> decreaseStockFromProduct(
            @RequestBody DecreaseStockFromProductResource resource,
            @PathVariable Long productId,
            @PathVariable Long warehouseId
    ) {
        var decreaseStockFromProductCommand = DecreaseStockFromProductCommandFromResourceAssembler.toCommandFromResource(resource, productId, warehouseId);
        var inventory = inventoryCommandService.handle(decreaseStockFromProductCommand);
        if (inventory.isEmpty()) return ResponseEntity.badRequest().build();
        var updatedInventoryEntity = inventory.get();
        var updatedInventoryResource = InventoryResourceFromEntityAssembler.toResourceFromEntity(updatedInventoryEntity);
        return ResponseEntity.ok(updatedInventoryResource);
    }

    /**
     * This method adds products to a warehouse by creating a new inventory.
     * @param resource - The resource containing the details of the products to be added.
     * @param productId - The ID of the product to be added to the warehouse.
     * @param warehouseId - The ID of the warehouse where the products will be added.
     * @return - ResponseEntity containing the created InventoryResource or a bad request response if the creation fails.
     */
    @PostMapping("products/{productId}")
    @Operation(
            summary = "Create a new inventory",
            description = "Creates a new inventory with the provided details."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Inventory created successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @PreAuthorize("@ownership.canAccessWarehouse(authentication, #warehouseId) and @ownership.canAccessProduct(authentication, #productId)")
    public ResponseEntity<InventoryResource> addProductsToWarehouse(
            @RequestBody AddProductsToWarehouseResource resource,
            @PathVariable Long productId,
            @PathVariable Long warehouseId
    ) {
        Optional<Inventory> inventory = inventoryCommandService.handle(AddProductsToWarehouseCommandFromResourceAssembler.toCommandFromResource(resource, productId, warehouseId));

        return inventory.map(source ->
                        new ResponseEntity<>(InventoryResourceFromEntityAssembler.toResourceFromEntity(source), CREATED))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    /**
     * This method deletes a product from the warehouse by its ID and best before date.
     * @param warehouseId - The ID of the warehouse from which the product will be deleted.
     * @param productId - The ID of the product to be deleted.
     * @param bestBeforeDate - The best before date of the product to be deleted.
     * @return - ResponseEntity containing a message indicating the success or failure of the deletion operation.
     */
    @DeleteMapping("products/{productId}")
    @Operation(
            summary = "Delete a product from warehouse",
            description = "Deletes a product from the warehouse by its ID and best before date."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - product could not be deleted")
    })
    @PreAuthorize("@ownership.canAccessWarehouse(authentication, #warehouseId) and @ownership.canAccessProduct(authentication, #productId)")
    public ResponseEntity<Map<String, String>> deleteProductFromWarehouse(@PathVariable Long warehouseId, @PathVariable Long productId, @RequestBody LocalDate bestBeforeDate) {
        var deleteProductCommand = new DeleteProductFromWarehouseCommand(productId, warehouseId, bestBeforeDate);
        Long deletedProductId = inventoryCommandService.handle(deleteProductCommand);
        if (deletedProductId == null) return ResponseEntity.badRequest().body(Map.of("error", "Product could not be deleted."));
        return ResponseEntity.ok(Map.of("message", "Product with ID %d deleted successfully.".formatted(deletedProductId)));
    }
}