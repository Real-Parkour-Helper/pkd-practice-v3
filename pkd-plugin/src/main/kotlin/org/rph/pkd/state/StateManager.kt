package org.rph.pkd.state

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.rph.core.data.PkdData
import org.rph.core.inventory.hotbar.HotbarAPI
import org.rph.pkd.state.runs.FullRunManager
import org.rph.pkd.state.runs.RoomRunManager
import org.rph.pkd.state.runs.Run
import org.rph.pkd.state.runs.RunManager
import org.rph.pkd.worlds.RoomsWorld
import org.rph.pkd.worlds.RunWorld
import java.io.File

class StateManager(
    private val plugin: JavaPlugin,
    private val player: Player
){

    enum class Mode { LOBBY, ROOMS, RUN, NONE }

    private var currentMode: Mode = Mode.NONE
    private var currentRunManager: RunManager? = null
    private var currentRunWorld: World? = null

    fun tpToLobby(callCleanup: Boolean = true) {
        if (currentMode == Mode.LOBBY) return
        onMainThread {
            if (callCleanup) ensureOldStateCleanup()

            val world = Bukkit.createWorld(WorldCreator("world_lobby"))
                ?: error("Lobby world not found or could not be created")

            val spawn = Location(world, 0.5, 65.0, 0.5)
            ensureBasics(world)
            player.teleport(spawn)

            HotbarAPI.applyLayout(player, "lobbyLayout")

            currentMode = Mode.LOBBY
        }
    }

    fun tpToRoom(roomName: String) {
        onMainThread {
            ensureOldStateCleanup()

            val spawn = RoomsWorld.getSpawnLocation(roomName)
                ?: error("Unknown room: $roomName")
            val corner = RoomsWorld.getRoomCorner(roomName)
                ?: error("Unable to get room corner for $roomName")
            val checkpoints = RoomsWorld.getCheckpoints(roomName)
                ?: error("Unable to get checkpoints for $roomName")
            val asset = PkdData.get(roomName)
                ?: error("Unable to get room asset for $roomName")
            val world = RoomsWorld.getWorld()

            ensureBasics(world)
            currentMode = Mode.ROOMS

            val cp = mutableListOf(spawn)
            cp.addAll(checkpoints)

            val run = Run(
                plugin = plugin,
                player = player,
                rooms = listOf(roomName),
                roomPositions = listOf(Triple(corner.z.toInt(), corner.y.toInt(), asset.meta!!.deathPlane ?: 2)),
                checkpoints = cp
            )

            currentRunManager = RoomRunManager(run)
            currentRunManager!!.start()
        }
    }

    fun tpToNextRoom() {
        if (currentMode != Mode.ROOMS || currentRunManager == null) return
        if (currentRunManager !is RoomRunManager) return

        val currentRoom = currentRunManager!!.currentRun().rooms[0]
        val nextRoom = RoomsWorld.nextRoom(currentRoom) ?: return
        tpToRoom(nextRoom)
    }

    fun tpToPrevRoom() {
        if (currentMode != Mode.ROOMS || currentRunManager == null) return
        if (currentRunManager !is RoomRunManager) return

        val currentRoom = currentRunManager!!.currentRun().rooms[0]
        val prevRoom = RoomsWorld.previousRoom(currentRoom) ?: return
        tpToRoom(prevRoom)
    }

    fun getRunManager(): RunManager? {
        return currentRunManager
    }

    fun tpToRun(rooms: List<String>) {
        ensureOldStateCleanup()

        RunWorld.createRunWorld(plugin, rooms) { run ->
            ensureBasics(run.world)
            currentRunWorld = run.world
            currentMode = Mode.RUN

            val runDataClass = Run(
                plugin = plugin,
                player = player,
                rooms = run.roomPositions.keys.toList(),
                roomPositions = run.roomPositions.values.toList(),
                checkpoints = run.checkpoints
            )

            currentRunManager = FullRunManager(runDataClass)
            currentRunManager!!.start()
        }

    }

    fun ensureOldStateCleanup() {
        if (currentRunManager != null) {
            currentRunManager!!.stop()
        }
        if (currentRunWorld != null) {
            println("Cleaning up world ${currentRunWorld!!.name}...")
            if (player.world.uid == currentRunWorld!!.uid) {
                tpToLobby(false)
            }

            onMainThread {
                Bukkit.unloadWorld(currentRunWorld, false)

                val folder = currentRunWorld!!.worldFolder
                if (folder.exists()) {
                    folder.deleteRecursively()
                }
            }
        }
    }

    private fun ensureBasics(world: World) {
        player.gameMode = GameMode.ADVENTURE
        player.exp = 0F
        player.level = 0

        world.setGameRuleValue("doDaylightCycle", "false")
        world.setGameRuleValue("doWeatherCycle", "false")
        world.setGameRuleValue("doMobSpawning", "false")
        world.setGameRuleValue("randomTickSpeed", "0")
        world.setStorm(false)
        world.time = 6000L
    }

    private fun onMainThread(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().runTask(plugin, action)
        }
    }

}