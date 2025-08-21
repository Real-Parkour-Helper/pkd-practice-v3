package org.rph.pkd.state.runs

import org.bukkit.Bukkit
import org.rph.core.checkpoints.CheckpointTracker
import org.rph.core.checkpoints.TickTimer
import org.rph.core.sound.PkdSounds
import org.rph.pkd.utils.extensions.sendActionBar

abstract class RunManager(protected val run: Run) {

    private var checkpointTracker: CheckpointTracker? = null
    private var tickTimer = TickTimer()
    private var tickTask: Int? = null

    private var tickTimerDelayTicks = 0

    protected fun startInternal() {
        checkpointTracker = CheckpointTracker(run.checkpoints, listOf(run.player), run.canSkipCPs, {_, cp -> onCheckpoint(cp)}, { onFinished() })
        checkpointTracker!!.resetToCheckpoint(run.player)
        applyLayout()

        tickTimer.start()
        tickTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(run.plugin, {
            tick()
        }, 0L, 1L)
    }

    fun stop() {
        if (tickTask == null) return
        tickTimer.stop()
        tickTimer.reset()
        Bukkit.getScheduler().cancelTask(tickTask!!)
    }

    fun resetToCheckpoint() {
        if (checkpointTracker == null) return

        checkpointTracker!!.resetToCheckpoint(run.player)
        if (checkpointTracker!!.getCheckpoint(run.player) == 0) {
            tickTimer.reset()
        }
    }

    fun boost() {
        // TODO
    }

    private fun tick() {
        checkpointTracker?.tick()

        if (tickTimerDelayTicks < run.timerDelay) {
            tickTimerDelayTicks++
        } else {
            tickTimer.tick()

            val elapsedTime = getElapsedTime()
            run.player.sendActionBar("§b§l$elapsedTime")
        }

        if (checkDeathPlane()) {
            resetToCheckpoint()
            PkdSounds.playResetSound(run.player)
        }
    }

    private fun checkDeathPlane(): Boolean {
        val zPos = run.player.location.z

        var currentRoom: Pair<Int, Int>? = null
        for (roomPos in run.roomPositions) {
            if (zPos >= roomPos.first) currentRoom = roomPos
            else break
        }

        if (currentRoom == null) return false

        return run.player.location.y <= (currentRoom.second + 4)
    }

    protected fun getElapsedTime(): String {
        return tickTimer.getElapsedTimeString()
    }

    /** This can be used to edit checkpoints e.g. after pre-game lobby waiting */
    abstract fun start()
    abstract fun applyLayout()
    abstract fun onCheckpoint(checkpoint: Int)
    abstract fun onFinished()
    abstract fun resetRun()
}