package com.coinbattle.domain.order.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "positions")
class Position(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 30)
    val ticker: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val direction: OrderDirection,

    @Column(nullable = false, precision = 30, scale = 10)
    var quantity: BigDecimal,

    @Column(nullable = false)
    var averagePrice: Long,

    @Column(nullable = false)
    val leverage: Int,

    @Column(nullable = false)
    var margin: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PositionStatus = PositionStatus.OPEN,

    @Version
    var version: Long = 0,

    @CreationTimestamp
    @Column(updatable = false)
    val openedAt: LocalDateTime = LocalDateTime.now(),

    var closedAt: LocalDateTime? = null
) {
    fun liquidationPrice(): Long {
        val threshold = 1.0 / leverage * 0.9
        return when (direction) {
            OrderDirection.LONG -> (averagePrice * (1.0 - threshold)).toLong()
            OrderDirection.SHORT -> (averagePrice * (1.0 + threshold)).toLong()
        }
    }

    fun unrealizedPnl(currentPrice: Long): Long {
        val positionValue = quantity.toDouble() * currentPrice
        val entryValue = quantity.toDouble() * averagePrice
        return when (direction) {
            OrderDirection.LONG -> ((positionValue - entryValue) * leverage).toLong()
            OrderDirection.SHORT -> ((entryValue - positionValue) * leverage).toLong()
        }
    }

    fun evaluatedValue(currentPrice: Long): Long {
        val pnl = unrealizedPnl(currentPrice)
        return margin + pnl
    }

    fun close(closedAt: LocalDateTime = LocalDateTime.now()) {
        this.status = PositionStatus.CLOSED
        this.closedAt = closedAt
    }
}

enum class PositionStatus { OPEN, CLOSED }
