package com.wineinventory.inventorymanagement.interfaces.rest.controllers;

import com.wineinventory.inventorymanagement.domain.model.aggregates.Product;
import com.wineinventory.inventorymanagement.domain.model.queries.GetAllProductsByAccountIdQuery;
import com.wineinventory.inventorymanagement.domain.services.ProductCommandService;
import com.wineinventory.inventorymanagement.domain.services.ProductQueryService;
import com.wineinventory.inventorymanagement.interfaces.rest.assemblers.CreateProductCommandFromResourceAssembler;
import com.wineinventory.inventorymanagement.interfaces.rest.assemblers.ProductResourceFromEntityAssembler;
import com.wineinventory.inventorymanagement.interfaces.rest.resources.CreateProductResource;
import com.wineinventory.inventorymanagement.interfaces.rest.resources.ProductResource;
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
 * REST controller for Account Products.
 * @summary
 * This class provides REST endpoints for account products.
 * @since 1.0.0
 */
@RestController
@RequestMapping(value = "api/v1/accounts/{accountId}/products", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Accounts")
public class AccountProductsController {

    private final ProductQueryService productQueryService;
    private final ProductCommandService productCommandService;

    public AccountProductsController(ProductQueryService productQueryService, ProductCommandService productCommandService) {
        this.productQueryService = productQueryService;
        this.productCommandService = productCommandService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Create a new product",
            description = "Creates a new product with the provided details."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @PreAuthorize("@ownership.isAccountOwner(authentication, #accountId)")
    public ResponseEntity<ProductResource> createProduct(@ModelAttribute CreateProductResource resource,
                                                         @PathVariable Long accountId) {
        Optional<Product> product = productCommandService.handle(CreateProductCommandFromResourceAssembler.toCommandFromResource(resource, accountId));

        return product.map(source ->
                        new ResponseEntity<>(ProductResourceFromEntityAssembler.toResourceFromEntity(source), CREATED))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @GetMapping
    @Operation(summary = "Get all products by account ID",
            description = "Retrieves all products associated with a specific account ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Products retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Not found - invalid account ID")
    })
    @PreAuthorize("@ownership.isAccountOwner(authentication, #accountId)")
    public ResponseEntity<List<ProductResource>> getAllProductsByAccountId(@PathVariable Long accountId) {

        var getAllProductsByAccountIdQuery = new GetAllProductsByAccountIdQuery(accountId);
        var products = productQueryService.handle(getAllProductsByAccountIdQuery);
        if (products.isEmpty()) return ResponseEntity.notFound().build();
        var productEntities = products.stream().map(ProductResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(productEntities);
    }
}