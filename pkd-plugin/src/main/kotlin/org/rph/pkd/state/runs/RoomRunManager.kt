package org.rph.pkd.state.runs

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.rph.core.inventory.hotbar.HotbarAPI
import org.rph.core.sound.PkdSounds
import org.rph.pkd.utils.extensions.upperCaseWords

class RoomRunManager(run: Run) : RunManager(run) {

    private val shownBarriers = mutableListOf<Location>()

    fun toggleBarriers() {
        if (shownBarriers.isNotEmpty()) {
            Bukkit.getScheduler().runTaskLater(run.plugin, {
                shownBarriers.forEach { loc ->
                    run.player.sendBlockChange(loc, Material.BARRIER, 0.toByte())
                }
                shownBarriers.clear()
            }, 1L)
        } else {
            val radius = 50
            for (x in (run.player.location.blockX - radius)..(run.player.location.blockX + radius)) {
                for (y in (run.player.location.blockY - radius).coerceAtLeast(0)..(run.player.location.blockY + radius).coerceAtMost(255)) {
                    for (z in (run.player.location.blockZ - radius)..(run.player.location.blockZ + radius)) {
                        val loc = Location(run.world, x.toDouble(), y.toDouble(), z.toDouble())
                        val type = run.world?.getBlockAt(loc)?.type
                        if (type == Material.BARRIER) {
                            // show a visible placeholder (red glass) to this player only
                            run.player.sendBlockChange(loc, Material.WOOL, 14) // 14 = red
                            shownBarriers += loc.clone()
                        }
                    }
                }
            }
        }
    }

    override fun start() {
        startInternal()
    }

    override fun applyLayout() {
        HotbarAPI.applyLayout(run.player, "roomsLayout")
    }

    override fun onCheckpoint(checkpoint: Int) {
        val time = getElapsedTime()
        run.player.sendMessage("§e§lCHECKPOINT!§r §aYou §7reached checkpoint §e$checkpoint §7in §6$time!")
        PkdSounds.playCheckpointSound(run.player)
    }

    override fun onFinished() {
        stopTickTimer()
        val time = getElapsedTime()
        val roomParts = run.rooms[0].split("_").toMutableList()
        roomParts.removeAt(0)
        val room = roomParts.joinToString(" ").upperCaseWords()
        run.player.sendMessage("§e§lCOMPLETED!§r §aYou completed §e$room §ain §6§l$time!")
        PkdSounds.playCheckpointSound(run.player)
        HotbarAPI.applyLayout(run.player, "roomRunOverLayout")
    }

    override fun resetRun() {
        stop()
        startInternal()
    }

    override fun getBoostCooldown(): Int = 0

}