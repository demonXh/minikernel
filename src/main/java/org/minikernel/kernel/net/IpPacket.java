package org.minikernel.kernel.net;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Minimal IPv4 header encode/decode. We support only the fixed 20-byte
 * header (no options) and the protocols we care about.
 *
 * <pre>
 *   0      4      8         16                            31
 *   +------+------+---------+------------------------------+
 *   |Vers=4| IHL=5|   TOS   |          TotalLength         |
 *   +------+------+---------+------------------------------+
 *   |          ID           |  Flags |    FragOffset       |
 *   +-----------------------+--------+---------------------+
 *   |     TTL    | Protocol |          HeaderChecksum      |
 *   +------------+----------+------------------------------+
 *   |                       SrcAddr                         |
 *   +-------------------------------------------------------+
 *   |                       DstAddr                         |
 *   +-------------------------------------------------------+
 * </pre>
 */
public final class IpPacket {

    public static final int HEADER_LEN = 20;
    public static final int PROTO_ICMP = 1;
    public static final int PROTO_UDP  = 17;
    public static final int PROTO_TCP  = 6;

    private IpPacket() {}

    public static byte[] encode(int srcIp, int dstIp, int protocol, byte[] payload) {
        ByteBuffer bb = ByteBuffer.allocate(HEADER_LEN + payload.length).order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) 0x45);          // version=4, IHL=5
        bb.put((byte) 0);              // TOS
        bb.putShort((short) (HEADER_LEN + payload.length));
        bb.putShort((short) 0);        // identification
        bb.putShort((short) 0);        // flags + frag offset
        bb.put((byte) 64);             // TTL
        bb.put((byte) protocol);
        bb.putShort((short) 0);        // checksum placeholder
        bb.putInt(srcIp);
        bb.putInt(dstIp);
        bb.put(payload);
        byte[] out = bb.array();
        int csum = checksum(out, 0, HEADER_LEN);
        out[10] = (byte) ((csum >>> 8) & 0xff);
        out[11] = (byte) (csum & 0xff);
        return out;
    }

    public static void decode(SkBuff skb) {
        byte[] d = skb.rawData();
        int off = skb.readPos();
        if (d.length - off < HEADER_LEN) throw new IllegalArgumentException("IP packet too short");
        int versionIhl = d[off] & 0xff;
        if ((versionIhl >>> 4) != 4) throw new IllegalArgumentException("not IPv4");
        skb.ipProtocol = d[off + 9] & 0xff;
        skb.srcIp = ByteBuffer.wrap(d, off + 12, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        skb.dstIp = ByteBuffer.wrap(d, off + 16, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        skb.advance(HEADER_LEN);
    }

    /** Standard 16-bit ones-complement checksum used by IP and UDP. */
    public static int checksum(byte[] data, int off, int len) {
        long sum = 0;
        int i = 0;
        while (i + 1 < len) {
            sum += ((data[off + i] & 0xff) << 8) | (data[off + i + 1] & 0xff);
            i += 2;
        }
        if (i < len) {
            sum += (data[off + i] & 0xff) << 8;
        }
        while ((sum >>> 16) != 0) {
            sum = (sum & 0xffff) + (sum >>> 16);
        }
        return (~((int) sum)) & 0xffff;
    }
}
