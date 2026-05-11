package com.coinbattle.domain.order.service

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.market.repository.TickerRedisRepository
import com.coinbattle.domain.order.dto.request.BuyOrderRequest
import com.coinbattle.domain.order.dto.request.SellOrderRequest
import com.coinbattle.domain.order.dto.response.OrderHistoryResponse
import com.coinbattle.domain.order.dto.response.OrderResponse
import com.coinbattle.domain.order.dto.response.PortfolioResponse
import com.coinbattle.domain.order.dto.response.PositionResponse
import com.coinbattle.domain.order.entity.Order
import com.coinbattle.domain.order.entity.OrderDirection
import com.coinbattle.domain.order.entity.OrderSide
import com.coinbattle.domain.order.entity.OrderStatus
import com.coinbattle.domain.order.entity.OrderType
import com.coinbattle.domain.order.entity.Position
import com.coinbattle.domain.order.entity.PositionStatus
import com.coinbattle.domain.order.event.OrderFilledEvent
import com.coinbattle.domain.order.repository.OrderRepository
import com.coinbattle.domain.order.repository.PositionRepository
import com.coinbattle.domain.user.repository.UserRepository
import org.redisson.api.RedissonClient
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

@Service
class OrderService(
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository,
    private val positionRepository: PositionRepository,
    private val tickerRedisRepository: TickerRedisRepository,
    private val redissonClient: RedissonClient,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val secureRandom = SecureRandom()
    private val BD_SCALE = 10
    private val BD_ROUNDING = RoundingMode.HALF_UP

    fun buy(userId: Long, request: BuyOrderRequest): OrderResponse {
        if (request.orderType == OrderType.LIMIT && request.limitPrice == null) {
            throw CoinBattleException(ErrorCode.LIMIT_PRICE_REQUIRED)
        }

        orderRepository.findByIdempotencyKey(request.idempotencyKey)
            ?.let { return OrderResponse.from(it) }

        val lock = redissonClient.getLock("user:${userId}:order")
        if (!lock.tryLock(0, 3, TimeUnit.SECONDS)) {
            throw CoinBattleException(ErrorCode.ORDER_LOCK_TIMEOUT)
        }

        try {
            return executeBuy(userId, request)
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
        }
    }

    fun sell(userId: Long, request: SellOrderRequest): OrderResponse {
        orderRepository.findByIdempotencyKey(request.idempotencyKey)
            ?.let { return OrderResponse.from(it) }

        val lock = redissonClient.getLock("user:${userId}:order")
        if (!lock.tryLock(0, 3, TimeUnit.SECONDS)) {
            throw CoinBattleException(ErrorCode.ORDER_LOCK_TIMEOUT)
        }

        try {
            return executeSell(userId, request)
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
        }
    }

    @Transactional
    fun executeBuy(userId: Long, request: BuyOrderRequest): OrderResponse {
        if (orderRepository.existsByIdempotencyKey(request.idempotencyKey)) {
            throw CoinBattleException(ErrorCode.DUPLICATE_ORDER)
        }

        val user = userRepository.findById(userId).orElseThrow {
            CoinBattleException(ErrorCode.USER_NOT_FOUND)
        }

        val currentPrice = resolvePrice(request.ticker, request.orderType, request.limitPrice)
        val slippage = applySlippage(currentPrice, request.amount, isBuyEntry(request.direction))
        val executedPrice = slippage.executedPrice
        val margin = request.amount
        val notionalValue = BigDecimal(margin).multiply(BigDecimal(request.leverage))
        val executedQuantity = notionalValue
            .divide(BigDecimal(executedPrice), BD_SCALE, BD_ROUNDING)

        if (user.balance < margin) {
            throw CoinBattleException(ErrorCode.INSUFFICIENT_BALANCE)
        }

        user.balance -= margin

        val position = upsertPosition(
            userId = userId,
            ticker = request.ticker,
            direction = request.direction,
            addQuantity = executedQuantity,
            executedPrice = executedPrice,
            leverage = request.leverage,
            addMargin = margin
        )

        val order = orderRepository.save(
            Order(
                userId = userId,
                positionId = position.id,
                idempotencyKey = request.idempotencyKey,
                ticker = request.ticker,
                orderType = request.orderType,
                direction = request.direction,
                side = OrderSide.BUY,
                requestedAmount = request.amount,
                limitPrice = request.limitPrice,
                executedPrice = executedPrice,
                executedAmount = margin,
                executedQuantity = executedQuantity,
                leverage = request.leverage,
                status = OrderStatus.FILLED
            )
        )

        val evaluatedValue = resolvePortfolioEvaluatedValue(userId, user.balance, currentPrice, position)
        eventPublisher.publishEvent(OrderFilledEvent(order.id, userId, request.ticker, evaluatedValue))

        return OrderResponse.from(order).copy(marketPrice = currentPrice, slippageRate = slippage.slippageRate)
    }

    @Transactional
    fun executeSell(userId: Long, request: SellOrderRequest): OrderResponse {
        if (orderRepository.existsByIdempotencyKey(request.idempotencyKey)) {
            throw CoinBattleException(ErrorCode.DUPLICATE_ORDER)
        }

        val user = userRepository.findById(userId).orElseThrow {
            CoinBattleException(ErrorCode.USER_NOT_FOUND)
        }

        val position = positionRepository.findById(request.positionId).orElseThrow {
            CoinBattleException(ErrorCode.POSITION_NOT_FOUND)
        }

        if (position.userId != userId) throw CoinBattleException(ErrorCode.POSITION_NOT_OWNED)
        if (position.status == PositionStatus.CLOSED) throw CoinBattleException(ErrorCode.POSITION_ALREADY_CLOSED)

        val closeRatioBd = request.closeRatio.setScale(BD_SCALE, BD_ROUNDING)
        val currentPrice = resolvePrice(position.ticker, OrderType.MARKET, null)
        val slippage = applySlippage(currentPrice, position.margin, isSellEntry(position.direction))
        val executedPrice = slippage.executedPrice

        val closeQuantity = position.quantity
            .multiply(closeRatioBd)
            .setScale(BD_SCALE, BD_ROUNDING)
        val closeMargin = BigDecimal(position.margin)
            .multiply(closeRatioBd)
            .setScale(BD_SCALE, BD_ROUNDING)
            .toLong()

        val entryValue = closeQuantity
            .multiply(BigDecimal(position.averagePrice))
            .setScale(BD_SCALE, BD_ROUNDING)
        val exitValue = closeQuantity
            .multiply(BigDecimal(executedPrice))
            .setScale(BD_SCALE, BD_ROUNDING)

        val rawPnl = when (position.direction) {
            OrderDirection.LONG -> exitValue.subtract(entryValue)
            OrderDirection.SHORT -> entryValue.subtract(exitValue)
        }
        val realizedPnl = rawPnl
            .multiply(BigDecimal(position.leverage))
            .setScale(BD_SCALE, BD_ROUNDING)
            .toLong()

        user.balance += closeMargin + realizedPnl

        val isFullClose = closeRatioBd.compareTo(BigDecimal.ONE) == 0
        if (isFullClose) {
            position.close()
        } else {
            position.quantity = position.quantity.subtract(closeQuantity).setScale(BD_SCALE, BD_ROUNDING)
            position.margin -= closeMargin
        }

        val order = orderRepository.save(
            Order(
                userId = userId,
                positionId = position.id,
                idempotencyKey = request.idempotencyKey,
                ticker = position.ticker,
                orderType = OrderType.MARKET,
                direction = position.direction,
                side = OrderSide.SELL,
                requestedAmount = null,
                executedPrice = executedPrice,
                executedAmount = closeMargin,
                executedQuantity = closeQuantity,
                leverage = position.leverage,
                closeRatio = closeRatioBd,
                realizedPnl = realizedPnl,
                status = OrderStatus.FILLED
            )
        )

        val evaluatedValue = resolvePortfolioEvaluatedValue(userId, user.balance, currentPrice, position)
        eventPublisher.publishEvent(OrderFilledEvent(order.id, userId, position.ticker, evaluatedValue))

        return OrderResponse.from(order).copy(marketPrice = currentPrice, slippageRate = slippage.slippageRate)
    }

    @Transactional
    fun forceClose(positionId: Long, liquidationPrice: Long): OrderResponse {
        val position = positionRepository.findById(positionId).orElseThrow {
            CoinBattleException(ErrorCode.POSITION_NOT_FOUND)
        }
        if (position.status == PositionStatus.CLOSED) throw CoinBattleException(ErrorCode.POSITION_ALREADY_CLOSED)

        val idempotencyKey = "liquidation:${positionId}:${System.currentTimeMillis()}"

        val closeQuantity = position.quantity
        val entryValue = closeQuantity.multiply(BigDecimal(position.averagePrice)).setScale(BD_SCALE, BD_ROUNDING)
        val exitValue = closeQuantity.multiply(BigDecimal(liquidationPrice)).setScale(BD_SCALE, BD_ROUNDING)

        val rawPnl = when (position.direction) {
            OrderDirection.LONG -> exitValue.subtract(entryValue)
            OrderDirection.SHORT -> entryValue.subtract(exitValue)
        }
        val realizedPnl = rawPnl
            .multiply(BigDecimal(position.leverage))
            .setScale(BD_SCALE, BD_ROUNDING)
            .toLong()

        val user = userRepository.findById(position.userId).orElseThrow {
            CoinBattleException(ErrorCode.USER_NOT_FOUND)
        }

        val returnAmount = (position.margin + realizedPnl).coerceAtLeast(0L)
        user.balance += returnAmount

        position.close()

        val order = orderRepository.save(
            Order(
                userId = position.userId,
                positionId = position.id,
                idempotencyKey = idempotencyKey,
                ticker = position.ticker,
                orderType = OrderType.MARKET,
                direction = position.direction,
                side = OrderSide.SELL,
                executedPrice = liquidationPrice,
                executedAmount = position.margin,
                executedQuantity = closeQuantity,
                leverage = position.leverage,
                closeRatio = BigDecimal.ONE,
                realizedPnl = realizedPnl,
                status = OrderStatus.FILLED
            )
        )

        val evaluatedValue = user.balance
        eventPublisher.publishEvent(OrderFilledEvent(order.id, position.userId, position.ticker, evaluatedValue))

        return OrderResponse.from(order)
    }

    @Transactional(readOnly = true)
    fun getPortfolio(userId: Long): PortfolioResponse {
        val user = userRepository.findById(userId).orElseThrow {
            CoinBattleException(ErrorCode.USER_NOT_FOUND)
        }
        val openPositions = positionRepository.findByUserIdAndStatus(userId, PositionStatus.OPEN)
        val positionResponses = openPositions.map { position ->
            val currentPrice = tickerRedisRepository.findByMarket(position.ticker)
                ?.tradePrice?.toLong() ?: position.averagePrice
            PositionResponse.from(position, currentPrice)
        }
        return PortfolioResponse.of(userId, user.balance, positionResponses)
    }

    @Transactional(readOnly = true)
    fun getOrderHistory(userId: Long): List<OrderHistoryResponse> =
        orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .map { OrderHistoryResponse.from(it) }

    private fun upsertPosition(
        userId: Long,
        ticker: String,
        direction: OrderDirection,
        addQuantity: BigDecimal,
        executedPrice: Long,
        leverage: Int,
        addMargin: Long
    ): Position {
        val existing = positionRepository.findByUserIdAndTickerAndDirectionAndStatus(
            userId, ticker, direction, PositionStatus.OPEN
        )

        if (existing != null) {
            val totalQuantity = existing.quantity.add(addQuantity).setScale(BD_SCALE, BD_ROUNDING)
            val existingEntryValue = existing.quantity
                .multiply(BigDecimal(existing.averagePrice))
                .setScale(BD_SCALE, BD_ROUNDING)
            val newEntryValue = addQuantity
                .multiply(BigDecimal(executedPrice))
                .setScale(BD_SCALE, BD_ROUNDING)
            val newAveragePrice = existingEntryValue
                .add(newEntryValue)
                .divide(totalQuantity, BD_SCALE, BD_ROUNDING)
                .toLong()

            existing.quantity = totalQuantity
            existing.averagePrice = newAveragePrice
            existing.margin += addMargin
            return existing
        }

        return positionRepository.save(
            Position(
                userId = userId,
                ticker = ticker,
                direction = direction,
                quantity = addQuantity.setScale(BD_SCALE, BD_ROUNDING),
                averagePrice = executedPrice,
                leverage = leverage,
                margin = addMargin
            )
        )
    }

    private fun resolvePrice(ticker: String, orderType: OrderType, limitPrice: Long?): Long {
        if (orderType == OrderType.LIMIT && limitPrice != null) return limitPrice
        return tickerRedisRepository.findByMarket(ticker)
            ?.tradePrice?.toLong()
            ?: throw CoinBattleException(ErrorCode.TICKER_NOT_FOUND)
    }

    private data class SlippageResult(val executedPrice: Long, val slippageRate: BigDecimal)

    private fun applySlippage(basePrice: Long, amount: Long, isAdverseHigh: Boolean): SlippageResult {
        val rate = when {
            amount <= 1_000_000L -> BigDecimal.ZERO
            amount <= 5_000_000L -> BigDecimal("0.0005")
            else -> {
                val randomBasisPoints = 10 + secureRandom.nextInt(21)
                BigDecimal(randomBasisPoints).divide(BigDecimal("10000"), BD_SCALE, BD_ROUNDING)
            }
        }

        if (rate.compareTo(BigDecimal.ZERO) == 0) return SlippageResult(basePrice, BigDecimal.ZERO)

        val adjustment = BigDecimal(basePrice).multiply(rate).setScale(BD_SCALE, BD_ROUNDING)
        val executedPrice = if (isAdverseHigh) {
            BigDecimal(basePrice).add(adjustment).setScale(0, BD_ROUNDING).toLong()
        } else {
            BigDecimal(basePrice).subtract(adjustment).setScale(0, BD_ROUNDING).toLong()
        }
        val signedRate = if (isAdverseHigh) rate else rate.negate()
        return SlippageResult(executedPrice, signedRate)
    }

    private fun isBuyEntry(direction: OrderDirection): Boolean = direction == OrderDirection.LONG

    private fun isSellEntry(direction: OrderDirection): Boolean = direction == OrderDirection.SHORT

    private fun resolvePortfolioEvaluatedValue(
        userId: Long,
        currentBalance: Long,
        latestPrice: Long,
        latestPosition: Position
    ): Long {
        val openPositions = positionRepository.findByUserIdAndStatus(userId, PositionStatus.OPEN)
        var totalPnl = 0L
        var totalMargin = 0L
        for (pos in openPositions) {
            val price = if (pos.id == latestPosition.id) latestPrice
            else tickerRedisRepository.findByMarket(pos.ticker)?.tradePrice?.toLong() ?: pos.averagePrice
            totalMargin += pos.margin
            totalPnl += pos.unrealizedPnl(price)
        }
        return currentBalance + totalMargin + totalPnl
    }
}
