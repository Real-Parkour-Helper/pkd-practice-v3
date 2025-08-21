package org.rph.pkd.state.runs

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.github.paperspigot.Title
import org.rph.core.inventory.hotbar.HotbarAPI
import org.rph.core.sound.PkdSounds

class FullRunManager(run: Run) : RunManager(run) {

    private var countdownTask: BukkitTask? = null

    override fun start() {
        var countdown = 10 // TODO: get from config

        val preGameSpawn = run.checkpoints[0]
        run.player.teleport(preGameSpawn)
        run.checkpoints.removeAt(0)

        countdownTask = object : BukkitRunnable() {
            override fun run() {
                if (countdown > 0) {
                    val chatColor = when (countdown) {
                        in 11..15 -> "§b" // Cyan for 11-15 seconds
                        in 6..10 -> "§6" // Gold for 6-10 seconds
                        in 1..5 -> "§c" // Red for 1-5 seconds
                        else -> "§e" // Yellow for everything else seconds
                    }
                    run.player.sendMessage("§eThe game starts in ${chatColor}${countdown}§e seconds!")

                    if (countdown == 10 || countdown in 1..5) {
                        val titleColor = when (countdown) {
                            10 -> "§a" // Green for 10 seconds
                            in 4..5 -> "§e" // Yellow for 4-5 seconds
                            in 1..3 -> "§c" // Red for 1-3 seconds
                            else -> "" // This doesn't happen
                        }
                        run.player.sendTitle(Title("${titleColor}${countdown}", "", 0, 20, 0))
                        PkdSounds.playTimerCountdownSound(run.player)
                    }

                    countdown--
                } else {
                    cancel()
                    startInternal()
                }
            }
        }.runTaskTimer(run.plugin, 0L, 20L)
    }

    override fun stop() {
        countdownTask?.cancel()
        super.stop()
    }

    override fun applyLayout() {
        HotbarAPI.applyLayout(run.player, "fullRunLayout")
    }

    override fun onCheckpoint(checkpoint: Int) {
        val time = getElapsedTime()
        run.player.sendMessage("§e§lCHECKPOINT!§r §aYou §7reached checkpoint §e$checkpoint §7in §6$time!")
        PkdSounds.playCheckpointSound(run.player)
    }

    override fun onFinished() {
        stopTickTimer()
        val time = getElapsedTime()
        run.player.sendMessage("§e§lCOMPLETED!§r §aYou completed the parkour in §6§l$time!")
        PkdSounds.playCheckpointSound(run.player)
        HotbarAPI.applyLayout(run.player, "roomRunOverLayout")
    }

    override fun resetRun() {
        stop()
        startInternal()
    }

}