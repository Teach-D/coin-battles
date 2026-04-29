package com.coinbattle.domain.order.repository

import com.coinbattle.domain.order.entity.OrderDirection
import com.coinbattle.domain.order.entity.Position
import com.coinbattle.domain.order.entity.PositionStatus
import org.springframework.data.jpa.repository.JpaRepository

interface PositionRepository : JpaRepository<Position, Long> {
    fun findByUserIdAndStatus(userId: Long, status: PositionStatus): List<Position>
    fun findByUserId(userId: Long): List<Position>
    fun findByUserIdAndTickerAndStatus(userId: Long, ticker: String, status: PositionStatus): Position?
    fun findByUserIdAndTickerAndDirectionAndStatus(
        userId: Long,
        ticker: String,
        direction: OrderDirection,
        status: PositionStatus
    ): Position?
    fun findAllByStatus(status: PositionStatus): List<Position>
}
