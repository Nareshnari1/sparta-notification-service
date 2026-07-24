package com.training.notificationservice.messaging;

import com.training.notificationservice.dto.request.OrderConfirmationRequestDto;
import com.training.notificationservice.event.OrderPlacedEvent;
import com.training.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Kafka ingestion adapter for order events. Mirrors {@code OrderNotificationController}
 * (the HTTP entry point) but for the messaging channel: it receives an
 * {@link OrderPlacedEvent}, maps it onto the existing {@link OrderConfirmationRequestDto},
 * and hands it to {@link NotificationService#createOrderConfirmation}, which
 * formats the email and runs it through the same async dispatch pipeline.
 * <p>
 * Thin by design - no formatting or persistence here; this class only adapts the
 * event onto the contract the service already understands.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final NotificationService notificationService;

    public OrderEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Consumes one "order placed" record. The topic and consumer group come from
     * {@code application.properties}; the container factory and JSON deserializer
     * are auto-configured by Spring Boot from the {@code spring.kafka.*} settings.
     *
     * <p>Malformed events (missing the fields we need to build a notification) are
     * logged and skipped - retrying them would never succeed. Any other failure
     * (e.g. the database being down) is allowed to propagate to Spring Kafka's
     * error handler, which retries before giving up, so a transient outage does
     * not silently drop the notification.
     */
    @KafkaListener(topics = "${notification.kafka.order-confirmed-topic}")
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (event == null || event.getOrderId() == null
                || event.getCustomerEmail() == null || event.getCustomerEmail().isBlank()) {
            log.warn("Skipping malformed order-placed event (missing orderId or customerEmail): {}", event);
            return;
        }

        log.info("Received order-placed event for order={}", event.getOrderId());
        notificationService.createOrderConfirmation(toConfirmationRequest(event));
        log.info("Order-placed event for order={} handed to notification pipeline", event.getOrderId());
    }

    /** Adapts the consumed event onto the DTO the service layer already accepts. */
    private OrderConfirmationRequestDto toConfirmationRequest(OrderPlacedEvent event) {
        OrderConfirmationRequestDto request = new OrderConfirmationRequestDto();
        request.setOrderId(event.getOrderId());
        request.setCustomerId(event.getCustomerId());
        request.setCustomerName(event.getCustomerName());
        request.setCustomerEmail(event.getCustomerEmail());
        request.setTotalAmount(event.getTotalAmount());
        request.setConfirmedAt(event.getConfirmedAt());
        request.setItems(toItems(event.getItems()));
        return request;
    }

    private List<OrderConfirmationRequestDto.Item> toItems(List<OrderPlacedEvent.Item> eventItems) {
        if (eventItems == null) {
            return List.of();
        }
        return eventItems.stream()
                .map(source -> {
                    OrderConfirmationRequestDto.Item item = new OrderConfirmationRequestDto.Item();
                    item.setProductName(source.getProductName());
                    item.setQuantity(source.getQuantity());
                    return item;
                })
                .collect(Collectors.toList());
    }
}