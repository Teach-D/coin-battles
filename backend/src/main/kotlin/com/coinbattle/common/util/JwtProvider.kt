package com.coinbattle.common.util

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import io.jsonwebtoken.*
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(
    @Value("\${jwt.secret}") secretKey: String,
    @Value("\${jwt.access-expiration-ms}") private val accessExpirationMs: Long,
    @Value("\${jwt.refresh-expiration-ms}") private val refreshExpirationMs: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey))

    fun generateAccessToken(userId: Long, role: String): String =
        Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .claim("type", "access")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + accessExpirationMs))
            .signWith(key)
            .compact()

    fun generateRefreshToken(userId: Long): String =
        Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + refreshExpirationMs))
            .signWith(key)
            .compact()

    fun getUserId(token: String): Long =
        getClaims(token).subject.toLong()

    fun validate(token: String): Boolean {
        return try {
            getClaims(token)
            true
        } catch (e: ExpiredJwtException) {
            throw CoinBattleException(ErrorCode.EXPIRED_TOKEN)
        } catch (e: JwtException) {
            throw CoinBattleException(ErrorCode.INVALID_TOKEN)
        }
    }

    private fun getClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}
