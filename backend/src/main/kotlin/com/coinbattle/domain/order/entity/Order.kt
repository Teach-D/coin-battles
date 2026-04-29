package com.coinbattle.domain.order.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class Order(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val userId: Long,

    @Column
    var positionId: Long? = null,

    @Column(nullable = false, unique = true, length = 64)
    val idempotencyKey: String,

    @Column(nullable = false, length = 30)
    val ticker: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val orderType: OrderType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val direction: OrderDirection,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val side: OrderSide,

    @Column
    val requestedAmount: Long? = null,

    @Column
    val limitPrice: Long? = null,

    @Column
    var executedPrice: Long? = null,

    @Column
    var executedAmount: Long? = null,

    @Column(precision = 30, scale = 10)
    var executedQuantity: BigDecimal? = null,

    @Column(nullable = false)
    val leverage: Int = 1,

    @Column(precision = 5, scale = 4)
    val closeRatio: BigDecimal? = null,

    @Column
    var realizedPnl: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING,

    @CreationTimestamp
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class OrderType { MARKET, LIMIT }
enum class OrderDirection { LONG, SHORT }
enum class OrderSide { BUY, SELL }
enum class OrderStatus { PENDING, FILLED, PARTIALLY_FILLED, CANCELLED }
