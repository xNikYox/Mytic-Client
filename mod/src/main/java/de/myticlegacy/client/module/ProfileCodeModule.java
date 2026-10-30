package de.myticlegacy.client.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.myticlegacy.client.setting.ActionSetting;
import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Profil-Code: das komplette Setup (Mods an/aus, Einstellungen, HUD-Positionen, HUD- und Server-Profile, optional Tasten)
 * als Text „MYTIC1:…“ kopieren und bei Freunden einfügen. Vor dem Einfügen wird der eigene Stand gemerkt („Rückgängig“).
 */
public class ProfileCodeModule extends Module {
    private static final String PREFIX = "MYTIC1:";

    private final BoolSetting keys;
    private JsonObject backup;

    public ProfileCodeModule() {
        super("profilecode", "Profil-Code", "Setup als Code kopieren und bei Freunden einfügen", Category.CLIENT, () -> Items.NAME_TAG, true);
        keys = new BoolSetting(this, "keys", "Tasten mitnehmen", false);
        new ActionSetting(this, "copy", "Eigenes Setup", () -> "Code kopieren", this::copy);
        new ActionSetting(this, "paste", "Setup eines Freundes", () -> "Code einfügen", this::paste);
        new ActionSetting(this, "undo", "Eigenes Setup zurückholen", () -> backup == null ? "–" : "Rückgängig", this::undo);
    }

    private void copy() {
        try {
            JsonObject data = CloudSyncModule.snapshot(keys.get());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
                gz.write(data.toString().getBytes(StandardCharsets.UTF_8));
            }
            String code = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
            Msg.actionBar("Profil-Code kopiert (" + code.length() + " Zeichen)");
        } catch (Exception e) {
            Msg.actionBar("Kopieren fehlgeschlagen");
        }
    }

    private void paste() {
        String code = Minecraft.getInstance().keyboardHandler.getClipboard();
        JsonObject data = decode(code);
        if (data == null) {
            Msg.actionBar("Kein Mytic-Profil-Code in der Zwischenablage");
            return;
        }
        backup = CloudSyncModule.snapshot(true); // eigenen Stand (mit Tasten) für „Rückgängig“ merken
        CloudSyncModule.apply(data);
        Msg.actionBar("Profil übernommen – „Rückgängig“ holt dein Setup zurück");
    }

    private void undo() {
        if (backup == null) return;
        CloudSyncModule.apply(backup);
        backup = null;
        Msg.actionBar("Eigenes Setup wiederhergestellt");
    }

    /** Code → Daten oder null (falsches Format, kaputt) */
    static JsonObject decode(String code) {
        if (code == null) return null;
        String c = code.trim();
        if (!c.startsWith(PREFIX)) return null;
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(c.substring(PREFIX.length()))))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = gz.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 4 * 1024 * 1024) return null; // Schutz gegen riesige Codes
            }
            var e = JsonParser.parseString(out.toString(StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
