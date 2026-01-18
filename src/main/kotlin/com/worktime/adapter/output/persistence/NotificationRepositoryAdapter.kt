package com.worktime.adapter.output.persistence

import com.worktime.application.port.output.NotificationRepository
import com.worktime.application.port.output.UserRepository
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.Notification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Notification Repository 어댑터
 */
@Component
@Transactional(readOnly = true)
class NotificationRepositoryAdapter(
    private val notificationJpaRepository: NotificationJpaRepository,
    private val userRepository: UserRepository
) : NotificationRepository {
    
    @Transactional
    override fun save(notification: Notification): Notification {
        return notificationJpaRepository.save(notification)
    }
    
    override fun findById(id: UUID): Notification? {
        return notificationJpaRepository.findById(id).orElse(null)
    }
    
    override fun findByUserId(userId: UUID): List<Notification> {
        val user = userRepository.findById(userId)
            ?: throw ResourceNotFoundException("User not found with id: $userId")
        
        return notificationJpaRepository.findByUserOrderByCreatedAtDesc(user)
    }
    
    override fun countUnreadByUserId(userId: UUID): Long {
        val user = userRepository.findById(userId)
            ?: throw ResourceNotFoundException("User not found with id: $userId")
        
        return notificationJpaRepository.countByUserAndIsReadFalse(user)
    }
    
    @Transactional
    override fun markAllAsReadByUserId(userId: UUID): Int {
        return notificationJpaRepository.markAllAsReadByUserId(userId)
    }
}
