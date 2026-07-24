package com.training.notificationservice.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The "order placed" event this service consumes from Kafka, published by the
 * Order Service when a customer places an order.
 * <p>
 * This is the Notification Service's OWN copy of the event shape - the two
 * services stay decoupled (no shared JAR); they agree only on the JSON on the
 * wire, exactly like {@code OrderConfirmationRequestDto} does for the HTTP path.
 * It is a plain POJO with a no-arg constructor and setters so Spring Kafka's
 * {@code JsonDeserializer} can bind each record to it.
 */
public class OrderPlacedEvent {

    private Long orderId;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private BigDecimal totalAmount;
    private List<Item> items;
    // Matches the producer's OrderConfirmedEvent JSON field name so the timestamp binds.
    private LocalDateTime confirmedAt;

    public OrderPlacedEvent() {
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    /** A single ordered line item, as published by the Order Service. */
    public static class Item {

        private String productName;
        private Integer quantity;

        public Item() {
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}