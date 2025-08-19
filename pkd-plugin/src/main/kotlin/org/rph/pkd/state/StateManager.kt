package org.rph.pkd.state

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.rph.core.inventory.hotbar.HotbarAPI
import org.rph.pkd.worlds.RoomsWorld

class StateManager(
    private val plugin: JavaPlugin,
    private val player: Player
){

    enum class Mode { LOBBY, ROOMS, RUN, NONE }

    private var currentMode: Mode = Mode.NONE

    fun tpToLobby() {
        onMainThread {
            val world = Bukkit.createWorld(WorldCreator("world_lobby"))
                ?: error("Lobby world not found or could not be created")
            val spawn = Location(world, 0.5, 65.0, 0.5)
            player.gameMode = GameMode.ADVENTURE
            player.teleport(spawn)

            HotbarAPI.applyLayout(player, "lobbyLayout")

            currentMode = Mode.LOBBY
        }
    }

    fun tpToRoom(roomName: String) {
        onMainThread {
            val spawn = RoomsWorld.getSpawnLocation(roomName)
                ?: error("Unknown room: $roomName")
            player.gameMode = GameMode.ADVENTURE
            player.teleport(spawn)

            HotbarAPI.applyLayout(player, "roomsLayout")

            currentMode = Mode.ROOMS
        }
    }

    private fun onMainThread(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().runTask(plugin, action)
        }
    }

}