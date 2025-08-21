package org.rph.pkd.commands

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.rph.core.data.PkdData
import org.rph.pkd.PKDPlugin

class RoomsCommand(private val plugin: PKDPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be run by a player.")
            return true
        }

        if (args.isEmpty()) {
            plugin.getStateManager(sender)?.tpToRoom("atlantis_1a")
        } else {
            val roomName = args.joinToString(" ").lowercase().replace(" ", "_")
            val foundRooms = PkdData.find(roomName)
            if (foundRooms.size == 0) {
                sender.sendMessage("${ChatColor.RED}No rooms matching ${ChatColor.GRAY}$roomName${ChatColor.RED} were found.")
            } else {
                val firstFoundRoom = foundRooms[0]
                val newRoom = firstFoundRoom.substringAfter('_', "")
                if (newRoom == roomName) {
                    plugin.getStateManager(sender)?.tpToRoom(firstFoundRoom)
                } else {
                    sender.sendMessage("${ChatColor.RED}No exact match for ${ChatColor.GRAY}$roomName${ChatColor.RED} was found, do you mean one of these:")
                    val txt = foundRooms.joinToString("${ChatColor.RED}, ") { "${ChatColor.GRAY}${it.substringAfter('_', "")}" }
                    sender.sendMessage(txt)
                }
            }
        }

        return true
    }
}