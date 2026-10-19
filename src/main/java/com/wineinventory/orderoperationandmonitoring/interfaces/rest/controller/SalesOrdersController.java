package com.wineinventory.orderoperationandmonitoring.interfaces.rest.controller;

import com.wineinventory.orderoperationandmonitoring.domain.model.aggregates.SalesOrder;
import com.wineinventory.orderoperationandmonitoring.domain.model.queries.GetAllSalesOrdersByBuyerIdQuery;
import com.wineinventory.orderoperationandmonitoring.domain.model.valueobjects.OrderStatus;
import com.wineinventory.orderoperationandmonitoring.domain.services.SalesOrderCommandService;
import com.wineinventory.orderoperationandmonitoring.domain.services.SalesOrderQueryService;
import com.wineinventory.orderoperationandmonitoring.interfaces.rest.resources.CreateSalesOrderResource;
import com.wineinventory.orderoperationandmonitoring.interfaces.rest.resources.SalesOrderResource;
import com.wineinventory.orderoperationandmonitoring.interfaces.rest.resources.UpdateSalesOrderStatusResource;
import com.wineinventory.orderoperationandmonitoring.interfaces.rest.assembler.SalesOrderResourceAssembler;
import com.wineinventory.shared.infrastructure.security.ownership.ResourceOwnershipGuard;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Orders", description = "Sales orders endpoints")
public class SalesOrdersController {

    private final SalesOrderCommandService salesOrderCommandService;
    private final SalesOrderQueryService salesOrderQueryService;
    private final ResourceOwnershipGuard ownership;

    public SalesOrdersController(SalesOrderCommandService salesOrderCommandService,
                                 SalesOrderQueryService salesOrderQueryService,
                                 ResourceOwnershipGuard ownership) {
        this.ownership = ownership;
        this.salesOrderCommandService = salesOrderCommandService;
        this.salesOrderQueryService = salesOrderQueryService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('DISTRIBUTOR') and @ownership.isAccountOwner(authentication, #resource.buyerId())")
    public ResponseEntity<SalesOrderResource> createSalesOrder(@Valid @RequestBody CreateSalesOrderResource resource) {
        var command = SalesOrderResourceAssembler.toCommand(resource);
        SalesOrder createdOrder = salesOrderCommandService.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SalesOrderResourceAssembler.toResource(createdOrder));
    }

    @GetMapping
    public List<SalesOrderResource> getSalesOrders(@RequestParam(value = "buyerId", required = false) Long buyerId,
                                                   Authentication authentication) {
        // A distributor only sees its own orders. A producer manages the sales, so it can see every order.
        if (!ownership.isProducer(authentication)) {
            buyerId = ResourceOwnershipGuard.currentAccountId(authentication).orElseThrow();
        }
        var orders = (buyerId != null)
                ? salesOrderQueryService.handle(new GetAllSalesOrdersByBuyerIdQuery(buyerId))
                : salesOrderQueryService.getAll();

        return orders.stream()
                .map(SalesOrderResourceAssembler::toResource)
                .toList();
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("@ownership.canAccessOrder(authentication, #orderId)")
    public SalesOrderResource getSalesOrderById(@PathVariable Long orderId) {
        return salesOrderQueryService.getById(orderId)
                .map(SalesOrderResourceAssembler::toResource)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Sales order with id " + orderId + " was not found"));
    }

    @PatchMapping(path = "/{orderId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('PRODUCER')")
    public SalesOrderResource updateOrderStatus(@PathVariable Long orderId,
                                                @Valid @RequestBody UpdateSalesOrderStatusResource resource) {
        try {
            var newStatus = SalesOrderResourceAssembler.parseStatus(resource.status());
            if (newStatus == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order status value is required");
            }
            SalesOrder updatedOrder = salesOrderCommandService.updateStatus(orderId, newStatus);
            return SalesOrderResourceAssembler.toResource(updatedOrder);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRODUCER')")
    public void deleteSalesOrder(@PathVariable Long orderId) {
        try {
            salesOrderCommandService.delete(orderId);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PatchMapping("/{orderId}/complete")
    @PreAuthorize("hasRole('PRODUCER')")
    public SalesOrderResource completeSalesOrder(@PathVariable Long orderId) {
        try {
            SalesOrder updatedOrder = salesOrderCommandService.updateStatus(orderId, OrderStatus.COMPLETED);
            return SalesOrderResourceAssembler.toResource(updatedOrder);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }
}
