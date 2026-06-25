package org.minikernel.kernel.net;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Ethernet II frame encode/decode. 14-byte header:
 * <pre>
 *   [6 dst MAC][6 src MAC][2 EtherType][... payload ...]
 * </pre>
 */
public final class EthernetFrame {

    public static final int HEADER_LEN = 14;
    public static final int ETHERTYPE_IPV4 = 0x0800;
    public static final int ETHERTYPE_ARP  = 0x0806;

    private EthernetFrame() {}

    public static byte[] encode(byte[] dstMac, byte[] srcMac, int etherType, byte[] payload) {
        if (dstMac.length != 6 || srcMac.length != 6) throw new IllegalArgumentException("MAC must be 6 bytes");
        ByteBuffer bb = ByteBuffer.allocate(HEADER_LEN + payload.length).order(ByteOrder.BIG_ENDIAN);
        bb.put(dstMac);
        bb.put(srcMac);
        bb.putShort((short) etherType);
        bb.put(payload);
        return bb.array();
    }

    public static void decode(SkBuff skb) {
        byte[] data = skb.rawData();
        if (data.length < HEADER_LEN) throw new IllegalArgumentException("frame too short: " + data.length);
        skb.dstMac = Arrays.copyOfRange(data, 0, 6);
        skb.srcMac = Arrays.copyOfRange(data, 6, 12);
        skb.etherType = ((data[12] & 0xFF) << 8) | (data[13] & 0xFF);
        skb.advance(HEADER_LEN);
    }

    public static byte[] broadcastMac() {
        return new byte[]{(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff};
    }

    public static String macToString(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02x", mac[i] & 0xff));
        }
        return sb.toString();
    }
}
