package de.myticlegacy.client.diag;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.File;

/**
 * „Diagnose-Messung“ starten: Befehl /myticdiagnose [sekunden|stop] oder eigene Taste (Standard: nicht belegt).
 * Misst 60 s den Render-Thread (SamplingProfiler) und schreibt den Bericht in den Diagnose-Ordner des Launchers.
 */
public final class DiagnoseMeasure {
    public static final int DEFAULT_SECONDS = 60;
    private static KeyMapping key;

    private DiagnoseMeasure() {
    }

    public static void register() {
        key = Compat.registerKey("key.myticclient.diagnose", InputConstants.UNKNOWN.getValue());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommandManager.literal("myticdiagnose")
                        .executes(ctx -> toggle(DEFAULT_SECONDS))
                        .then(ClientCommandManager.literal("stop").executes(ctx -> SamplingProfiler.stopRunning() ? 1 : 0))
                        .then(ClientCommandManager.argument("sekunden", IntegerArgumentType.integer(5, 600))
                                .executes(ctx -> toggle(IntegerArgumentType.getInteger(ctx, "sekunden"))))));
    }

    /** Jeden Tick: Taste abfragen (kostet nichts, solange sie nicht gedrückt wird) */
    public static void tick() {
        if (key == null) return;
        while (key.consumeClick()) toggle(DEFAULT_SECONDS);
    }

    /** Ordner für Berichte: vom Launcher (-Dmytic.diagnose.dir), sonst mytic/diagnose im Spielordner */
    public static File dir() {
        String prop = System.getProperty("mytic.diagnose.dir", "").trim();
        if (!prop.isEmpty()) return new File(prop);
        return FabricLoader.getInstance().getGameDir().resolve("mytic").resolve("diagnose").toFile();
    }

    private static int toggle(int seconds) {
        Minecraft mc = Minecraft.getInstance();
        if (SamplingProfiler.isRunning()) {
            SamplingProfiler.stopRunning();
            Compat.chat(mc, "§8[§dMytic§8] §7Diagnose-Messung wird beendet …");
            return 1;
        }
        // Befehle und Tasten laufen im Render-Thread – genau der wird gemessen
        mc.execute(() -> {
            Thread render = Thread.currentThread();
            String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : mc.hasSingleplayerServer() ? "Einzelspieler" : "Menü";
            String header = "Mytic " + MyticClient.VERSION + " · Minecraft " + Compat.versionName() + " · Java " + System.getProperty("java.version")
                    + "\nOrt: " + server + " · FPS beim Start: " + mc.getFps() + " · Mods: " + FabricLoader.getInstance().getAllMods().size()
                    + "\nSichtweite: " + mc.options.renderDistance().get();
            boolean started = SamplingProfiler.start(render, new FabricMods(), dir(), seconds, header, (file, error) -> mc.execute(() -> {
                if (file != null) Compat.chat(mc, "§8[§dMytic§8] §aDiagnose-Messung fertig: §f" + file.getName() + " §7(Launcher → Einstellungen → Diagnose)");
                else Compat.chat(mc, "§8[§dMytic§8] §cDiagnose-Messung fehlgeschlagen: " + error);
            }));
            if (started) Compat.chat(mc, "§8[§dMytic§8] §7Diagnose-Messung läuft " + seconds + " s – spiel normal weiter. Abbrechen: §f/myticdiagnose stop");
        });
        return 1;
    }
}
