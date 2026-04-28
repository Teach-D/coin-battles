package com.coinbattle.domain.user.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Convert(converter = EmailEncryptConverter::class)
    @Column(nullable = false, unique = true, length = 500)
    var email: String,

    @Column(nullable = false, unique = true, length = 50)
    var nickname: String,

    @Column(length = 500)
    var profileImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val provider: AuthProvider,

    @Column(nullable = false)
    val providerId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: UserRole = UserRole.ROLE_USER,

    @Column(nullable = false)
    var balance: Long = 10_000_000L,

    @Version
    var version: Long = 0,

    @CreationTimestamp
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class AuthProvider { GOOGLE, KAKAO }
enum class UserRole { ROLE_USER, ROLE_ADMIN }
