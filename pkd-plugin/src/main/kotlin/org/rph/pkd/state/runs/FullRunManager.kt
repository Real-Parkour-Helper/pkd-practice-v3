package org.rph.pkd.state.runs

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.github.paperspigot.Title
import org.rph.core.inventory.hotbar.HotbarAPI
import org.rph.core.sound.PkdSounds

class FullRunManager(run: Run) : RunManager(run) {

    private var countdownTask: BukkitTask? = null
    private var resetDoorBlocks = mutableListOf<MutableList<TempBlock>>()
    private var originalDoorPositions: MutableList<Pair<Location, Location>> = mutableListOf()
    private var originalDropDoorsAt: MutableList<Int> = mutableListOf()

    override fun start() {
        var countdown = 10 // TODO: get from config

        val preGameSpawn = run.checkpoints[0]
        run.player.teleport(preGameSpawn)
        run.checkpoints.removeAt(0)

        run.doorPositions?.forEach { originalDoorPositions.add(it) }
        run.dropDoorsAt?.forEach { originalDropDoorsAt.add(it) }

        println("world: ${run.world}")

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

    override fun tick() {
        super.tick()

        if (run.dropDoorsAt.isNullOrEmpty() || run.doorPositions.isNullOrEmpty()) return

        val currentCheckpoint = getCurrentCheckpoint()
        if (currentCheckpoint == run.dropDoorsAt[0]) {
            run.dropDoorsAt.removeAt(0)
            val doorPair = run.doorPositions[0]
            println("doorPair: $doorPair")
            run.doorPositions.removeAt(0)
            if (doorPair != null) {
                println("dropping 1")
                removeDoor(doorPair.first)
                println("dropping 2")
                removeDoor(doorPair.second)
            }
        }
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
        Bukkit.getScheduler().runTask(run.plugin) {
            stop()
            if (run.world != null) {
                resetDoorBlocks.forEach {
                    it.forEach { block ->
                        run.world.getBlockAt(
                            Location(
                                run.world,
                                block.x.toDouble(),
                                block.y.toDouble(),
                                block.z.toDouble()
                            )
                        ).setTypeIdAndData(block.id, block.data, false)
                    }
                }
            }
            resetDoorBlocks.clear()

            run.doorPositions?.clear()
            run.dropDoorsAt?.clear()
            originalDoorPositions.forEach { run.doorPositions?.add(it) }
            originalDropDoorsAt.forEach { run.dropDoorsAt?.add(it) }

            startInternal()
        }
    }

    private data class TempBlock(
        val x: Int,
        val y: Int,
        val z: Int,
        val id: Int,
        val data: Byte
    )

    private fun removeDoor(bottomRight: Location) {
        println("dropping door at $bottomRight (${run.world})")
        if (run.world == null) return

        val tempBlocks = mutableListOf<TempBlock>()
        for (dx in 0..4) {
            for (dy in 0..4) {
                val x = (bottomRight.x + dx).toInt()
                val y = (bottomRight.y + dy).toInt()
                val z = bottomRight.z.toInt()
                val block = run.world.getBlockAt(x, y, z)
                tempBlocks.add(
                    TempBlock(x, y, z, block.typeId, block.data)
                )
            }
        }

        for (dx in 0..4) {
            for (dy in 0..4) {
                val x = (bottomRight.x + dx).toInt()
                val y = (bottomRight.y + dy).toInt()
                val z = bottomRight.z.toInt()

                val block = run.world.getBlockAt(x, y, z)
                block.type = Material.AIR
            }
        }

        resetDoorBlocks.add(tempBlocks)
    }

}