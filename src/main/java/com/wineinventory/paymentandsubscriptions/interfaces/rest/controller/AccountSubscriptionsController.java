package com.wineinventory.paymentandsubscriptions.interfaces.rest.controller;

import com.wineinventory.paymentandsubscriptions.domain.repositories.PlanRepository;
import com.wineinventory.paymentandsubscriptions.interfaces.rest.assemblers.CreateSubscriptionAssembler;
import com.wineinventory.paymentandsubscriptions.interfaces.rest.resources.UpgradeSubscriptionResource;
import com.wineinventory.paymentandsubscriptions.interfaces.rest.assemblers.SubscriptionAssembler;
import com.wineinventory.paymentandsubscriptions.application.internal.outboundservices.paymentproviders.services.PaypalService;
import com.wineinventory.paymentandsubscriptions.application.internal.outboundservices.paymentproviders.models.PaypalOrder;
import com.wineinventory.paymentandsubscriptions.application.internal.outboundservices.paymentproviders.models.PaypalOrderItem;
import com.wineinventory.paymentandsubscriptions.application.internal.commandservices.SubscriptionCommandService;
import com.wineinventory.paymentandsubscriptions.application.internal.queryservices.SubscriptionQueryService;
import com.wineinventory.paymentandsubscriptions.domain.model.aggregates.Subscription;
import com.wineinventory.paymentandsubscriptions.domain.model.commands.CreateSubscriptionCommand;
import com.wineinventory.paymentandsubscriptions.infrastructure.external.paypal.PayPalSubscriptionService;
import com.wineinventory.paymentandsubscriptions.domain.model.queries.PayPalCreateSubscriptionQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/subscriptions")
@Tag(name = "Accounts", description = "Account Management")
public class AccountSubscriptionsController {

    private final SubscriptionCommandService subscriptionCommandService;
    private final PaypalService paypalService;
    private final SubscriptionQueryService subscriptionQueryService;
    private final PayPalSubscriptionService payPalSubscriptionService;
    private final PlanRepository planRepository;

    public AccountSubscriptionsController(
            SubscriptionCommandService subscriptionCommandService,
            PaypalService paypalService,
            SubscriptionQueryService subscriptionQueryService,
            PayPalSubscriptionService payPalSubscriptionService,
            PlanRepository planRepository) {
        this.subscriptionCommandService = subscriptionCommandService;
        this.paypalService = paypalService;
        this.subscriptionQueryService = subscriptionQueryService;
        this.payPalSubscriptionService = payPalSubscriptionService;
        this.planRepository = planRepository;
    }

    @PostMapping
    @Operation(summary = "Create a new subscription for an account", 
               description = "Initializes a new subscription for the specified account.")
    @PreAuthorize("@ownership.isAccountOwner(authentication, #accountId)")
    public ResponseEntity<SubscriptionAssembler> createSubscription(
            @Parameter(description = "Account ID", required = true) @PathVariable String accountId,
            @RequestBody CreateSubscriptionAssembler request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        try {
            System.err.println("[createSubscription] accountId=" + accountId + ", selectedPlanId=" + request.selectedPlanId());
            if (accountId == null || accountId.isBlank()) return ResponseEntity.badRequest().build();
            if (request == null || request.selectedPlanId() == null || request.selectedPlanId().isBlank()) return ResponseEntity.badRequest().build();

            Long userId = Long.parseLong(accountId);

            var existing = subscriptionQueryService.handleFindByUser(userId).stream()
                    .filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus()))
                    .findFirst();
            if (existing.isPresent()) {
                return ResponseEntity.status(409).build();
            }
            var requested = request.selectedPlanId();
            var planOpt = planRepository.findByPlanId(requested);
            if (planOpt.isEmpty()) planOpt = planRepository.findByPlanId(requested != null ? requested.trim() : null);
            var plan = planOpt.orElseGet(() -> {
                var normalized = requested != null ? requested.trim().toLowerCase() : "";
                var all = planRepository.findAll();
                var match = all.stream()
                    .filter(p -> p.getPlanId() != null && p.getPlanId().trim().toLowerCase().equals(normalized))
                    .findFirst();
                if (match.isPresent()) return match.get();
                System.err.println("[createSubscription] plan not found. requested=" + requested + ", availablePlanIds=" +
                        all.stream().map(p -> p.getPlanId()).toList());
                throw new IllegalArgumentException("Plan not found");
            });
            System.err.println("[createSubscription] plan found: id=" + plan.getId() + ", planId=" + plan.getPlanId() + ", type=" + plan.getPlanType() + ", currency=" + plan.getCurrency() + ", price=" + plan.getPrice());

