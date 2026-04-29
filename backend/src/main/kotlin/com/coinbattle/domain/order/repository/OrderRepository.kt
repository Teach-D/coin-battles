package com.coinbattle.domain.order.repository

import com.coinbattle.domain.order.entity.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {
    fun existsByIdempotencyKey(idempotencyKey: String): Boolean
    fun findByIdempotencyKey(idempotencyKey: String): Order?
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Order>
}
