package com.worktime.domain.model

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.*

/**
 * Notification (알림) 엔티티
 * 사용자에게 전송되는 알림을 나타냅니다.
 */
@Entity
@Table(
    name = "notifications",
    indexes = [
        Index(name = "idx_notification_user", columnList = "user_id"),
        Index(name = "idx_notification_created", columnList = "created_at"),
        Index(name = "idx_notification_read", columnList = "is_read")
    ]
)
class Notification(
    @Id
    @Column(columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
    
    /**
     * 알림을 받을 사용자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = ForeignKey(name = "fk_notification_user"))
    val user: User,
    
    /**
     * 알림 제목
     */
    @Column(nullable = false, length = 100)
    val title: String,
    
    /**
     * 알림 메시지
     */
    @Column(nullable = false, length = 500)
    val message: String,
    
    /**
     * 알림 타입
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,
    
    /**
     * 관련 엔티티의 ID (예: Schedule ID)
     * 알림 클릭 시 해당 페이지로 이동하기 위해 사용
     */
    @Column(name = "reference_id", columnDefinition = "uuid")
    val referenceId: UUID? = null,
    
    /**
     * 알림 읽음 여부
     */
    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,
    
    /**
     * 알림 생성 시각 (UTC)
     */
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    
    /**
     * 알림을 읽음 처리합니다
     */
    fun markAsRead() {
        isRead = true
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Notification) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "Notification(id=$id, type=$type, title='$title', isRead=$isRead)"
    }
}
