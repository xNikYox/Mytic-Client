package de.myticlegacy.client.invsee;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.ScreenMouse;
import de.myticlegacy.client.gui.ModMenuScreen;
import de.myticlegacy.client.hud.HudModule;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.module.Msg;
import de.myticlegacy.client.setting.ActionSetting;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ItemListSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * InvSee-Übersicht: beim /invsee (auch /openinv, /inv, /isee …) ein Glas-Panel neben dem Inventar mit den gewählten
 * Items (Totems, Kristalle, Gapples …) und der Rüstung samt Haltbarkeit und Schutzart; HUD mit dem letzten /invsee
 * (im HUD-Editor und im Chat verschiebbar); Taste wiederholt das letzte /invsee unsichtbar und aktualisiert das HUD.
 * Basiert auf „InvSee Summary“ von Noah (MIT-Lizenz), neu geschrieben im Mytic-Design.
 */
public class InvSeeModule extends HudModule {
    private static final Pattern INVSEE_COMMAND = Pattern.compile("^(?:[\\w-]+:)?(?:invsee|openinv|inv|inventorysee|seeinv|isee)\\s+(\\S+).*$", Pattern.CASE_INSENSITIVE);
    private static final long INVSEE_WINDOW_MS = 5000;
    private static final String[] DEFAULT_ITEMS = {"minecraft:totem_of_undying", "minecraft:end_crystal", "minecraft:enchanted_golden_apple"};
    private static InvSeeModule instance;

    public final BoolSetting summary, hud, itemNames, everyChest, showArmor, hudSameItems;
    public final ModeSetting layout;
    public final SliderSetting overlayScale;
    public final ItemListSetting items, hudItems;

    private static InvSeeData.Snapshot last;
    private static long lastTime;
    private static String pendingName;
    private static long pendingTime;
    private static boolean autoClosePending, openMenuNextTick;
    private KeyMapping repeatKey, configKey;
    // HUD im Chat verschieben
    private boolean chatDrag;
    private double chatMouseX, chatMouseY;
    private int chatStartX, chatStartY;

    public InvSeeModule() {
        super("invsee", "InvSee-Übersicht", "Beim /invsee: gezählte Items und Rüstung neben dem Inventar, HUD mit dem letzten /invsee",
                () -> Items.TOTEM_OF_UNDYING, false, 4, 200);
        instance = this;
        summary = new BoolSetting(this, "summary", "Übersicht neben dem Inventar", true);
        hud = new BoolSetting(this, "hud", "HUD mit dem letzten /invsee", true);
        itemNames = new BoolSetting(this, "names", "Item-Namen anzeigen", true);
        showArmor = new BoolSetting(this, "armor", "Rüstung mit Haltbarkeit und Schutzart", true);
        everyChest = new BoolSetting(this, "everyChest", "Auch bei jeder Kiste", false);
        layout = new ModeSetting(this, "layout", "Anordnung im Inventar", "Liste", "Liste", "Einzeln");
        overlayScale = new SliderSetting(this, "overlayScale", "Größe im Inventar", 50, 200, 5, 100, " %");
        items = new ItemListSetting(this, "items", "Gezählte Items (Inventar)", 12, () -> true, DEFAULT_ITEMS);
        hudSameItems = new BoolSetting(this, "hudSame", "HUD zählt dieselben Items", false);
        hudItems = new ItemListSetting(this, "hudItems", "Gezählte Items (HUD)", 12, () -> !hudSameItems.get(), DEFAULT_ITEMS);
        new ActionSetting(this, "resetPos", "Verschobene Anzeigen", () -> "Positionen zurücksetzen", () -> {
            MyticClient.config().settings.remove(OFFSETS);
            resetPosition();
            Msg.actionBar("InvSee-Positionen zurückgesetzt");
        });
        new ActionSetting(this, "clear", "HUD mit dem letzten /invsee", () -> "HUD leeren", () -> last = null);
    }

    @Override
    protected boolean defaultBackground() {
        return true;
    }

    public static InvSeeModule get() {
        return instance;
    }

