package com.coinbattle.domain.user.service

import com.coinbattle.domain.user.entity.AuthProvider
import com.coinbattle.domain.user.entity.CoinBattlePrincipal
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class OAuth2UserService(
    private val userService: UserService
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val provider = AuthProvider.valueOf(registrationId.uppercase())

        val (email, nickname, profileImageUrl, providerId) = when (registrationId) {
            "google" -> parseGoogle(oAuth2User.attributes)
            "kakao" -> parseKakao(oAuth2User.attributes)
            else -> throw IllegalArgumentException("Unsupported provider: $registrationId")
        }

        val user = userService.findOrCreateSocialUser(email, nickname, profileImageUrl, provider, providerId)
        return CoinBattlePrincipal(user, oAuth2User.attributes)
    }

    private fun parseGoogle(attrs: Map<String, Any>): OAuthAttributes = OAuthAttributes(
        email = attrs["email"] as String,
        nickname = attrs["name"] as String,
        profileImageUrl = attrs["picture"] as? String,
        providerId = attrs["sub"] as String
    )

    @Suppress("UNCHECKED_CAST")
    private fun parseKakao(attrs: Map<String, Any>): OAuthAttributes {
        val account = attrs["kakao_account"] as Map<String, Any>
        val profile = account["profile"] as Map<String, Any>
        val email = (account["email"] as? String) ?: "${attrs["id"]}@kakao.local"
        return OAuthAttributes(
            email = email,
            nickname = profile["nickname"] as String,
            profileImageUrl = profile["profile_image_url"] as? String,
            providerId = attrs["id"].toString()
        )
    }

    private data class OAuthAttributes(
        val email: String,
        val nickname: String,
        val profileImageUrl: String?,
        val providerId: String
    )
}
