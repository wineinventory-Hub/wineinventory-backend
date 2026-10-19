package com.wineinventory.shared.infrastructure.security.ownership;

import com.wineinventory.alertsandnotifications.domain.model.queries.GetAlertByIdQuery;
import com.wineinventory.alertsandnotifications.domain.services.AlertQueryService;
import com.wineinventory.authorization.infrastructure.Authorization.sfs.model.UserDetailsImpl;
import com.wineinventory.inventorymanagement.domain.model.queries.GetProductByIdQuery;
import com.wineinventory.inventorymanagement.domain.model.queries.GetWarehouseByIdQuery;
import com.wineinventory.inventorymanagement.domain.services.ProductQueryService;
import com.wineinventory.inventorymanagement.domain.services.WarehouseQueryService;
import com.wineinventory.orderoperationandmonitoring.domain.services.SalesOrderQueryService;
import com.wineinventory.reportingandcareguide.domain.model.queries.GetCareGuideByIdQuery;
import com.wineinventory.reportingandcareguide.domain.services.CareGuideQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Resource Ownership Guard.
 * <p>
 * This component is used from {@code @PreAuthorize} expressions to check that the authenticated user
 * owns the resource it is trying to access. The account id of a user is its user id.
 * When the resource does not exist the check passes, so the controller can answer with 404.
 * </p>
 * Example: {@code @PreAuthorize("@ownership.isAccountOwner(authentication, #accountId)")}
 */
@Component("ownership")
public class ResourceOwnershipGuard {

    private final WarehouseQueryService warehouseQueryService;
    private final ProductQueryService productQueryService;
    private final AlertQueryService alertQueryService;
    private final CareGuideQueryService careGuideQueryService;
    private final SalesOrderQueryService salesOrderQueryService;

    public ResourceOwnershipGuard(WarehouseQueryService warehouseQueryService,
                                  ProductQueryService productQueryService,
                                  AlertQueryService alertQueryService,
                                  CareGuideQueryService careGuideQueryService,
                                  SalesOrderQueryService salesOrderQueryService) {
        this.warehouseQueryService = warehouseQueryService;
        this.productQueryService = productQueryService;
        this.alertQueryService = alertQueryService;
        this.careGuideQueryService = careGuideQueryService;
        this.salesOrderQueryService = salesOrderQueryService;
    }

    /**
     * Returns the account id of the authenticated user.
     * @param authentication the current authentication
     * @return the account id, or empty when the request is not authenticated with a user
     */
    public static Optional<Long> currentAccountId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl user) {
            return Optional.ofNullable(user.getUserId());
        }
        return Optional.empty();
    }

    /**
     * Checks that the account id belongs to the authenticated user.
     * @param authentication the current authentication
     * @param accountId the account id of the request, as a number or a string
     * @return true if the authenticated user is the owner of the account
     */
    public boolean isAccountOwner(Authentication authentication, Object accountId) {
        if (accountId == null) return false;
        return currentAccountId(authentication)
                .map(current -> current.toString().equals(accountId.toString().trim()))
                .orElse(false);
    }

    public boolean canAccessWarehouse(Authentication authentication, Long warehouseId) {
        if (warehouseId == null || warehouseId <= 0) return true;
        return warehouseQueryService.handle(new GetWarehouseByIdQuery(warehouseId))
                .map(warehouse -> isAccountOwner(authentication, warehouse.getAccountId().accountId()))
                .orElse(true);
    }

    public boolean canAccessProduct(Authentication authentication, Long productId) {
        if (productId == null || productId <= 0) return true;
        return productQueryService.handle(new GetProductByIdQuery(productId))
                .map(product -> isAccountOwner(authentication, product.getAccountId().accountId()))
                .orElse(true);
    }

    public boolean canAccessAlert(Authentication authentication, String alertId) {
        if (alertId == null || alertId.isBlank()) return true;
        return alertQueryService.handle(new GetAlertByIdQuery(alertId))
                .map(alert -> isAccountOwner(authentication, alert.getAccountId().accountId()))
                .orElse(true);
    }

    public boolean canAccessCareGuide(Authentication authentication, Long careGuideId) {
        if (careGuideId == null) return true;
        return careGuideQueryService.handle(new GetCareGuideByIdQuery(careGuideId)).stream()
                .allMatch(careGuide -> isAccountOwner(authentication, careGuide.getAccountId()));
    }

    /**
     * A distributor can only access its own orders. A producer manages the sales, so it can access every order.
     */
    public boolean canAccessOrder(Authentication authentication, Long orderId) {
        if (isProducer(authentication)) return true;
        return salesOrderQueryService.getById(orderId)
                .map(order -> isAccountOwner(authentication, order.getBuyerId()))
                .orElse(true);
    }

    public boolean isProducer(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> Objects.equals(authority.getAuthority(), "ROLE_PRODUCER"));
    }
}
