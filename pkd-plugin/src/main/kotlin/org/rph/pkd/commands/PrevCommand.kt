package org.rph.pkd.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.rph.pkd.PKDPlugin

class PrevCommand(private val plugin: PKDPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be run by a player.")
            return true
        }

        plugin.getStateManager(sender)?.tpToPrevRoom()
        return true
    }
}