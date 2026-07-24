package com.training.notificationservice.service.impl;

import com.training.notificationservice.dto.request.NotificationRequestDto;
import com.training.notificationservice.dto.request.OrderConfirmationRequestDto;
import com.training.notificationservice.dto.response.NotificationResponseDto;
import com.training.notificationservice.entity.Notification;
import com.training.notificationservice.enums.NotificationChannel;
import com.training.notificationservice.enums.NotificationStatus;
import com.training.notificationservice.exception.NotificationNotFoundException;
import com.training.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AsyncNotificationDispatcher dispatcher;

    private NotificationRequestDto validRequest() {
        NotificationRequestDto request = new NotificationRequestDto();
        request.setRecipient("jane.doe@example.com");
        request.setChannel(NotificationChannel.EMAIL);
        request.setSubject("Order confirmed");
        request.setMessage("Your order has shipped");
        return request;
    }

    @Test
    void createNotification_persistsAndDispatchesSynchronously() {
        NotificationServiceImpl service = new NotificationServiceImpl(notificationRepository, dispatcher);
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponseDto result = service.createNotification(validRequest());

        assertThat(result.getRecipient()).isEqualTo("jane.doe@example.com");
        assertThat(result.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING);
        verify(notificationRepository).save(any(Notification.class));
        verify(dispatcher).dispatch(any(Notification.class));
    }

    @Test
    void createOrderConfirmation_persistsPendingAndDispatchesAsynchronously() {
        NotificationServiceImpl service = new NotificationServiceImpl(notificationRepository, dispatcher);
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderConfirmationRequestDto request = new OrderConfirmationRequestDto();
        request.setOrderId(42L);
        request.setCustomerName("Jane Doe");
        request.setCustomerEmail("jane.doe@example.com");
        request.setTotalAmount(new BigDecimal("99.90"));
        OrderConfirmationRequestDto.Item item = new OrderConfirmationRequestDto.Item();
        item.setProductName("Widget");
        item.setQuantity(2);
        request.setItems(List.of(item));

        NotificationResponseDto result = service.createOrderConfirmation(request);

        // Accept-side response is PENDING; delivery has not run yet (it's async).
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(result.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(result.getRecipient()).isEqualTo("jane.doe@example.com");
        assertThat(result.getSubject()).isEqualTo("Order #42 confirmed");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage())
                .contains("Jane Doe")
                .contains("Widget")
                .contains("99.90");

        // Delivery is handed to the background dispatcher, not run inline.
        verify(dispatcher).dispatchAsync(any());
    }

    @Test
    void getNotificationById_whenFound_returnsMappedDto() {
        NotificationServiceImpl service = new NotificationServiceImpl(notificationRepository, dispatcher);
        UUID id = UUID.randomUUID();
        Notification entity = new Notification();
        entity.setId(id);
        entity.setRecipient("jane.doe@example.com");
        entity.setChannel(NotificationChannel.EMAIL);
        entity.setMessage("hello");
        when(notificationRepository.findById(id)).thenReturn(Optional.of(entity));

        NotificationResponseDto result = service.getNotificationById(id);

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getRecipient()).isEqualTo("jane.doe@example.com");
    }

    @Test
    void getNotificationById_whenMissing_throwsNotFound() {
        NotificationServiceImpl service = new NotificationServiceImpl(notificationRepository, dispatcher);
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotificationById(id))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}