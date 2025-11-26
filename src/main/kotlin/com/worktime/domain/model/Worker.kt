package com.worktime.domain.model

import jakarta.persistence.*
import java.util.*

/**
 * Worker (알바생) entity.
 * Represents a part-time worker in the system.
 */
@Entity
@Table(name = "workers")
class Worker(
    @Id
    @Column(columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
    
    @Column(nullable = false, length = 100)
    var name: String,
    
    @Column(nullable = false, length = 20)
    var phoneNumber: String,
    
    @Column(nullable = false)
    var active: Boolean = true
) : BaseEntity() {
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Worker) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "Worker(id=$id, name='$name', phoneNumber='$phoneNumber', active=$active)"
    }
}
