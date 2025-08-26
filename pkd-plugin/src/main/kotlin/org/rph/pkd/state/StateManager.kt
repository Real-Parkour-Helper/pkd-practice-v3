package org.rph.pkd.state

import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.rph.core.data.PkdData
import org.rph.core.inventory.hotbar.HotbarAPI
import org.rph.pkd.PKDPlugin
import org.rph.pkd.state.runs.FullRunManager
import org.rph.pkd.state.runs.RoomRunManager
import org.rph.pkd.state.runs.Run
import org.rph.pkd.state.runs.RunManager
import org.rph.pkd.worlds.RoomsWorld
import org.rph.pkd.worlds.RunWorld

class StateManager(
    private val plugin: PKDPlugin,
    private val player: Player
){

    enum class Mode { LOBBY, ROOMS, RUN, NONE }

    private var currentMode: Mode = Mode.NONE
    private var currentRunManager: RunManager? = null
    private var currentRunWorld: World? = null

    fun getCurrentMode() = currentMode

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
                world = world,
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
                world = run.world,
                checkpoints = run.checkpoints,
                doorPositions = run.doors,
                dropDoorsAt = run.dropDoorsAt,
                timerDelay = 20L
            )

            currentRunManager = FullRunManager(runDataClass)
            currentRunManager!!.start()
        }

    }

    fun startRun(map: String = "all", roomCount: Int = 8) {
        val rooms = mutableListOf<String>()
        if (map == "all") {
            rooms.addAll(PkdData.rooms())
        } else if (map.contains("|")) {
            map.split("|").map { it.lowercase() }.forEach { m ->
                val r = PkdData.rooms(m)
                if (r.isEmpty()) {
                    player.sendMessage("${ChatColor.RED}No rooms found for map '${ChatColor.GRAY}$m${ChatColor.RED}'.")
                } else {
                    rooms.addAll(r)
                }
            }
        } else {
            val r = PkdData.rooms(map)
            if (r.isEmpty()) {
                player.sendMessage("${ChatColor.RED}No rooms found for map '${ChatColor.GRAY}$map${ChatColor.RED}'.")
            } else {
                rooms.addAll(r)
            }
        }

        val pregame = rooms.filter { "pregame" in it }.random()
        val start = rooms.filter { "start" in it }.random()

        val middleRooms = rooms.filter { "start" !in it && "end" !in it && "pregame" !in it }
        val roomsToUse = pickRandom(middleRooms, roomCount)

        val end = rooms.filter { "end" in it }.random()

        val run = mutableListOf(pregame, start)
        run.addAll(roomsToUse)
        run.add(end)
        tpToRun(run)
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

    private fun <T> pickRandom(list: List<T>, n: Int): List<T> {
        if (list.isEmpty()) return emptyList()
        return if (n <= list.size) {
            list.shuffled().take(n)
        } else {
            List(n) { list.random() }
        }
    }

    private fun ensureBasics(world: World) {
        player.gameMode = GameMode.ADVENTURE
        player.addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, Int.MAX_VALUE, 0))
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