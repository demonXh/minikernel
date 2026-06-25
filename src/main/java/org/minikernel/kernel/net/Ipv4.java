package org.minikernel.kernel.net;

/** IPv4 address helpers: pack/unpack between dotted-quad and int32. */
public final class Ipv4 {

    private Ipv4() {}

    public static int pack(int a, int b, int c, int d) {
        return ((a & 0xff) << 24) | ((b & 0xff) << 16) | ((c & 0xff) << 8) | (d & 0xff);
    }

    public static int parse(String s) {
        String[] parts = s.split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("bad IP: " + s);
        return pack(Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
    }

    public static String format(int ip) {
        return ((ip >>> 24) & 0xff) + "." + ((ip >>> 16) & 0xff) + "." + ((ip >>> 8) & 0xff) + "." + (ip & 0xff);
    }
}
