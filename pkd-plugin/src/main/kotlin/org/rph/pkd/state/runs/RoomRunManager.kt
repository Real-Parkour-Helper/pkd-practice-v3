package org.rph.pkd.state.runs

import org.rph.core.inventory.hotbar.HotbarAPI
import org.rph.core.sound.PkdSounds
import org.rph.pkd.utils.extensions.upperCaseWords

class RoomRunManager(run: Run) : RunManager(run) {

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

}