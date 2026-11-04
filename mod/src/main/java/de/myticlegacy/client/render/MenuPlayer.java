package de.myticlegacy.client.render;

import com.mojang.authlib.GameProfile;
import de.myticlegacy.client.cosmetics.Cosmetics;
import de.myticlegacy.client.cosmetics.MiniMe;
import de.myticlegacy.client.cosmetics.MiniMeState;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import de.myticlegacy.client.cosmetics.WingsState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Eigener Spieler als 3D-Modell im Hauptmenü – ohne Welt: Der Zustand fürs Zeichnen wird direkt gebaut (Skin, Mytic-
 * Umhang, Flügel, Mini-Mes, Aura), ohne Namensschild. Fassung ab 1.21.9 (ältere Versionen haben eigene).
 */
public final class MenuPlayer {
    /** Kennung im Zeichen-Zustand, an der die Cosmetics-Ebenen das Menü-Modell erkennen */
    public static final int ID = -7_000_001;
    private static final AvatarRenderState STATE = new AvatarRenderState();
    private static CompletableFuture<Optional<PlayerSkin>> skinFuture;
    private static UUID skinFor;

    private MenuPlayer() {
    }

    /** Wird ein echtes 3D-Modell gezeichnet? (ältere Versionen: nur ein flaches Bild) */
    public static boolean supported() {
        return true;
    }

    private static PlayerSkin skin(Minecraft mc) {
        UUID uuid = mc.getUser().getProfileId();
        if (!uuid.equals(skinFor)) {
            skinFor = uuid;
            // Profil mit Skin-Daten im Hintergrund holen (getGameProfile kann kurz warten), dann den Skin laden
            skinFuture = CompletableFuture.supplyAsync(mc::getGameProfile).thenCompose(p -> mc.getSkinManager().get(p))
                    .exceptionally(e -> Optional.empty());
        }
        PlayerSkin base = skinFuture.getNow(Optional.empty()).orElseGet(() -> DefaultPlayerSkin.get(new GameProfile(uuid, mc.getUser().getName())));
        ClientAsset.Texture cape = Cosmetics.ownCape();
        return cape == null ? base : new PlayerSkin(base.body(), cape, base.elytra(), base.model(), base.secure());
    }

    /**
     * Zeichnen in einen Bereich (GUI-Koordinaten, ohne die Mytic-Skalierung): yaw = Drehung um die Hochachse in Grad
     * (0 = von vorn), pitch = leichte Neigung der Kamera. size = Pixel pro Block.
     */
    public static void draw(GuiGraphics g, int x0, int y0, int x1, int y1, float size, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        AvatarRenderState s = STATE;
        float t = (System.currentTimeMillis() % 3_600_000L) / 50f; // Ticks für Animationen
        s.entityType = EntityType.PLAYER;
        s.id = ID;
        s.skin = skin(mc);
        s.ageInTicks = t;
        s.boundingBoxWidth = 0.6f;
        s.boundingBoxHeight = 1.8f;
        s.eyeHeight = 1.62f;
        s.scale = 1f;
        s.lightCoords = 0xF000F0;
        s.nameTag = null; // kein Namensschild
        s.scoreText = null;
        s.isInvisible = false;
        s.bodyRot = 180f + yaw;
        s.yRot = (float) Math.sin(t / 40f) * 6f; // Kopf schaut sich leicht um
        s.xRot = (float) Math.sin(t / 55f) * 3f;
        s.walkAnimationPos = 0f;
        s.walkAnimationSpeed = 0f;
        s.showHat = mc.options.isModelPartEnabled(PlayerModelPart.HAT);
        s.showJacket = mc.options.isModelPartEnabled(PlayerModelPart.JACKET);
        s.showLeftPants = mc.options.isModelPartEnabled(PlayerModelPart.LEFT_PANTS_LEG);
        s.showRightPants = mc.options.isModelPartEnabled(PlayerModelPart.RIGHT_PANTS_LEG);
        s.showLeftSleeve = mc.options.isModelPartEnabled(PlayerModelPart.LEFT_SLEEVE);
        s.showRightSleeve = mc.options.isModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE);
        s.showCape = mc.options.isModelPartEnabled(PlayerModelPart.CAPE);
        // Umhang weht im Wind wie im Spiel
        float[] wind = Cosmetics.wind(ID);
        s.capeLean = 6f + wind[0];
        s.capeLean2 = wind[1];
        s.capeFlap = 2f + wind[2];
        ((WingsState) s).mytic$setWings(CosmeticsCore.selectedWings());
        ((MiniMeState) s).mytic$setMiniMes(MiniMe.ownSkins());

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(pitch));
        rotation.mul(camera);
        Vector3f translation = new Vector3f(0f, s.boundingBoxHeight / 2f + 0.0625f, 0f);
        g.submitEntityRenderState(s, size, translation, rotation, camera, x0, y0, x1, y1);
    }
}
