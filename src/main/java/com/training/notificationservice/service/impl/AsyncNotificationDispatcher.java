package com.training.notificationservice.service.impl;

import com.training.notificationservice.entity.Notification;
import com.training.notificationservice.enums.NotificationChannel;
import com.training.notificationservice.enums.NotificationStatus;
import com.training.notificationservice.repository.NotificationRepository;
import com.training.notificationservice.service.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns channel dispatch (the Strategy lookup + delivery + status transition),
 * offered in two flavours:
 * <ul>
 *   <li>{@link #dispatch(Notification)} - synchronous, joins the caller's
 *       transaction; used by the direct create endpoints that return the final
 *       SENT/FAILED status to the caller.</li>
 *   <li>{@link #dispatchAsync(UUID)} - runs on a background thread in its own
 *       transaction; used by the inter-service accept endpoints so the HTTP
 *       response can return immediately while delivery happens afterwards.</li>
 * </ul>
 * Kept as a separate bean so {@code @Async} is applied via the Spring proxy
 * (self-invocation from the service would bypass it).
 */
@Component
public class AsyncNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AsyncNotificationDispatcher.class);

    private final NotificationRepository notificationRepository;
    private final Map<NotificationChannel, NotificationSender> sendersByChannel;

    public AsyncNotificationDispatcher(NotificationRepository notificationRepository,
                                        List<NotificationSender> senders) {
        this.notificationRepository = notificationRepository;
        this.sendersByChannel = senders.stream()
                .collect(Collectors.toMap(NotificationSender::getChannel, Function.identity()));
    }

    /** Synchronous dispatch, joining the caller's transaction. */
    @Transactional
    public void dispatch(Notification notification) {
        applyDispatch(notification);
    }

    /**
     * Background dispatch: reloads the (already-committed) notification by id in
     * a fresh transaction, then delivers it. Any failure is captured on the
     * entity as FAILED rather than propagated - there is no caller to return to.
     */
    @Async("notificationDispatchExecutor")
    @Transactional
    public void dispatchAsync(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) {
            log.warn("Async dispatch skipped: notification {} no longer exists", notificationId);
            return;
        }
        applyDispatch(notification);
    }

    /**
     * Attempts delivery through the sender registered for this notification's
     * channel. If no channel implementation has been added yet, the notification
     * is left in its current status rather than treated as an error.
     */
    private void applyDispatch(Notification notification) {
        NotificationSender sender = sendersByChannel.get(notification.getChannel());
        if (sender == null) {
            log.info("No sender registered yet for channel={}; leaving notification {} as {}",
                    notification.getChannel(), notification.getId(), notification.getStatus());
            return;
        }
        try {
            sender.send(notification);
            notification.setStatus(NotificationStatus.SENT);
        } catch (Exception ex) {
            log.error("Dispatch failed for notification {} on channel {}: {}",
                    notification.getId(), notification.getChannel(), ex.getMessage(), ex);
            notification.setRetryCount(notification.getRetryCount() + 1);
            notification.setStatus(NotificationStatus.FAILED);
        }
        notificationRepository.save(notification);
    }
}