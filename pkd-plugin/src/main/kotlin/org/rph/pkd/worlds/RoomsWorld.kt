package org.rph.pkd.worlds

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.util.Vector
import org.rph.core.data.PkdData
import org.rph.pkd.utils.Schematics
import org.rph.pkd.utils.VoidWorldGenerator
import java.io.File

object RoomsWorld {

    private val gson = Gson()

    private val roomsPerRow = 20
    private val worldName = "world_rooms"
    private val versionMarker = "roomsVersion.txt"

    private val floorY = 64
    private val roomLocations = mutableMapOf<String, Vector>()

    fun init() {
        val folder = File(Bukkit.getWorldContainer(), worldName)

        if (!isWorldUpToDateOnDisk(folder)) {
            Bukkit.getWorld(worldName)?.let { Bukkit.unloadWorld(it, false) }
            if (folder.exists()) folder.deleteRecursively()
            buildWorld()
        } else {
            loadVoidWorld()
            loadRoomLocationsFromDisk(folder)
        }
    }

    private fun loadRoomLocationsFromDisk(folder: File) {
        val roomsFile = File(folder, "room_locations.json")
        val json = roomsFile.readText()
        val type = object : TypeToken<MutableMap<String, Vector>>() {}.type
        val loaded: MutableMap<String, Vector> = gson.fromJson(json, type)
        roomLocations.clear()
        loaded.forEach { entry ->
            roomLocations[entry.key] = entry.value
        }
    }

    private fun loadVoidWorld(): World {
        return WorldCreator(worldName)
            .environment(World.Environment.NORMAL)
            .generateStructures(false)
            .generator(VoidWorldGenerator())
            .createWorld() ?: error("Failed to load rooms world")
    }

    fun getWorld(): World {
        return Bukkit.getWorld(worldName) ?: error("Rooms world not found")
    }

    fun getSpawnLocation(roomName: String): Location? {
        val roomCorner = roomLocations[roomName] ?: return null
        val asset = PkdData.get(roomName) ?: return null
        val frontDoor = asset.meta!!.frontDoor
        return Location(getWorld(), roomCorner.x + frontDoor.x + 3, roomCorner.y + frontDoor.y + 1, roomCorner.z + frontDoor.z + 2, 0f, 0f)
    }

    fun getRoomCorner(roomName: String): Location? {
        val roomCorner = roomLocations[roomName] ?: return null
        return Location(getWorld(), roomCorner.x, roomCorner.y, roomCorner.z, 0f, 0f)
    }

    fun getCheckpoints(roomName: String): MutableList<Location>? {
        val asset = PkdData.get(roomName) ?: return null
        val corner = getRoomCorner(roomName) ?: return null

        val world = getWorld()
        val checkpoints = mutableListOf<Location>()

        asset.meta!!.checkpoints.forEach {
            checkpoints.add(Location(world, corner.x + it.x, corner.y + it.y, corner.z + it.z))
        }

        return checkpoints
    }

    fun nextRoom(roomName: String): String? {
        val rooms = getAlphabeticalRooms()
        val index = rooms.indexOfFirst { it == roomName }
        if (index == -1 || rooms.isEmpty()) return null
        val nextIndex = (index + 1) % rooms.size
        return rooms[nextIndex]
    }

    fun previousRoom(roomName: String): String? {
        val rooms = getAlphabeticalRooms()
        val index = rooms.indexOfFirst { it == roomName }
        if (index == -1 || rooms.isEmpty()) return null
        val prevIndex = if (index == 0) rooms.size - 1 else index - 1
        return rooms[prevIndex]
    }

    private fun getAlphabeticalRooms(): List<String> = roomLocations.keys.toList().sorted()

    private fun isWorldUpToDateOnDisk(folder: File): Boolean {
        if (!folder.exists()) return false

        val versionFile = File(folder, versionMarker)
        if (!versionFile.exists() || versionFile.readText() != PkdData.where()) return false

        val roomsFile = File(folder, "room_locations.json")
        return roomsFile.exists()
    }

    private fun buildWorld() {
        val w = loadVoidWorld()

        w.setGameRuleValue("doDaylightCycle", "false")
        w.setGameRuleValue("doWeatherCycle", "false")
        w.setGameRuleValue("doMobSpawning", "false")
        w.setGameRuleValue("randomTickSpeed", "0")
        w.setStorm(false)
        w.time = 6000L

        roomLocations.clear()

        val allRooms = PkdData.rooms("All")
            .filter { "start" !in it && "end" !in it && "pregame" !in it }

        val rows = mutableListOf<List<String>>()
        for (i in allRooms.indices step roomsPerRow) {
            rows += allRooms.subList(i, minOf(i + roomsPerRow, allRooms.size))
        }

        val pasteJobs = mutableListOf<Schematics.PasteJob>()

        var rowZ = 0.0
        for (row in rows) {
            var roomX = 0.0
            var maxLength = 0

            for (room in row) {
                val asset = PkdData.get(room) ?: continue
                val roomCorner = Vector(
                    roomX, floorY.toDouble(), rowZ
                )
                roomLocations[room] = roomCorner

                pasteJobs += Schematics.PasteJob(
                    asset.schem.toFile(),
                    Location(w, roomX, floorY.toDouble(), rowZ),
                    ignoreAir = true
                )

                roomX += asset.meta!!.width
                maxLength = maxOf(maxLength, asset.meta!!.length)
            }

            rowZ += maxLength
        }

        Schematics.pasteBatch(w, pasteJobs)

        val versionFile = w.worldFolder.resolve(versionMarker)
        versionFile.writeText(PkdData.where())
        val roomLocationsFile = w.worldFolder.resolve("room_locations.json")
        roomLocationsFile.writeText(gson.toJson(roomLocations))
    }

}