package com.training.notificationservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Inbound wire contract for an order-confirmation event published by the Order
 * Service. Mirrors {@code com.training.orderservice.client.dto.OrderConfirmationRequest}
 * field-for-field so the two services stay decoupled (no shared JAR) while
 * remaining JSON-compatible.
 * <p>
 * The Order Service sends order-shaped data; turning that into a channel,
 * subject, and message body is the Notification Service's responsibility, so
 * that formatting logic lives here rather than leaking into every caller.
 */
public class OrderConfirmationRequestDto {

    @NotNull(message = "orderId is required")
    private Long orderId;

    private Long customerId;

    private String customerName;

    @NotBlank(message = "customerEmail is required")
    @Email(message = "customerEmail must be a valid email address")
    private String customerEmail;

    private BigDecimal totalAmount;

    @NotEmpty(message = "items must not be empty")
    @Valid
    private List<Item> items;

    private LocalDateTime confirmedAt;

    public OrderConfirmationRequestDto() {
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

    /** A single ordered line item, as sent by the Order Service. */
    public static class Item {

        @NotBlank(message = "productName is required")
        private String productName;

        @NotNull(message = "quantity is required")
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
