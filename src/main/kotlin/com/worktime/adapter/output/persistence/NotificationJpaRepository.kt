package com.worktime.adapter.output.persistence

import com.worktime.domain.model.Notification
import com.worktime.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

/**
 * Notification JPA Repository
 */
interface NotificationJpaRepository : JpaRepository<Notification, UUID> {
    
    /**
     * 사용자의 모든 알림을 최신순으로 조회
     */
    fun findByUserOrderByCreatedAtDesc(user: User): List<Notification>
    
    /**
     * 사용자의 읽지 않은 알림 개수 조회
     */
    fun countByUserAndIsReadFalse(user: User): Long
    
    /**
     * 사용자의 모든 알림을 읽음 처리
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    fun markAllAsReadByUserId(@Param("userId") userId: UUID): Int
}
