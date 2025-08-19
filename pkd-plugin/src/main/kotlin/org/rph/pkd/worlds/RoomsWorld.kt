package org.rph.pkd.worlds

import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.rph.core.data.PkdData
import org.rph.pkd.utils.Schematics
import org.rph.pkd.utils.VoidWorldGenerator
import java.io.File

object RoomsWorld {

    private data class SpawnLocation(
        val roomName: String,
        val x: Double,
        val y: Double,
        val z: Double
    )

    private val gson = Gson()

    private val roomsPerRow = 20
    private val worldName = "world_rooms"
    private val versionMarker = "roomsVersion.txt"

    private val floorY = 64
    private val spawnLocations = mutableListOf<SpawnLocation>()

    fun init() {
        val folder = File(Bukkit.getWorldContainer(), worldName)

        if (!isWorldUpToDateOnDisk(folder)) {
            Bukkit.getWorld(worldName)?.let { Bukkit.unloadWorld(it, false) }
            if (folder.exists()) folder.deleteRecursively()
            buildWorld()
        } else {
            loadVoidWorld()
            loadSpawnLocationsFromDisk(folder)
        }
    }

    private fun loadSpawnLocationsFromDisk(folder: File) {
        val spawnsFile = File(folder, "spawn_locations.json")
        val json = spawnsFile.readText()
        val loaded = gson.fromJson(json, Array<SpawnLocation>::class.java).toList()
        spawnLocations.clear()
        spawnLocations.addAll(loaded)
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
        val spawn = spawnLocations.find { it.roomName == roomName } ?: return null
        return Location(getWorld(), spawn.x, spawn.y, spawn.z, 0f, 0f)
    }

    fun nextRoom(roomName: String): String? {
        val index = spawnLocations.indexOfFirst { it.roomName == roomName }
        if (index == -1 || spawnLocations.isEmpty()) return null
        val nextIndex = (index + 1) % spawnLocations.size
        return spawnLocations[nextIndex].roomName
    }

    fun previousRoom(roomName: String): String? {
        val index = spawnLocations.indexOfFirst { it.roomName == roomName }
        if (index == -1 || spawnLocations.isEmpty()) return null
        val prevIndex = if (index == 0) spawnLocations.size - 1 else index - 1
        return spawnLocations[prevIndex].roomName
    }

    private fun isWorldUpToDateOnDisk(folder: File): Boolean {
        if (!folder.exists()) return false

        val versionFile = File(folder, versionMarker)
        if (!versionFile.exists() || versionFile.readText() != PkdData.where()) return false

        val spawnsFile = File(folder, "spawn_locations.json")
        return spawnsFile.exists()
    }

    private fun buildWorld() {
        val w = loadVoidWorld()

        w.setGameRuleValue("doDaylightCycle", "false")
        w.setGameRuleValue("doWeatherCycle", "false")
        w.setGameRuleValue("doMobSpawning", "false")
        w.setGameRuleValue("randomTickSpeed", "0")
        w.setStorm(false)
        w.time = 6000L

        spawnLocations.clear()

        val allRooms = PkdData.rooms("All")
            .filter { "start" !in it && "end" !in it && "pregame" !in it }

        val rows = mutableListOf<List<String>>()
        for (i in allRooms.indices step roomsPerRow) {
            rows += allRooms.subList(i, minOf(i + roomsPerRow, allRooms.size))
        }

        var rowZ = 0.0
        for (row in rows) {
            var roomX = 0.0
            var maxLength = 0

            for (room in row) {
                val asset = PkdData.get(room) ?: continue
                val spawn = SpawnLocation(
                    room,
                    roomX + asset.meta!!.frontDoor.x + 3,
                    floorY.toDouble() + asset.meta!!.frontDoor.y + 1,
                    rowZ + asset.meta!!.frontDoor.z + 2
                )
                spawnLocations += spawn

                Schematics.pasteSchematic(
                    asset.schem.toFile(),
                    w,
                    Location(w, roomX, floorY.toDouble(), rowZ)
                )

                roomX += asset.meta!!.width
                maxLength = maxOf(maxLength, asset.meta!!.length)
            }

            rowZ += maxLength
        }

        val versionFile = w.worldFolder.resolve(versionMarker)
        versionFile.writeText(PkdData.where())
        val spawnLocationsFile = w.worldFolder.resolve("spawn_locations.json")
        spawnLocationsFile.writeText(gson.toJson(spawnLocations))
    }

}