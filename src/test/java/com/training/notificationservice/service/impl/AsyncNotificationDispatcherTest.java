package com.training.notificationservice.service.impl;

import com.training.notificationservice.entity.Notification;
import com.training.notificationservice.enums.NotificationChannel;
import com.training.notificationservice.enums.NotificationStatus;
import com.training.notificationservice.repository.NotificationRepository;
import com.training.notificationservice.service.NotificationSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncNotificationDispatcherTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationSender emailSender;

    private Notification emailNotification() {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setRecipient("jane.doe@example.com");
        n.setChannel(NotificationChannel.EMAIL);
        n.setMessage("hello");
        return n;
    }

    @Test
    void dispatch_success_marksSentAndSaves() {
        when(emailSender.getChannel()).thenReturn(NotificationChannel.EMAIL);
        AsyncNotificationDispatcher dispatcher =
                new AsyncNotificationDispatcher(notificationRepository, List.of(emailSender));
        Notification n = emailNotification();

        dispatcher.dispatch(n);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(emailSender).send(n);
        verify(notificationRepository).save(n);
    }

    @Test
    void dispatch_whenSenderThrows_marksFailedAndIncrementsRetryCount() {
        when(emailSender.getChannel()).thenReturn(NotificationChannel.EMAIL);
        doThrow(new RuntimeException("provider unreachable")).when(emailSender).send(any(Notification.class));
        AsyncNotificationDispatcher dispatcher =
                new AsyncNotificationDispatcher(notificationRepository, List.of(emailSender));
        Notification n = emailNotification();

        dispatcher.dispatch(n);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.getRetryCount()).isEqualTo(1);
        verify(notificationRepository).save(n);
    }

    @Test
    void dispatch_withNoSenderRegistered_leavesStatusPendingAndDoesNotSave() {
        AsyncNotificationDispatcher dispatcher =
                new AsyncNotificationDispatcher(notificationRepository, Collections.emptyList());
        Notification n = emailNotification();

        dispatcher.dispatch(n);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void dispatchAsync_reloadsByIdThenDispatches() {
        when(emailSender.getChannel()).thenReturn(NotificationChannel.EMAIL);
        AsyncNotificationDispatcher dispatcher =
                new AsyncNotificationDispatcher(notificationRepository, List.of(emailSender));
        Notification n = emailNotification();
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        dispatcher.dispatchAsync(n.getId());

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(emailSender).send(n);
        verify(notificationRepository).save(n);
    }

    @Test
    void dispatchAsync_whenNotificationMissing_isNoOp() {
        AsyncNotificationDispatcher dispatcher =
                new AsyncNotificationDispatcher(notificationRepository, Collections.emptyList());
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        dispatcher.dispatchAsync(id);

        verify(notificationRepository, never()).save(any(Notification.class));
    }
}