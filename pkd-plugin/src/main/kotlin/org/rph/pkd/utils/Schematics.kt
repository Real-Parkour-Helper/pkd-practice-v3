package org.rph.pkd.utils

import com.sk89q.worldedit.Vector
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitWorld
import com.sk89q.worldedit.schematic.SchematicFormat
import org.bukkit.Location
import org.bukkit.World
import java.io.File

object Schematics {

    fun pasteSchematic(schematic: File, world: World, at: Location, ignoreAir: Boolean = true) {
        try {
            val format = SchematicFormat.getFormat(schematic)
                ?: error("Unsupported schematic format for file: ${schematic.name}")

            val clipboard = format.load(schematic)

            val weWorld = BukkitWorld(world)
            val editSession = WorldEdit.getInstance()
                .editSessionFactory
                .getEditSession(weWorld, -1) // -1 = unlimited blocks

            // Faster & fewer physics
            editSession.setFastMode(true)

            val to = Vector(at.blockX, at.blockY, at.blockZ)
            clipboard.paste(editSession, to, ignoreAir)
            editSession.flushQueue()
        } catch (e: Exception) {
            e.printStackTrace()
            error("Failed to paste schematic: ${e.message}")
        }
    }

}