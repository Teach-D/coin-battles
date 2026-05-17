package com.coinbattle.domain.battle

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InviteControllerTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("postgres:16")

        @Container
        @JvmStatic
        val redis: GenericContainer<Nothing> = GenericContainer<Nothing>("redis:7-alpine")
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    // ── POST /api/battles/{battleId}/invite ─────────────────────────

    @Test
    fun `초대코드_생성_인증_토큰_없으면_401_반환`() {
        // given
        val battleId = UUID.randomUUID()

        // when & then
        mockMvc.post("/api/battles/$battleId/invite") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `초대코드_생성_비참가자_요청시_403_반환`() {
        // given
        val battleId = UUID.randomUUID()

        // when & then
        mockMvc.post("/api/battles/$battleId/invite") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer valid_participant_token_for_other_battle")
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `초대코드_생성_WAITING_배틀_요청시_400_반환`() {
        // given
        val battleId = UUID.randomUUID()

        // when & then
        mockMvc.post("/api/battles/$battleId/invite") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer valid_participant_token")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ── POST /api/battles/join/{inviteCode} ─────────────────────────

    @Test
    fun `초대코드_참가_인증_토큰_없으면_401_반환`() {
        // given
        val inviteCode = UUID.randomUUID().toString()

        // when & then
        mockMvc.post("/api/battles/join/$inviteCode") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `만료된_초대코드로_참가시_404_반환`() {
        // given
        val expiredCode = UUID.randomUUID().toString()

        // when & then
        mockMvc.post("/api/battles/join/$expiredCode") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer valid_user_token")
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `이미_참가중인_배틀_초대코드로_재참가시_409_반환`() {
        // given
        val inviteCode = UUID.randomUUID().toString()

        // when & then
        mockMvc.post("/api/battles/join/$inviteCode") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer already_joined_user_token")
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `FINISHED_배틀_초대코드로_참가시_400_반환`() {
        // given
        val inviteCode = UUID.randomUUID().toString()

        // when & then
        mockMvc.post("/api/battles/join/$inviteCode") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer valid_user_token")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
