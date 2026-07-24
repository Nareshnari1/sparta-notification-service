package com.training.notificationservice.controller;

import com.training.notificationservice.dto.response.NotificationResponseDto;
import com.training.notificationservice.enums.NotificationChannel;
import com.training.notificationservice.enums.NotificationStatus;
import com.training.notificationservice.exception.GlobalExceptionHandler;
import com.training.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderNotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderNotificationController controller = new OrderNotificationController(notificationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private NotificationResponseDto confirmationResponse(NotificationStatus status) {
        NotificationResponseDto dto = new NotificationResponseDto();
        dto.setId(UUID.randomUUID());
        dto.setRecipient("jane.doe@example.com");
        dto.setChannel(NotificationChannel.EMAIL);
        dto.setSubject("Order #42 confirmed");
        dto.setMessage("Hi Jane, your order has been confirmed.");
        dto.setStatus(status);
        dto.setRetryCount(0);
        dto.setCreatedAt(LocalDateTime.now());
        dto.setUpdatedAt(LocalDateTime.now());
        return dto;
    }

    @Test
    void sendOrderConfirmationReturns202WithPendingBody() throws Exception {
        // Accept-and-dispatch-later: the endpoint returns 202 immediately while
        // the notification is still PENDING; delivery happens asynchronously.
        when(notificationService.createOrderConfirmation(any()))
                .thenReturn(confirmationResponse(NotificationStatus.PENDING));

        String body = """
                {
                  "orderId": 42,
                  "customerId": 7,
                  "customerName": "Jane Doe",
                  "customerEmail": "jane.doe@example.com",
                  "totalAmount": 99.90,
                  "items": [{"productName": "Widget", "quantity": 2}],
                  "confirmedAt": "2026-07-18T10:00:00"
                }
                """;

        mockMvc.perform(post("/api/v1/notifications/order-confirmation")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void missingOrderIdReturns400() throws Exception {
        String body = """
                {
                  "customerEmail": "jane.doe@example.com",
                  "items": [{"productName": "Widget", "quantity": 2}]
                }
                """;

        mockMvc.perform(post("/api/v1/notifications/order-confirmation")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCustomerEmailReturns400() throws Exception {
        String body = """
                {
                  "orderId": 42,
                  "customerEmail": "not-an-email",
                  "items": [{"productName": "Widget", "quantity": 2}]
                }
                """;

        mockMvc.perform(post("/api/v1/notifications/order-confirmation")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyItemsReturns400() throws Exception {
        String body = """
                {
                  "orderId": 42,
                  "customerEmail": "jane.doe@example.com",
                  "items": []
                }
                """;

        mockMvc.perform(post("/api/v1/notifications/order-confirmation")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}