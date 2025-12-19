package com.worktime.application.port.output

import com.worktime.domain.model.Owner
import java.util.*

/**
 * Owner Repository Output Port
 */
interface OwnerRepository {
    fun save(owner: Owner): Owner
    fun findById(id: UUID): Owner?
    fun findByUserId(userId: UUID): Owner?
}