    static void lastSeen(InvSeeData.Snapshot snap) {
        last = snap;
        lastTime = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------------------------------ Anmelden

    public void register() {
        repeatKey = Compat.registerKey("key.myticclient.invsee_repeat", InputConstants.KEY_I);
        configKey = Compat.registerKey("key.myticclient.invsee_config", InputConstants.UNKNOWN.getValue());
        ClientSendMessageEvents.COMMAND.register(this::onCommand);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommandManager.literal("invseesummary")
                        .executes(ctx -> {
                            openMenuNextTick = true;
                            return 1;
                        })
                        .then(ClientCommandManager.literal("clear").executes(ctx -> {
                            last = null;
                            Compat.chat(Minecraft.getInstance(), "§8[§dMytic§8] §7InvSee-HUD geleert.");
                            return 1;
                        }))));
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof ChatScreen chat) {
                attachChat(chat);
                return;
            }
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) return;
            InvSeeOverlay prev = InvSeeOverlay.previous(container);
            if (prev != null) {
                InvSeeOverlay.attach(container, prev.title(), prev.isInvsee()); // neu aufgebaut (Fenstergröße)
                return;
            }
            if (!enabled()) return;
            boolean fromInvsee = pendingName != null && System.currentTimeMillis() - pendingTime < INVSEE_WINDOW_MS;
            if (fromInvsee) {
                String name = pendingName;
                pendingName = null;
                InvSeeOverlay.attach(container, name, true);
                if (autoClosePending) {
                    autoClosePending = false;
                    InvSeeAutoClose.track(container);
                }
            } else if (everyChest.get() && summary.get()) {
                InvSeeOverlay.attach(container, screen.getTitle().getString(), false);
            }
        });
    }

    /** jeder gesendete Befehl: /invsee <Name> merken (das nächste Inventar gehört dazu) */
    private void onCommand(String command) {
        String cmd = command.trim();
        Matcher m = INVSEE_COMMAND.matcher(cmd);
        if (!m.matches()) return;
        autoClosePending = false;
        pendingName = m.group(1);
        pendingTime = System.currentTimeMillis();
        if (!cmd.equals(lastCommand())) {
            MyticClient.config().settings.put(LAST_COMMAND, cmd);
            MyticClient.config().save();
        }
    }

    private static final String LAST_COMMAND = "invsee.lastCommand", OFFSETS = "invsee.offsets";

    private static String lastCommand() {
        Object v = MyticClient.config().settings.get(LAST_COMMAND);
        return v instanceof String s ? s : null;
    }

    public void tick(Minecraft mc) {
        while (repeatKey.consumeClick()) if (enabled()) repeat(mc);
        while (configKey.consumeClick()) openMenuNextTick = true;
        if (openMenuNextTick) {
            openMenuNextTick = false;
            Compat.setScreen(new ModMenuScreen(null, this));
        }
        InvSeeAutoClose.tick(mc);
    }

    private void repeat(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null || Compat.screen(mc) != null) return;
        String cmd = lastCommand();
        if (cmd == null) {
            Msg.actionBar("§cNoch kein /invsee benutzt");
            return;
        }
        mc.getConnection().sendCommand(cmd);
        onCommand(cmd);
        autoClosePending = true;
    }

    // ------------------------------------------------------------------------------------------ Versatz im Inventar

    /** Gespeicherter Versatz einer Anzeige im Inventar ("list" oder Zeilen-Schlüssel) */
    int[] offset(String key) {
        Object all = MyticClient.config().settings.get(OFFSETS);
        if (all instanceof Map<?, ?> map && map.get(key) instanceof List<?> l && l.size() == 2
                && l.get(0) instanceof Number a && l.get(1) instanceof Number b) {
            return new int[]{a.intValue(), b.intValue()};
        }
        return new int[]{0, 0};
    }

    @SuppressWarnings("unchecked")
    void setOffset(String key, int x, int y, boolean save) {
        Object all = MyticClient.config().settings.get(OFFSETS);
        Map<String, Object> map = new LinkedHashMap<>();
        if (all instanceof Map<?, ?> m) map.putAll((Map<String, Object>) m);
        map.put(key, List.of(x, y));
        MyticClient.config().settings.put(OFFSETS, map);
        if (save) MyticClient.config().save();
    }

    void saveOffsets() {
        MyticClient.config().save();
    }

    // ------------------------------------------------------------------------------------------ HUD

    private List<String> hudList() {
        return hudSameItems.get() ? items.get() : hudItems.get();
    }

    private InvSeeData.Snapshot shown(boolean preview) {
        if (last != null || !preview) return last;
        List<ItemStack> armor = new ArrayList<>();
        armor.add(Module.stack(Items.NETHERITE_HELMET));
        armor.add(Module.stack(Items.NETHERITE_CHESTPLATE));
        if (!armor.get(1).isEmpty()) armor.get(1).setDamageValue(armor.get(1).getMaxDamage() / 3);
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("minecraft:totem_of_undying", 4);
        counts.put("minecraft:end_crystal", 64);
        return new InvSeeData.Snapshot("Noah", counts, armor);
    }

    private List<InvSeeData.Row> rows(InvSeeData.Snapshot s) {
        List<InvSeeData.Row> rows = InvSeeData.itemRows(hudList(), s.counts());
        if (showArmor.get()) rows.addAll(InvSeeData.armorRows(s.armor()));
        return rows;
    }

    private static String ago(long ms) {
        long s = ms / 1000;
        if (s < 60) return "vor " + s + " s";
        if (s < 3600) return "vor " + s / 60 + " min";
        return "vor " + s / 3600 + " h";
    }

    private String right(boolean preview) {
        return last == null && preview ? "vor 12 s" : ago(System.currentTimeMillis() - lastTime);
    }

    private float[] sizeValue;
    private long sizeAt;

    /** Größe höchstens alle 50 ms neu berechnen (wird pro Bild mehrmals gebraucht) */
    private float[] size(boolean preview) {
        long now = System.nanoTime();
        if (sizeValue != null && now - sizeAt < REFRESH_NANOS) return sizeValue;
        sizeAt = now;
        InvSeeData.Snapshot s = shown(preview);
        if (s == null) return sizeValue = new float[]{80, 40};
        List<InvSeeData.Row> rows = rows(s);
        float k = Compat.guiScale() * scaleFactor();
        return sizeValue = new float[]{InvSeePanel.width(s.player(), right(preview), rows, itemNames.get(), k), InvSeePanel.height(rows)};
    }

    @Override
    public boolean hasContent() {
        return hud.get() && last != null;
    }

    @Override
    public int baseWidth() {
        return Math.round(size(true)[0]);
    }

    @Override
    public int baseHeight() {
        return Math.round(size(true)[1]);
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        InvSeeData.Snapshot s = shown(preview);
        if (s == null) return;
        List<InvSeeData.Row> rows = rows(s);
        float k = de.myticlegacy.client.gui.ui.Draw.k(g);
        float w = InvSeePanel.width(s.player(), right(preview), rows, itemNames.get(), k);
        InvSeePanel.drawList(g, s.player(), right(preview), rows, w, itemNames.get(), background.get() ? (int) Math.round(backgroundOpacity.get()) : 0);
    }

    // ------------------------------------------------------------------------------------------ im Chat verschieben

    private void attachChat(ChatScreen chat) {
        ScreenMouse.attach(chat, new ScreenMouse.Handler() {
            @Override
            public boolean down(double x, double y, int button) {
                if (button != 0 || !enabled() || !hasContent() || x < x() || y < y() || x >= x() + width() || y >= y() + height()) return false;
                chatDrag = true;
                chatMouseX = x;
                chatMouseY = y;
                chatStartX = x();
                chatStartY = y();
                return true;
            }

            @Override
            public boolean up(double x, double y, int button) {
                if (!chatDrag) return false;
                chatDrag = false;
                MyticClient.config().save();
                return true;
            }
        });
        ScreenEvents.afterRender(chat).register((s, g, mouseX, mouseY, delta) -> {
            if (chatDrag) moveTo(chatStartX + (int) Math.round(mouseX - chatMouseX), chatStartY + (int) Math.round(mouseY - chatMouseY));
        });
        ScreenEvents.remove(chat).register(s -> {
            if (chatDrag) {
                chatDrag = false;
                MyticClient.config().save();
            }
        });
    }
}
