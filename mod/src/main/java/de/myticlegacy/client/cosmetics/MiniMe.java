package de.myticlegacy.client.cosmetics;

import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MiniMe für diese Version: Skin über den Skin-Manager von Minecraft (aus dem Profil von MiniMeCore).
 * Diese Fassung gilt ab 1.21.9 (PlayerSkin mit ClientAsset); ältere Versionen haben eine eigene MiniMe.java.
 */
public final class MiniMe {
    private static final Map<String, PlayerSkin> SKINS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();

    private MiniMe() {
    }

    /** MiniMe-Skins dieser Figur je Stelle (Reihenfolge wie MiniMeCore.POSITIONS) oder null, wenn sie keinen hat. */
    public static PlayerSkin[] skinsFor(Object entity) {
        Minecraft mc = Minecraft.getInstance();
        if (!(entity instanceof Entity e) || mc.player == null) return null;
        String uuid = e.getUUID().toString().replace("-", "");
        boolean self = e.getUUID().equals(mc.player.getUUID());
        PlayerSkin[] skins = null;
        for (int i = 0; i < MiniMeCore.POSITIONS.length; i++) {
            String name = MiniMeCore.nameOf(uuid, self, MiniMeCore.POSITIONS[i][0]);
            PlayerSkin skin = name == null ? null : skin(name);
            if (skin == null) continue;
            if (skins == null) skins = new PlayerSkin[MiniMeCore.POSITIONS.length];
            skins[i] = skin;
        }
        return skins;
    }

    /** Skin zu einem Namen (null, solange er lädt). */
    public static PlayerSkin skin(String name) {
        MiniMeCore.Profile profile = MiniMeCore.profile(name);
        if (profile == null || profile.textures == null) return null;
        PlayerSkin skin = SKINS.get(profile.uuid);
        if (skin != null) return skin;
        Long failed = FAILED.get(profile.uuid);
        if (failed != null && System.currentTimeMillis() - failed < 60_000) return null;
        if (LOADING.putIfAbsent(profile.uuid, Boolean.TRUE) != null) return null;
        LinkedHashMultimap<String, Property> props = LinkedHashMultimap.create();
        props.put("textures", new Property("textures", profile.textures, profile.signature));
        UUID uuid = UUID.fromString(profile.uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        Minecraft.getInstance().getSkinManager().get(new GameProfile(uuid, profile.name, new PropertyMap(props)))
                .whenComplete((opt, error) -> {
                    if (error == null && opt.isPresent()) {
                        SKINS.put(profile.uuid, opt.get());
                    } else {
                        // nach einer Minute erneut versuchen
                        de.myticlegacy.client.MyticClient.LOG.warn("MiniMe-Skin " + profile.name + " nicht geladen: " + (error != null ? error : "kein Skin"));
                        FAILED.put(profile.uuid, System.currentTimeMillis());
                    }
                    LOADING.remove(profile.uuid);
                });
        return null;
    }
}
