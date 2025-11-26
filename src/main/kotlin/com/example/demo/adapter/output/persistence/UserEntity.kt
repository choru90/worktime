package com.example.demo.adapter.output.persistence

import jakarta.persistence.*

/**
 * JPA Entity for User persistence (Outbound Adapter).
 * This is separate from the domain model to maintain separation of concerns.
 */
@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false)
    var name: String,
    
    @Column(nullable = false, unique = true)
    var email: String
)
