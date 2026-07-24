package com.training.notificationservice.controller;

import com.training.notificationservice.dto.request.OrderConfirmationRequestDto;
import com.training.notificationservice.dto.response.NotificationResponseDto;
import com.training.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound integration endpoint for the Order Service. When an order is
 * confirmed, the Order Service POSTs an order-shaped payload here; this
 * controller validates it and hands off to {@link NotificationService}, which
 * formats the email and runs it through the shared dispatch pipeline.
 * <p>
 * Thin by design - all order-to-notification mapping lives in the service layer.
 */
@RestController
@RequestMapping("/api/v1/notifications/order-confirmation")
@Tag(name = "Order Notifications", description = "Order Service integration endpoint")
public class OrderNotificationController {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationController.class);

    private final NotificationService notificationService;

    public OrderNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @Operation(summary = "Accept an order-confirmation notification (called by the Order Service)")
    public ResponseEntity<NotificationResponseDto> sendOrderConfirmation(
            @Valid @RequestBody OrderConfirmationRequestDto request) {
        log.info("POST /api/v1/notifications/order-confirmation order={}", request.getOrderId());
        // 202 Accepted: the notification is persisted and queued; delivery
        // finishes asynchronously so the Order Service is not blocked on SMTP.
        NotificationResponseDto accepted = notificationService.createOrderConfirmation(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
    }
}