package com.rehand.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rehand.domain.Notification;
import com.rehand.domain.NotificationType;
import com.rehand.domain.User;
import com.rehand.repository.NotificationRepository;

/**
 * Stores in-app notifications. Called from other services inside their transaction, so a notification
 * is only saved when the change it reports is saved too. The bell and the list page come in step 7.
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Saves one unread notification. The message is cut to 255 characters (the column size).
     *
     * @param link relative URL the notification opens, for example "/orders/12", or null
     */
    public Notification notify(User user, NotificationType type, String message, String link) {
        String text = message == null ? "" : message.trim();
        if (text.length() > Notification.MESSAGE_MAX) {
            text = text.substring(0, Notification.MESSAGE_MAX - 3) + "...";
        }
        String url = link != null && link.length() > 255 ? null : link;
        return notificationRepository.save(new Notification(user, type, text, url));
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return userId == null ? 0 : notificationRepository.countByUserIdAndReadFalse(userId);
    }
}
