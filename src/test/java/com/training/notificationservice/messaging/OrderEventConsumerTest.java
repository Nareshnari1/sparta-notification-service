package com.training.notificationservice.messaging;

import com.training.notificationservice.dto.request.OrderConfirmationRequestDto;
import com.training.notificationservice.event.OrderPlacedEvent;
import com.training.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    private OrderPlacedEvent validEvent() {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setOrderId(42L);
        event.setCustomerId(7L);
        event.setCustomerName("Jane Doe");
        event.setCustomerEmail("jane.doe@example.com");
        event.setTotalAmount(new BigDecimal("99.90"));
        event.setConfirmedAt(LocalDateTime.parse("2026-07-18T10:00:00"));
        OrderPlacedEvent.Item item = new OrderPlacedEvent.Item();
        item.setProductName("Widget");
        item.setQuantity(2);
        event.setItems(List.of(item));
        return event;
    }

    @Test
    void onOrderPlaced_validEvent_mapsAndDelegatesToService() {
        OrderEventConsumer consumer = new OrderEventConsumer(notificationService);

        consumer.onOrderPlaced(validEvent());

        // The event should be mapped 1:1 onto the DTO the service already accepts.
        ArgumentCaptor<OrderConfirmationRequestDto> captor =
                ArgumentCaptor.forClass(OrderConfirmationRequestDto.class);
        verify(notificationService).createOrderConfirmation(captor.capture());

        OrderConfirmationRequestDto request = captor.getValue();
        assertThat(request.getOrderId()).isEqualTo(42L);
        assertThat(request.getCustomerId()).isEqualTo(7L);
        assertThat(request.getCustomerName()).isEqualTo("Jane Doe");
        assertThat(request.getCustomerEmail()).isEqualTo("jane.doe@example.com");
        assertThat(request.getTotalAmount()).isEqualByComparingTo("99.90");
        assertThat(request.getConfirmedAt()).isEqualTo(LocalDateTime.parse("2026-07-18T10:00:00"));
        assertThat(request.getItems()).hasSize(1);
        assertThat(request.getItems().get(0).getProductName()).isEqualTo("Widget");
        assertThat(request.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void onOrderPlaced_nullItems_mapsToEmptyList() {
        OrderEventConsumer consumer = new OrderEventConsumer(notificationService);
        OrderPlacedEvent event = validEvent();
        event.setItems(null);

        consumer.onOrderPlaced(event);

        ArgumentCaptor<OrderConfirmationRequestDto> captor =
                ArgumentCaptor.forClass(OrderConfirmationRequestDto.class);
        verify(notificationService).createOrderConfirmation(captor.capture());
        assertThat(captor.getValue().getItems()).isEmpty();
    }

    @Test
    void onOrderPlaced_missingEmail_isSkipped() {
        OrderEventConsumer consumer = new OrderEventConsumer(notificationService);
        OrderPlacedEvent event = validEvent();
        event.setCustomerEmail("  ");

        consumer.onOrderPlaced(event);

        // Malformed events are dropped, never forwarded to the service.
        verifyNoInteractions(notificationService);
    }

    @Test
    void onOrderPlaced_missingOrderId_isSkipped() {
        OrderEventConsumer consumer = new OrderEventConsumer(notificationService);
        OrderPlacedEvent event = validEvent();
        event.setOrderId(null);

        consumer.onOrderPlaced(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void onOrderPlaced_nullEvent_isSkipped() {
        OrderEventConsumer consumer = new OrderEventConsumer(notificationService);

        consumer.onOrderPlaced(null);

        verifyNoInteractions(notificationService);
    }
}