            Subscription subscription;
            String planType = plan.getPlanType();
            if ("Free".equalsIgnoreCase(planType)) {
                var command = new CreateSubscriptionCommand(
                        userId,
                        plan.getId(),
                        null,
                        plan.getCurrency(),
                        plan.getPrice(),
                        "ACTIVE",
                        null
                );
                subscription = subscriptionCommandService.handle(command);
            } else {
                if (plan.getPaypalPlanId() == null || plan.getPaypalPlanId().isBlank()) {
                    return ResponseEntity.badRequest().build();
                }
                PayPalCreateSubscriptionQuery pp = (idempotencyKey != null && !idempotencyKey.isBlank())
                        ? payPalSubscriptionService.createSubscription(plan.getPaypalPlanId(), idempotencyKey)
                        : payPalSubscriptionService.createSubscription(plan.getPaypalPlanId());

                String approvalUrl = pp.links().stream()
                        .filter(l -> "approve".equalsIgnoreCase(l.rel()))
                        .map(PayPalCreateSubscriptionQuery.PayPalLink::href)
                        .findFirst()
                        .orElse(null);

                var command = new CreateSubscriptionCommand(
                        userId,
                        plan.getId(),
                        pp.id(),
                        plan.getCurrency(),
                        plan.getPrice(),
                        "PENDING_APPROVAL",
                        approvalUrl
                );
                subscription = subscriptionCommandService.handle(command);
            }
            System.err.println("[createSubscription] subscription saved: id=" + subscription.getId() + ", status=" + subscription.getStatus());

            SubscriptionAssembler response = SubscriptionAssembler.fromSubscriptionWithPayment(
                subscription.getId().toString(),
                plan.getPlanId(), 
                subscription.getStatus(),
                subscription.getNextBillingDate() != null ? subscription.getNextBillingDate().toString() : null,
                plan.getPlanType(),
                plan.getPaymentFrequency(),
                plan.getMaxProducts(),
                subscription.getPaypalSubscriptionId(),
                subscription.getApprovalUrl(),
                subscription.getApprovalUrl(),
                "Subscription created. Redirect user to approvalUrl."
            );

            return ResponseEntity.status(201).body(response);
            
        } catch (IllegalArgumentException e) {
            System.err.println("createSubscription error: " + e.getMessage());
            if ("Plan not found".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            System.err.println("createSubscription unexpected error: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{subscriptionId}")
    @Operation(summary = "Upgrade an existing subscription for an account", 
               description = "Upgrades an existing subscription for the specified account.")
    @PreAuthorize("@ownership.isAccountOwner(authentication, #accountId)")
    public ResponseEntity<SubscriptionAssembler> upgradeSubscription(
            @Parameter(description = "Account ID", required = true) @PathVariable String accountId,
            @Parameter(description = "Subscription ID", required = true) @PathVariable String subscriptionId,
            @RequestBody UpgradeSubscriptionResource request) {
        try {
            List<PaypalOrderItem> items = List.of(
                new PaypalOrderItem(
                    "Subscription Upgrade", 
                    "Upgrade to " + request.newPlanId(),
                    "PLAN-" + request.newPlanId(),
                    BigDecimal.valueOf(149.99), 
                    BigDecimal.ZERO,
                    1,
                    "DIGITAL_GOODS"
                )
            );
            
            PaypalOrder paypalOrder = paypalService.createOrder(
                BigDecimal.valueOf(149.99), 
                "USD", 
                items
            );
            
            SubscriptionAssembler response = SubscriptionAssembler.fromSubscriptionWithPayment(
                subscriptionId,
                request.newPlanId(),
                "PENDING_UPGRADE",
                "2024-12-31",
                "MONTHLY",
                "MONTHLY",
                1000,
                paypalOrder.getPaypalOrderId(),
                "https://www.paypal.com/checkoutnow?token=" + paypalOrder.getPaypalOrderId(),
                "https://www.paypal.com/checkoutnow?token=" + paypalOrder.getPaypalOrderId(),
                "Subscription upgrade initiated. Please complete payment."
            );
            
            return ResponseEntity.status(201).body(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    @Operation(summary = "Get subscription by account id", 
               description = "Retrieves a subscription for the specified account.")
    @PreAuthorize("@ownership.isAccountOwner(authentication, #accountId)")
    public ResponseEntity<SubscriptionAssembler> getSubscriptionByAccountId(
            @Parameter(description = "Account ID", required = true) @PathVariable String accountId) {
        try {
            Long userId = Long.parseLong(accountId);
            var list = subscriptionQueryService.handleFindByUser(userId);
            if (list == null || list.isEmpty()) return ResponseEntity.notFound().build();
            var subscription = list.stream().max(Comparator.comparingLong(Subscription::getId)).get();
            var plan = planRepository.findById(subscription.getPlanId()).orElse(null);
            String planIdentifier = plan != null ? plan.getPlanId() : null;
            String planType = plan != null ? plan.getPlanType() : null;
            String paymentFrequency = plan != null ? plan.getPaymentFrequency() : null;
            Integer maxProducts = plan != null ? plan.getMaxProducts() : null;

            SubscriptionAssembler response = SubscriptionAssembler.fromSubscriptionWithPayment(
                    subscription.getId().toString(),
                    planIdentifier,
                    subscription.getStatus(),
                    subscription.getNextBillingDate() != null ? subscription.getNextBillingDate().toString() : null,
                    planType,
                    paymentFrequency,
                    maxProducts,
                    subscription.getPaypalSubscriptionId(),
                    subscription.getApprovalUrl(),
                    subscription.getApprovalUrl(),
                    null
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
