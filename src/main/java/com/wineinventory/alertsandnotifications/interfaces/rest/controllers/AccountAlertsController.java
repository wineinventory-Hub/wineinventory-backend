package com.wineinventory.alertsandnotifications.interfaces.rest.controllers;

import com.wineinventory.alertsandnotifications.domain.model.queries.GetAllAlertsByAccountIdQuery;
import com.wineinventory.alertsandnotifications.domain.model.valueobjects.AccountId;
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
 * Controller for managing alerts associated with a specific account.
 *
 * @summary
 * REST controller that exposes endpoints for account-specific alert operations,
 * allowing retrieval of alerts by account ID.
 *
 * @since 1.0
 */
@RestController
@RequestMapping(value = "api/v1/accounts/{accountId}/alerts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Accounts")
public class AccountAlertsController {

    private final AlertQueryService alertQueryService;

    /**
     * Constructor for AccountAlertsController.
     *
     * @param alertQueryService The query service for retrieving alert information.
     */
    public AccountAlertsController(AlertQueryService alertQueryService) {
        this.alertQueryService = alertQueryService;
    }

    /**
     * This endpoint retrieves all alerts associated with a specific account ID.
     *
     * @param accountId The account ID for which alerts are being requested.
     * @return The ResponseEntity containing the list of alert resources, or a NotFound result if no alerts found.
     */
    @Operation(
            summary = "Get all alerts by account ID",
            description = "Retrieves all alerts associated with a specific account ID",
            operationId = "GetAlertsByAccountId"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns all alerts by account ID.", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = AlertResource.class))),
            @ApiResponse(responseCode = "404", description = "No alerts found for the specified account.")
    })
    @GetMapping
    @PreAuthorize("@ownership.isAccountOwner(authentication, #accountId)")
    public ResponseEntity<?> getAlertsByAccountId(@PathVariable String accountId) {
        var targetAccountId = new AccountId(accountId);
        var getAllAlertsByAccountIdQuery = new GetAllAlertsByAccountIdQuery(targetAccountId);
        var alerts = alertQueryService.handle(getAllAlertsByAccountIdQuery);
        if (alerts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var alertResources = alerts.stream()
                .map(AlertResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(alertResources);
    }
}