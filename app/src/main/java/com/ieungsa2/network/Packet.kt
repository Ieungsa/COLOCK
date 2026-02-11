package com.ieungsa2.network

import java.net.InetAddress
import java.nio.ByteBuffer

// IP 헤더의 프로토콜 상수
const val TCP = 6
const val UDP = 17

data class IPHeader(
    val version: Int,
    val headerLength: Int, // in bytes
    val protocol: Int,
    val sourceAddress: InetAddress,
    val destinationAddress: InetAddress
)

data class TCPHeader(
    val sourcePort: Int,
    val destinationPort: Int,
    val isSYN: Boolean,
    val isACK: Boolean,
    val isFIN: Boolean,
    val isRST: Boolean
)

data class UDPHeader(
    val sourcePort: Int,
    val destinationPort: Int,
    val length: Int
)

class Packet(
    val ipHeader: IPHeader,
    val tcpHeader: TCPHeader?,
    val udpHeader: UDPHeader?
) {
    override fun toString(): String {
        val protocolStr = when (ipHeader.protocol) {
            TCP -> "TCP"
            UDP -> "UDP"
            else -> "Unknown"
        }
        val header = tcpHeader ?: udpHeader
        val (srcPort, destPort) = when (header) {
            is TCPHeader -> Pair(header.sourcePort, header.destinationPort)
            is UDPHeader -> Pair(header.sourcePort, header.destinationPort)
            else -> Pair(0, 0)
        }
        return "$protocolStr | ${ipHeader.sourceAddress.hostAddress}:$srcPort -> ${ipHeader.destinationAddress.hostAddress}:$destPort"
    }

    companion object {
        fun create(buffer: ByteBuffer): Packet? {
            if (buffer.remaining() < 20) return null // 최소 IP 헤더 길이

            buffer.rewind()

            val versionAndIhl = buffer.get().toInt()
            val version = versionAndIhl shr 4
            if (version != 4) return null // IPv4만 지원

            val ipHeaderLength = (versionAndIhl and 0x0F) * 4
            if (buffer.remaining() < ipHeaderLength - 1) return null

            buffer.position(9)
            val protocol = buffer.get().toInt() and 0xFF

            val sourceIp = ByteArray(4)
            val destIp = ByteArray(4)
            buffer.position(12)
            buffer.get(sourceIp)
            buffer.get(destIp)
            val sourceAddress = InetAddress.getByAddress(sourceIp)
            val destinationAddress = InetAddress.getByAddress(destIp)

            val ipHeader = IPHeader(version, ipHeaderLength, protocol, sourceAddress, destinationAddress)

            buffer.position(ipHeaderLength)
            var tcpHeader: TCPHeader? = null
            var udpHeader: UDPHeader? = null

            when (protocol) {
                TCP -> {
                    if (buffer.remaining() < 20) return null // 최소 TCP 헤더 길이
                    val srcPort = buffer.getShort().toInt() and 0xFFFF
                    val destPort = buffer.getShort().toInt() and 0xFFFF
                    buffer.position(buffer.position() + 8) // Seq/Ack 번호 스킵
                    val flags = buffer.get(buffer.position() + 1).toInt() and 0xFF
                    tcpHeader = TCPHeader(
                        sourcePort = srcPort,
                        destinationPort = destPort,
                        isSYN = (flags and 0x02) != 0,
                        isACK = (flags and 0x10) != 0,
                        isFIN = (flags and 0x01) != 0,
                        isRST = (flags and 0x04) != 0
                    )
                }
                UDP -> {
                    if (buffer.remaining() < 8) return null // UDP 헤더 길이
                    val srcPort = buffer.getShort().toInt() and 0xFFFF
                    val destPort = buffer.getShort().toInt() and 0xFFFF
                    val length = buffer.getShort().toInt() and 0xFFFF
                    udpHeader = UDPHeader(srcPort, destPort, length)
                }
            }
            return Packet(ipHeader, tcpHeader, udpHeader)
        }
    }
}
