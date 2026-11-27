package de.myticlegacy.client.emotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Alle Emotes (gleiche IDs wie im Friends-Server, friends-server/src/live.js). Die ersten acht sind für alle frei;
 * spätere Emotes können mit free = false kommen und werden über die Admin-Seite als "emote:&lt;id&gt;" freigeschaltet –
 * welche man nutzen darf, meldet der Server beim Verbinden ("hi"). (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class EmoteCatalog {
    public static final class Emote {
        public final String id;
        public final String name;
        /** Icon aus gui/ui/Icons */
        public final String icon;
        /** Länge in Millisekunden (1–4 s) */
        public final int ms;
        public final boolean free;

        Emote(String id, String name, String icon, int ms, boolean free) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.ms = ms;
            this.free = free;
        }
    }

    public static final List<Emote> ALL;

    static {
        List<Emote> list = new ArrayList<Emote>();
        list.add(new Emote("winken", "Winken", "wave", 2000, true));
        list.add(new Emote("jubeln", "Jubeln", "cheer", 2200, true));
        list.add(new Emote("klatschen", "Klatschen", "clap", 2400, true));
        list.add(new Emote("tanzen", "Tanzen", "music", 4000, true));
        list.add(new Emote("verbeugen", "Verbeugen", "bow", 2200, true));
        list.add(new Emote("facepalm", "Facepalm", "facepalm", 2400, true));
        list.add(new Emote("hinsetzen", "Hinsetzen", "sit", 4000, true));
        ALL = Collections.unmodifiableList(list);
    }

    private EmoteCatalog() {
    }

    public static Emote get(String id) {
        if (id == null) return null;
        for (Emote e : ALL) if (e.id.equals(id)) return e;
        return null;
    }
}
