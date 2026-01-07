package com.worktime.adapter.input.web.dto.store

import java.util.UUID

/**
 * 영업장 생성 요청
 */
data class StoreCreateRequest(
    val name: String,
    val address: String? = null,
    val timezone: String = "Asia/Seoul"
)

/**
 * 영업장 수정 요청
 */
data class StoreUpdateRequest(
    val name: String? = null,
    val address: String? = null,
    val timezone: String? = null
)

/**
 * 영업장 응답
 */
data class StoreResponse(
    val id: UUID,
    val name: String,
    val address: String?,
    val timezone: String,
    val active: Boolean
) {
    companion object {
        fun from(store: com.worktime.domain.model.Store): StoreResponse {
            return StoreResponse(
                id = store.id,
                name = store.name,
                address = store.address,
                timezone = store.timezone,
                active = store.active
            )
        }
    }
}
