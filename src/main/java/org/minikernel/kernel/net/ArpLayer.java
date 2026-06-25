package org.minikernel.kernel.net;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Address Resolution Protocol: IPv4 -> MAC. We only implement the
 * request/reply path used by our loopback bridge.
 *
 * <p>ARP packet (28 bytes, no options):
 * <pre>
 *   [HType=1][PType=0x0800][HLen=6][PLen=4][Op]
 *   [SHA 6][SPA 4][THA 6][TPA 4]
 * </pre>
 */
public final class ArpLayer {

    public static final int OP_REQUEST = 1;
    public static final int OP_REPLY   = 2;

    private final Map<Integer, byte[]> cache = new HashMap<>();

    public synchronized void learn(int ip, byte[] mac) {
        cache.put(ip, mac.clone());
    }

    public synchronized byte[] resolve(int ip) {
        byte[] mac = cache.get(ip);
        return mac == null ? null : mac.clone();
    }

    public synchronized int cacheSize() { return cache.size(); }

    public static byte[] encode(int op, byte[] senderMac, int senderIp, byte[] targetMac, int targetIp) {
        ByteBuffer bb = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN);
        bb.putShort((short) 1);
        bb.putShort((short) EthernetFrame.ETHERTYPE_IPV4);
        bb.put((byte) 6);
        bb.put((byte) 4);
        bb.putShort((short) op);
        bb.put(senderMac);
        bb.putInt(senderIp);
        bb.put(targetMac);
        bb.putInt(targetIp);
        return bb.array();
    }

    /** Decode an ARP payload and update the cache. Returns the parsed op, or -1 on malformed input. */
    public synchronized int onArpFrame(byte[] payload) {
        if (payload.length < 28) return -1;
        int op = ((payload[6] & 0xff) << 8) | (payload[7] & 0xff);
        byte[] senderMac = Arrays.copyOfRange(payload, 8, 14);
        int senderIp = ByteBuffer.wrap(payload, 14, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        learn(senderIp, senderMac);
        return op;
    }
}
