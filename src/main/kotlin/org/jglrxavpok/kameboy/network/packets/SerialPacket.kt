package org.jglrxavpok.kameboy.network.packets

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import org.jglrxavpok.kameboy.EmulatorCore
import org.jglrxavpok.kameboy.KameboyCore.Companion.CoreInstance
import org.jglrxavpok.kameboy.helpful.asUnsigned
import org.jglrxavpok.kameboy.helpful.asUnsigned8
import org.jglrxavpok.kameboy.network.AbstractPacket
import org.jglrxavpok.kameboy.network.INetworkHandler
import org.jglrxavpok.kameboy.network.PacketHandler

class SerialPacket(var byte: Int): AbstractPacket() {

    internal constructor(): this(0)

    override fun decodeFrom(buffer: ByteBuf) {
        byte = buffer.readInt()
    }

    override fun encodeInto(buffer: ByteBuf) {
        buffer.writeInt(byte)
    }

    object Handler: PacketHandler<SerialPacket> {
        override fun handlePacket(packet: SerialPacket, ctx: ChannelHandlerContext, netHandler: INetworkHandler) {
            val core = CoreInstance.core
            val serialIO = core.gameboy.mapper.serialIO
            serialIO.receive(packet.byte.asUnsigned8())
            val type = if(serialIO.isInternalClock) "Master" else "Slave"
            println(">> ($type) Received ${packet.byte}")
        }
    }
}