package org.rph.pkd.commands

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.rph.core.data.PkdData
import org.rph.pkd.PKDPlugin

class RunCommand(private val plugin: PKDPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be run by a player.")
            return true
        }

        val map = if (args.isEmpty()) "all" else args[0]
        plugin.getStateManager(sender)?.startRun(map, args.getOrNull(1)?.toInt() ?: 8)

        return true
    }
}