package org.rph.pkd.state.runs

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

data class Run(
    val plugin: JavaPlugin,
    val player: Player,
    val rooms: List<String>,
    val roomPositions: List<Triple<Int, Int, Int>>, // bottom right corner's z and y position + death plane Y
    val checkpoints: MutableList<Location>,
    val canSkipCPs: Boolean = false,
    val timerDelay: Long = 0L
)