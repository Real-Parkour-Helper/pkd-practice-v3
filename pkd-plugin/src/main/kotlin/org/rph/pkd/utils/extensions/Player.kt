package org.rph.pkd.utils.extensions

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.wrappers.WrappedChatComponent
import org.bukkit.entity.Player

fun Player.sendActionBar(message: String) {
    val protocolManager = ProtocolLibrary.getProtocolManager()

    val packet = protocolManager.createPacket(PacketType.Play.Server.CHAT)
    packet.chatComponents.write(0, WrappedChatComponent.fromText(message))
    packet.bytes.write(0, 2.toByte())

    try {
        protocolManager.sendServerPacket(player, packet)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}