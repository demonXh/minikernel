package org.minikernel.kernel.net;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Minimal UDP datagram encode/decode. 8-byte header:
 * <pre>
 *   [srcPort 16][dstPort 16][length 16][checksum 16][payload ...]
 * </pre>
 *
 * <p>We compute the checksum over the standard pseudo-header for correctness
 * but accept packets that arrive with checksum=0 (Linux semantics: optional
 * for IPv4).
 */
public final class UdpDatagram {

    public static final int HEADER_LEN = 8;

    private UdpDatagram() {}

    public static byte[] encode(int srcIp, int dstIp, int srcPort, int dstPort, byte[] payload) {
        int totalLen = HEADER_LEN + payload.length;
        ByteBuffer bb = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN);
        bb.putShort((short) srcPort);
        bb.putShort((short) dstPort);
        bb.putShort((short) totalLen);
        bb.putShort((short) 0); // checksum placeholder
        bb.put(payload);
        byte[] out = bb.array();
        int csum = pseudoChecksum(srcIp, dstIp, out);
        out[6] = (byte) ((csum >>> 8) & 0xff);
        out[7] = (byte) (csum & 0xff);
        return out;
    }

    public static void decode(SkBuff skb) {
        byte[] d = skb.rawData();
        int off = skb.readPos();
        if (d.length - off < HEADER_LEN) throw new IllegalArgumentException("UDP too short");
        skb.srcPort = ((d[off] & 0xff) << 8) | (d[off + 1] & 0xff);
        skb.dstPort = ((d[off + 2] & 0xff) << 8) | (d[off + 3] & 0xff);
        skb.advance(HEADER_LEN);
    }

    private static int pseudoChecksum(int srcIp, int dstIp, byte[] udp) {
        ByteBuffer pseudo = ByteBuffer.allocate(12 + udp.length).order(ByteOrder.BIG_ENDIAN);
        pseudo.putInt(srcIp);
        pseudo.putInt(dstIp);
        pseudo.put((byte) 0);
        pseudo.put((byte) IpPacket.PROTO_UDP);
        pseudo.putShort((short) udp.length);
        pseudo.put(udp);
        return IpPacket.checksum(pseudo.array(), 0, pseudo.position());
    }
}
