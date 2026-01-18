package com.worktime.application.service

import com.worktime.application.port.output.NotificationRepository
import com.worktime.application.port.output.UserRepository
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.Notification
import com.worktime.domain.model.NotificationType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * 알림 서비스
 */
@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository
) {
    
    /**
     * 새로운 알림을 생성합니다
     */
    @Transactional
    fun createNotification(
        userId: UUID,
        type: NotificationType,
        title: String,
        message: String,
        referenceId: UUID? = null
    ): Notification {
        val user = userRepository.findById(userId)
            ?: throw ResourceNotFoundException("User not found with id: $userId")
        
        val notification = Notification(
            user = user,
            type = type,
            title = title,
            message = message,
            referenceId = referenceId
        )
        
        return notificationRepository.save(notification)
    }
    
    /**
     * 사용자의 알림 목록을 조회합니다
     */
    fun getNotificationsByUserId(userId: UUID): List<Notification> {
        return notificationRepository.findByUserId(userId)
    }
    
    /**
     * 사용자의 읽지 않은 알림 개수를 조회합니다
     */
    fun getUnreadCount(userId: UUID): Long {
        return notificationRepository.countUnreadByUserId(userId)
    }
    
    /**
     * 알림을 읽음 처리합니다
     */
    @Transactional
    fun markAsRead(notificationId: UUID, userId: UUID) {
        val notification = notificationRepository.findById(notificationId)
            ?: throw ResourceNotFoundException("Notification not found with id: $notificationId")
        
        // 본인의 알림인지 확인
        if (notification.user.id != userId) {
            throw IllegalArgumentException("Not authorized to mark this notification as read")
        }
        
        notification.markAsRead()
        notificationRepository.save(notification)
    }
    
    /**
     * 사용자의 모든 알림을 읽음 처리합니다
     */
    @Transactional
    fun markAllAsRead(userId: UUID): Int {
        return notificationRepository.markAllAsReadByUserId(userId)
    }
}
