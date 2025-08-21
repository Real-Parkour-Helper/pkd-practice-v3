package org.rph.pkd.state.runs

import org.bukkit.Bukkit
import org.rph.core.boost.BoostManager
import org.rph.core.checkpoints.CheckpointTracker
import org.rph.core.checkpoints.TickTimer
import org.rph.core.sound.PkdSounds
import org.rph.pkd.utils.extensions.sendActionBar

abstract class RunManager(protected val run: Run) {

    private var checkpointTracker: CheckpointTracker? = null
    private var boostManager: BoostManager? = null
    private var tickTimer = TickTimer()
    private var tickTask: Int? = null

    private var tickTimerDelayTicks = 0

    protected fun startInternal() {
        checkpointTracker = CheckpointTracker(run.checkpoints, listOf(run.player), run.canSkipCPs, {_, cp -> onCheckpoint(cp)}, { onFinished() })
        checkpointTracker!!.resetToCheckpoint(run.player)

        boostManager = BoostManager(run.player.uniqueId) { 10 }

        applyLayout()

        tickTimer.start()
        tickTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(run.plugin, {
            tick()
        }, 0L, 1L)
    }

    open fun stop() {
        if (tickTask == null) return
        tickTimer.stop()
        tickTimer.reset()
        checkpointTracker?.resetTickCounter()
        boostManager?.stopCooldown()
        Bukkit.getScheduler().cancelTask(tickTask!!)
    }

    fun resetToCheckpoint() {
        if (checkpointTracker == null) return

        PkdSounds.playResetSound(run.player)

        if (checkpointTracker!!.getCheckpoint(run.player) == 0) {
            resetRun()
        }

        checkpointTracker!!.resetToCheckpoint(run.player)
    }

    fun currentRun() = run
    fun currentBoostManager() = boostManager

    private fun tick() {
        if (tickTimerDelayTicks < run.timerDelay) {
            tickTimerDelayTicks++
        } else {
            tickTimer.tick()

            val elapsedTime = getElapsedTime()
            run.player.sendActionBar("§b§l$elapsedTime")
        }

        checkpointTracker?.tick()

        if (checkDeathPlane()) {
            resetToCheckpoint()
        }
    }

    private fun checkDeathPlane(): Boolean {
        val zPos = run.player.location.z

        var currentRoom: Triple<Int, Int, Int>? = null
        for (roomPos in run.roomPositions) {
            if (zPos >= roomPos.first) currentRoom = roomPos
            else break
        }

        if (currentRoom == null) return false

        return run.player.location.y <= (currentRoom.second + currentRoom.third)
    }

    protected fun getElapsedTime(): String {
        return tickTimer.getElapsedTimeString()
    }

    protected fun stopTickTimer() {
        tickTimer.stop()
    }

    /** This can be used to edit checkpoints e.g. after pre-game lobby waiting */
    abstract fun start()
    abstract fun applyLayout()
    abstract fun onCheckpoint(checkpoint: Int)
    abstract fun onFinished()
    abstract fun resetRun()
}