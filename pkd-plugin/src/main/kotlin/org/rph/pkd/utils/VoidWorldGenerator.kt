package org.rph.pkd.utils

import org.bukkit.World
import org.bukkit.generator.ChunkGenerator
import java.util.*

class VoidWorldGenerator : ChunkGenerator() {
    override fun generate(world: World, random: Random, x: Int, z: Int): ByteArray {
        return ByteArray(32768)
    }
}