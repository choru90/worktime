package com.worktime.domain.model

/**
 * Domain model representing a User.
 * This is a pure business object without any framework dependencies.
 */
data class User(
    val id: Long? = null,
    val name: String,
    val email: String
) {
    init {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(email.contains("@")) { "Email must be valid" }
    }
}
