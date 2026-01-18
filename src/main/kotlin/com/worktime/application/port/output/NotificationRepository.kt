package com.worktime.application.port.output

import com.worktime.domain.model.Notification
import java.util.*

/**
 * 알림 저장소 인터페이스
 */
interface NotificationRepository {
    
    /**
     * 알림을 저장합니다
     */
    fun save(notification: Notification): Notification
    
    /**
     * ID로 알림을 조회합니다
     */
    fun findById(id: UUID): Notification?
    
    /**
     * 사용자의 모든 알림을 최신순으로 조회합니다
     */
    fun findByUserId(userId: UUID): List<Notification>
    
    /**
     * 사용자의 읽지 않은 알림 개수를 조회합니다
     */
    fun countUnreadByUserId(userId: UUID): Long
    
    /**
     * 사용자의 모든 알림을 읽음 처리합니다
     */
    fun markAllAsReadByUserId(userId: UUID): Int
}
