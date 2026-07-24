package com.training.notificationservice.service.impl;

import com.training.notificationservice.dto.request.NotificationRequestDto;
import com.training.notificationservice.dto.request.OrderConfirmationRequestDto;
import com.training.notificationservice.dto.response.NotificationResponseDto;
import com.training.notificationservice.entity.Notification;
import com.training.notificationservice.enums.NotificationChannel;
import com.training.notificationservice.enums.NotificationStatus;
import com.training.notificationservice.exception.NotificationNotFoundException;
import com.training.notificationservice.repository.NotificationRepository;
import com.training.notificationservice.repository.NotificationSpecifications;
import com.training.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates notification creation, lookup, and search. Channel delivery is
 * delegated to {@link AsyncNotificationDispatcher} - synchronously for the
 * direct create endpoints (which return the final SENT/FAILED status), and
 * asynchronously for the inter-service order-confirmation endpoint (which
 * accepts the request and lets delivery finish in the background).
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final AsyncNotificationDispatcher dispatcher;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                    AsyncNotificationDispatcher dispatcher) {
        this.notificationRepository = notificationRepository;
        this.dispatcher = dispatcher;
    }

    @Override
    @Transactional
    public NotificationResponseDto createNotification(NotificationRequestDto request) {
        log.info("Creating {} notification for recipient={}", request.getChannel(), mask(request.getRecipient()));

        Notification notification = toEntity(request);
        Notification saved = notificationRepository.save(notification);

        dispatcher.dispatch(saved);

        log.info("Notification {} persisted with status={}", saved.getId(), saved.getStatus());
        return toResponseDto(saved);
    }

    /**
     * Accept-and-dispatch-later flow for the Order Service. Deliberately NOT
     * {@code @Transactional}: the save commits in its own transaction so the row
     * is visible when {@link AsyncNotificationDispatcher#dispatchAsync(UUID)}
     * reloads it on the background thread. Returns immediately with status
     * PENDING - the caller gets a fast 202 instead of waiting for the SMTP send.
     */
    @Override
    public NotificationResponseDto createOrderConfirmation(OrderConfirmationRequestDto request) {
        log.info("Accepting order-confirmation notification for order={} recipient={}",
                request.getOrderId(), mask(request.getCustomerEmail()));

        NotificationRequestDto generic = new NotificationRequestDto();
        generic.setRecipient(request.getCustomerEmail());
        generic.setChannel(NotificationChannel.EMAIL);
        generic.setSubject("Order #" + request.getOrderId() + " confirmed");
        generic.setMessage(buildConfirmationMessage(request));

        Notification saved = notificationRepository.save(toEntity(generic));
        dispatcher.dispatchAsync(saved.getId());

        log.info("Notification {} accepted (status={}); delivery running asynchronously",
                saved.getId(), saved.getStatus());
        return toResponseDto(saved);
    }

    /** Renders the order details into a human-readable email body. */
    private String buildConfirmationMessage(OrderConfirmationRequestDto request) {
        StringBuilder body = new StringBuilder();
        String name = request.getCustomerName() == null || request.getCustomerName().isBlank()
                ? "there" : request.getCustomerName();
        body.append("Hi ").append(name).append(",\n\n")
                .append("Thanks for your order! Order #").append(request.getOrderId())
                .append(" has been confirmed.\n\n");

        if (request.getItems() != null) {
            body.append("Items:\n");
            request.getItems().forEach(item ->
                    body.append("  - ").append(item.getProductName())
                            .append(" x ").append(item.getQuantity()).append('\n'));
        }

        if (request.getTotalAmount() != null) {
            body.append("\nTotal: ").append(request.getTotalAmount());
        }
        return body.toString();
    }

    @Override
    public NotificationResponseDto getNotificationById(UUID id) {
        log.debug("Looking up notification {}", id);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Notification {} not found", id);
                    return new NotificationNotFoundException(id);
                });
        return toResponseDto(notification);
    }

    @Override
    public Page<NotificationResponseDto> searchNotifications(String recipient, NotificationStatus status,
                                                               NotificationChannel channel, Pageable pageable) {
        log.debug("Searching notifications recipient={}, status={}, channel={}, page={}",
                recipient == null ? null : mask(recipient), status, channel, pageable);
        return notificationRepository
                .findAll(NotificationSpecifications.filterBy(recipient, status, channel), pageable)
                .map(this::toResponseDto);
    }

    private Notification toEntity(NotificationRequestDto request) {
        Notification notification = new Notification();
        notification.setRecipient(request.getRecipient());
        notification.setChannel(request.getChannel());
        notification.setSubject(request.getSubject());
        notification.setMessage(request.getMessage());
        notification.setTemplateId(request.getTemplateId());
        return notification;
    }

    private NotificationResponseDto toResponseDto(Notification notification) {
        NotificationResponseDto dto = new NotificationResponseDto();
        dto.setId(notification.getId());
        dto.setRecipient(notification.getRecipient());
        dto.setChannel(notification.getChannel());
        dto.setSubject(notification.getSubject());
        dto.setMessage(notification.getMessage());
        dto.setTemplateId(notification.getTemplateId());
        dto.setStatus(notification.getStatus());
        dto.setRetryCount(notification.getRetryCount());
        dto.setRead(notification.isRead());
        dto.setCreatedAt(notification.getCreatedAt());
        dto.setUpdatedAt(notification.getUpdatedAt());
        return dto;
    }

    /** Masks a recipient (email/phone) so PII never appears in full in logs. */
    private String mask(String recipient) {
        if (recipient == null || recipient.length() <= 2) {
            return "***";
        }
        int visible = Math.min(2, recipient.length() - 1);
        return recipient.substring(0, visible) + "***";
    }
}