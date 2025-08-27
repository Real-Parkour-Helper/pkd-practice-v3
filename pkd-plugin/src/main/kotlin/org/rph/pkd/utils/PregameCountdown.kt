package org.rph.pkd.utils

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.github.paperspigot.Title
import org.rph.core.sound.PkdSounds

class PregameCountdown(
    private val plugin: JavaPlugin,
    private val player: Player,
    private val secondsTotal: Int,
    private val onFinish: () -> Unit
) : BukkitRunnable() {

    private val startMono = System.nanoTime()
    private var lastShown = Int.MAX_VALUE

    override fun run() {
        val elapsedSec = ((System.nanoTime() - startMono) / 1_000_000_000L).toInt()
        val remaining = (secondsTotal - elapsedSec).coerceAtLeast(0)

        if (remaining != lastShown) {
            lastShown = remaining

            val chatColor = when (remaining) {
                in 11..15 -> "§b"
                in 6..10 -> "§6"
                in 1..5 -> "§c"
                else -> "§e"
            }
            player.sendMessage("§eThe game starts in ${chatColor}${remaining}§e seconds!")

            if (remaining == 10 || remaining in 1..5) {
                val titleColor = when (remaining) {
                    10 -> "§a"
                    in 4..5 -> "§e"
                    in 1..3 -> "§c"
                    else -> ""
                }
                player.sendTitle(Title("${titleColor}${remaining}", "", 0, 20, 0))
                PkdSounds.playTimerCountdownSound(player)
            }

            if (remaining == 0) {
                cancel()
                // call your startInternal() here (or via a callback)
                onFinish.invoke()
            }
        }
    }
}