package org.rph.pkd.state

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.rph.pkd.worlds.RoomsWorld

object StateManager {

    enum class Mode { LOBBY, ROOMS, RUN }

    lateinit var plugin: JavaPlugin

    fun modeOf(p: Player): Mode =
        when {
            p.world.name == "world_rooms" -> Mode.ROOMS
            p.world.name.startsWith("pkd_run_") -> Mode.RUN
            else -> Mode.LOBBY
        }

    fun tpToLobby(p: Player) {
        onMainThread {
            val world = Bukkit.createWorld(WorldCreator("world_lobby"))
                ?: error("Lobby world not found or could not be created")
            val spawn = Location(world, 0.5, 65.0, 0.5)
            p.gameMode = GameMode.ADVENTURE
            p.teleport(spawn)
        }
    }

    fun tpToRoom(p: Player, roomName: String) {
        onMainThread {
            val spawn = RoomsWorld.getSpawnLocation(roomName)
                ?: error("Unknown room: $roomName")
            p.gameMode = GameMode.ADVENTURE
            p.teleport(spawn)
        }
    }

    private fun onMainThread(action: () -> Unit) {
        if (!::plugin.isInitialized) error("StateManager not initialized with plugin instance")

        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().runTask(plugin, action)
        }
    }

}