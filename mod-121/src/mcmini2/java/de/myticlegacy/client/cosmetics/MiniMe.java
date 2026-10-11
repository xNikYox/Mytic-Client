package de.myticlegacy.client.cosmetics;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** MiniMe für 1.21–1.21.8: Skin über den Skin-Manager (lookupInsecure liefert bis zum Laden den Standard-Skin). */
public final class MiniMe {
    private static final Map<String, Supplier<PlayerSkin>> SKINS = new ConcurrentHashMap<>();

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

    public static PlayerSkin skin(String name) {
        MiniMeCore.Profile profile = MiniMeCore.profile(name);
        if (profile == null || profile.textures == null) return null;
        return SKINS.computeIfAbsent(profile.uuid, id -> {
            UUID uuid = UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            GameProfile game = new GameProfile(uuid, profile.name);
            game.getProperties().put("textures", new Property("textures", profile.textures, profile.signature));
            return Minecraft.getInstance().getSkinManager().lookupInsecure(game);
        }).get();
    }
}
