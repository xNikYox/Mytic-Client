package de.myticlegacy.client.friends;

import java.util.Locale;

/**
 * „Freunden beitreten“: Ist eine Server-Adresse für andere erreichbar? Gleiche Regeln wie im Friends-Server
 * (friends-server/src/join.js) und im Launcher. Nur öffentliche Adressen – keine Einzelspieler-/LAN-Welt, kein Realm,
 * keine privaten oder lokalen Netze. Es wird nie DNS abgefragt. (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class JoinTarget {
    /** Platzhalter, die statt einer Adresse gemeldet werden */
    public static final String SINGLEPLAYER = "Einzelspieler", LAN = "LAN-Welt", REALMS = "Realms", PRIVATE = "Privater Server";

    private JoinTarget() {
    }

    /**
     * Was Freunde als Server sehen: Realms/LAN als Platzhalter, private Adressen als „Privater Server“, öffentliche
     * Adressen unverändert. ip null = kein Server.
     */
    public static String shown(String ip, boolean lan, boolean realm) {
        if (ip == null) return null;
        if (realm) return REALMS;
        if (lan) return LAN;
        return joinable(ip) ? ip.trim() : PRIVATE;
    }

    /** Darf man dieser Adresse beitreten (öffentlich erreichbar)? */
    public static boolean joinable(String server) {
        if (server == null) return false;
        String text = server.trim().toLowerCase(Locale.ROOT);
        if (text.equals("einzelspieler") || text.equals("lan-welt") || text.equals("realms") || text.equals("privater server")) return false;
        String host = host(text);
        if (host == null) return false;
        if (host.indexOf(':') >= 0) {
            // IPv6: Loopback, unspezifiziert, link-lokal (fe80::/10), eindeutig lokal (fc00::/7)
            return !(host.equals("::1") || host.equals("::") || host.matches("^fe[89ab].*") || host.matches("^f[cd].*"));
        }
        int[] v4 = ipv4(host);
        if (v4 != null) return !privateV4(v4);
        if (!host.matches("[a-z0-9.-]+") || host.indexOf('.') < 0 || host.startsWith(".") || host.endsWith(".") || host.contains("..")) return false;
        return !host.matches("(.*\\.)?(localhost|local|lan|home|internal|intranet|corp|private|home\\.arpa)");
    }

    /** Host aus "host", "host:port", "[v6]:port" (klein) oder null bei ungültiger Adresse. */
    static String host(String text) {
        if (text.isEmpty() || text.length() > 64 || text.matches(".*[\\s/@].*")) return null;
        java.util.regex.Matcher v6 = java.util.regex.Pattern.compile("^\\[([0-9a-f:.]+)\\](?::(\\d{1,5}))?$").matcher(text);
        if (v6.matches()) return v6.group(2) == null || port(v6.group(2)) ? v6.group(1) : null;
        int colons = text.length() - text.replace(":", "").length();
        if (colons == 1) {
            int i = text.indexOf(':');
            String p = text.substring(i + 1);
            if (!p.matches("\\d{1,5}") || !port(p)) return null;
            text = text.substring(0, i);
        }
        return text.isEmpty() ? null : text;
    }

    private static boolean port(String p) {
        int n = Integer.parseInt(p);
        return n >= 1 && n <= 65535;
    }

    private static int[] ipv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return null;
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("\\d{1,3}")) return null;
            out[i] = Integer.parseInt(parts[i]);
            if (out[i] > 255) return null;
        }
        return out;
    }

    private static boolean privateV4(int[] ip) {
        int a = ip[0], b = ip[1];
        return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168)
                || (a == 100 && b >= 64 && b <= 127) || a >= 224;
    }
}
