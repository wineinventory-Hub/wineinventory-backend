package com.wineinventory.alertsandnotifications.interfaces.rest.controllers;

import com.wineinventory.alertsandnotifications.domain.model.queries.GetAlertByIdQuery;
import com.wineinventory.alertsandnotifications.domain.services.AlertCommandService;
import com.wineinventory.alertsandnotifications.domain.services.AlertQueryService;
import com.wineinventory.alertsandnotifications.interfaces.rest.assemblers.AlertResourceFromEntityAssembler;
import com.wineinventory.alertsandnotifications.interfaces.rest.resources.AlertResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;


/**
 * This controller provides endpoints for managing alerts.
 *
 * @summary
 * REST controller that exposes endpoints for alert management operations,
 * including retrieval and status updates.
 *
 * @since 1.0
 */
@RestController
@RequestMapping(value = "api/v1/alerts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Alerts", description = "Available Alert Endpoints")
public class AlertsController {

    private final AlertQueryService alertQueryService;

    /**
     * Constructor for AlertsController.
     *
     * @param alertCommandService The command service for handling alert operations.
     * @param alertQueryService   The query service for retrieving alert information.
     */
    public AlertsController(AlertCommandService alertCommandService, AlertQueryService alertQueryService) {
        this.alertQueryService = alertQueryService;
    }

    /**
     * This endpoint retrieves an alert by its unique identifier.
     *
     * @param alertId The unique identifier of the alert to be retrieved.
     * @return The ResponseEntity containing the alert resource if found, or a NotFound result if not found.
     */
    @Operation(
            summary = "Get Alert by ID",
            description = "Retrieves an alert by its unique identifier.",
            operationId = "GetAlertById"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alert found!", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = AlertResource.class))),
            @ApiResponse(responseCode = "404", description = "Alert not found...")
    })
    @GetMapping("/{alertId}")
    @PreAuthorize("@ownership.canAccessAlert(authentication, #alertId)")
    public ResponseEntity<AlertResource> getAlertById(@PathVariable String alertId) {
        var alert = alertQueryService.handle(new GetAlertByIdQuery(alertId));
        if (alert.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var resource = AlertResourceFromEntityAssembler.toResourceFromEntity(alert.get());
        return ResponseEntity.ok(resource);
    }
}