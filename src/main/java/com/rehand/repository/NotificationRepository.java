package com.rehand.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rehand.domain.Notification;

/** Notifications. The list page and "mark as read" arrive with the bell in step 7. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByUserIdAndReadFalse(Long userId);
}
