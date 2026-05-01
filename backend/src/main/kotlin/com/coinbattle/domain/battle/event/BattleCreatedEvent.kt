package com.coinbattle.domain.battle.event

import com.coinbattle.domain.battle.entity.Battle
import org.springframework.context.ApplicationEvent
import java.time.Instant

class BattleCreatedEvent(source: Any, val battle: Battle, val occurredAt: Instant = Instant.now()) : ApplicationEvent(source)