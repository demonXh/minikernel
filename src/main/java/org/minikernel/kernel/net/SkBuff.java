package org.minikernel.kernel.net;

import java.util.Arrays;

/**
 * Network packet buffer, the Java analogue of Linux's {@code sk_buff}.
 *
 * <p>Holds the raw bytes plus header offsets so each protocol layer can
 * stamp where its header begins without copying. We keep it minimal: only
 * the read-cursor moves as we peel off headers.
 */
public final class SkBuff {

    private final byte[] data;
    private int readPos;
    /** EtherType / IP protocol / UDP src port populated by lower layers as the packet climbs. */
    public int etherType;
    public int ipProtocol;
    public int srcIp;
    public int dstIp;
    public int srcPort;
    public int dstPort;
    public byte[] srcMac;
    public byte[] dstMac;

    public SkBuff(byte[] data) { this.data = data; }

    public static SkBuff of(byte[] data) { return new SkBuff(data); }

    public byte[] rawData() { return data; }
    public int readPos() { return readPos; }
    public int remaining() { return data.length - readPos; }
    public void advance(int n) { readPos += n; }

    public byte[] payload() {
        return Arrays.copyOfRange(data, readPos, data.length);
    }

    /** Slice from current cursor to end as a new SkBuff (does not copy bytes). */
    public SkBuff sliceFromCursor() {
        byte[] copy = Arrays.copyOfRange(data, readPos, data.length);
        SkBuff s = new SkBuff(copy);
        s.etherType = this.etherType;
        s.ipProtocol = this.ipProtocol;
        s.srcIp = this.srcIp;
        s.dstIp = this.dstIp;
        s.srcPort = this.srcPort;
        s.dstPort = this.dstPort;
        s.srcMac = this.srcMac;
        s.dstMac = this.dstMac;
        return s;
    }
}
