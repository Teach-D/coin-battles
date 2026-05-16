package com.coinbattle.domain.battle.service

import com.coinbattle.domain.battle.dto.response.BattleResultResponse
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Service
class BattleCardImageService {

    fun generateCardImage(result: BattleResultResponse): ByteArray {
        val width = 800
        val height = 400
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.color = Color(0x18, 0x18, 0x1B)
        g.fillRect(0, 0, width, height)

        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, 28)
        g.drawString("CoinBattle 결과", 40, 60)

        val winner = result.participants.firstOrNull { it.isWinner } ?: result.participants.firstOrNull()
        if (winner != null) {
            val profitColor = if (winner.profitRate >= 0) Color(0x2D, 0xD4, 0xBF) else Color(0xF8, 0x71, 0x71)
            g.font = Font("SansSerif", Font.BOLD, 48)
            g.color = Color.WHITE
            g.drawString(winner.nickname, 40, 140)
            g.color = profitColor
            g.font = Font("SansSerif", Font.BOLD, 36)
            val sign = if (winner.profitRate >= 0) "+" else ""
            g.drawString("$sign${"%.2f".format(winner.profitRate)}%", 40, 190)
        }

        g.color = Color(0x3F, 0x3F, 0x46)
        g.fillRect(40, 220, width - 80, 2)

        g.font = Font("SansSerif", Font.PLAIN, 20)
        val displayParticipants = result.participants.sortedBy { it.rank }.take(5)
        displayParticipants.forEachIndexed { index, participant ->
            val y = 260 + index * 30
            val profitColor = if (participant.profitRate >= 0) Color(0x2D, 0xD4, 0xBF) else Color(0xF8, 0x71, 0x71)
            g.color = Color.WHITE
            g.drawString("#${participant.rank}", 40, y)
            g.drawString(participant.nickname, 100, y)
            g.color = profitColor
            val sign = if (participant.profitRate >= 0) "+" else ""
            g.drawString("$sign${"%.2f".format(participant.profitRate)}%", 400, y)
        }

        g.dispose()

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "png", outputStream)
        return outputStream.toByteArray()
    }
}
