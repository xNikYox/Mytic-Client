package de.myticlegacy.client.invsee;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.GuiGraphics;
import de.myticlegacy.client.gui.ModMenuScreen;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.Widgets;
import de.myticlegacy.client.hud.HudModule;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.CustomSetting;
import de.myticlegacy.client.setting.ItemListSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * InvSee-Übersicht für 1.8.9: beim /invsee ein Glas-Panel neben dem Inventar mit gezählten Items (z. B. Goldäpfel,
 * Wurftränke der Heilung) und Rüstung samt Haltbarkeit und Schutzart; HUD mit dem letzten /invsee; Taste I wiederholt
 * das letzte /invsee unsichtbar. Basiert auf „InvSee Summary“ von Noah (MIT-Lizenz).
 */
public class InvSeeModule extends HudModule {
    private static final Pattern INVSEE_COMMAND = Pattern.compile("^(?:[\\w-]+:)?(?:invsee|openinv|inv|inventorysee|seeinv|isee)\\s+(\\S+).*$", Pattern.CASE_INSENSITIVE);
    private static final long INVSEE_WINDOW_MS = 5000;
    // eigene Listen für 1.8.9 (andere Items als in den neuen Versionen)
    private static final String[] DEFAULT_ITEMS = {"minecraft:golden_apple@1", "minecraft:golden_apple@0", "minecraft:potion@16421", "minecraft:ender_pearl"};
    private static final String LAST_COMMAND = "invsee.lastCommand", OFFSETS = "invsee.offsets189";
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
    private KeyBinding repeatKey, configKey;
    private boolean chatDrag;
    private double chatMouseX, chatMouseY;
    private int chatStartX, chatStartY;

    public InvSeeModule() {
        super("invsee", "InvSee-Übersicht", "Beim /invsee: gezählte Items und Rüstung neben dem Inventar, HUD mit dem letzten /invsee",
                () -> Items.golden_apple, false, 4, 200);
        instance = this;
        summary = new BoolSetting(this, "summary", "Übersicht neben dem Inventar", true);
        hud = new BoolSetting(this, "hud", "HUD mit dem letzten /invsee", true);
        itemNames = new BoolSetting(this, "names", "Item-Namen anzeigen", true);
        showArmor = new BoolSetting(this, "armor", "Rüstung mit Haltbarkeit und Schutzart", true);
        everyChest = new BoolSetting(this, "everyChest", "Auch bei jeder Kiste", false);
        layout = new ModeSetting(this, "layout", "Anordnung im Inventar", "Liste", "Liste", "Einzeln");
        overlayScale = new SliderSetting(this, "overlayScale", "Größe im Inventar", 50, 200, 5, 100, " %");
        items = new ItemListSetting(this, "items189", "Gezählte Items (Inventar)", 12, () -> true, DEFAULT_ITEMS);
        hudSameItems = new BoolSetting(this, "hudSame", "HUD zählt dieselben Items", false);
        hudItems = new ItemListSetting(this, "hudItems189", "Gezählte Items (HUD)", 12, () -> !hudSameItems.get(), DEFAULT_ITEMS);
        new CustomSetting<Boolean>(this, "actions", "Aktionen", false) {
            @Override
            public Boolean get() {
                return false;
            }

            @Override
            public void reset() {
            }

            @Override
            public float height() {
                return 62;
            }

            @Override
            public void draw(Kit kit, float x, float y, float w) {
                float d = kit.d, bh = 38 * d, by = y + 12 * d;
                float bw = kit.button("invsee:reset", x, by, bh, "Positionen zurücksetzen", "move", Widgets.SECONDARY, new Runnable() {
                    public void run() {
                        MyticClient.config().settings.remove(OFFSETS);
                        resetPosition();
                        MyticClient.config().save();
                    }
                });
                kit.button("invsee:clear", x + bw + 8 * d, by, bh, "HUD leeren", "trash", Widgets.GHOST, new Runnable() {
                    public void run() {
                        last = null;
                    }
                });
            }
        };
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
        repeatKey = Compat.registerKey("key.myticclient.invsee_repeat", Keyboard.KEY_I);
        configKey = Compat.registerKey("key.myticclient.invsee_config", 0);
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override
            public String getCommandName() {
                return "invseesummary";
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/invseesummary [clear]";
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {
                if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
                    last = null;
                    sender.addChatMessage(new ChatComponentText("§8[§dMytic§8] §7InvSee-HUD geleert."));
                } else {
                    openMenuNextTick = true;
                }
            }

            @Override
            public boolean canCommandSenderUseCommand(ICommandSender sender) {
                return true;
            }

            @Override
            public int getRequiredPermissionLevel() {
                return 0;
            }
        });
    }

    private static String lastCommand() {
        Object v = MyticClient.config().settings.get(LAST_COMMAND);
        return v instanceof String ? (String) v : null;
    }

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

    public void tick(Minecraft mc) {
        while (repeatKey.isPressed()) if (enabled()) repeat(mc);
        while (configKey.isPressed()) openMenuNextTick = true;
        if (openMenuNextTick) {
            openMenuNextTick = false;
            Compat.setScreen(new ModMenuScreen(null, this));
        }
        InvSeeAutoClose.tick(mc);
    }

    private void repeat(Minecraft mc) {
        if (mc.thePlayer == null || mc.currentScreen != null) return;
        String cmd = lastCommand();
        if (cmd == null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§8[§dMytic§8] §cNoch kein /invsee benutzt"));
            return;
        }
        mc.thePlayer.sendChatMessage("/" + cmd);
        onCommand(cmd);
        autoClosePending = true;
    }

    // ------------------------------------------------------------------------------------------ Ereignisse

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiContainer) || event.gui instanceof GuiInventory || event.gui instanceof GuiContainerCreative) return;
        GuiContainer container = (GuiContainer) event.gui;
        if (InvSeeOverlay.of(container) != null || !enabled()) return;
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
            String title = container.inventorySlots.inventorySlots.isEmpty() ? "Kiste"
                    : container.inventorySlots.getSlot(0).inventory.getDisplayName().getUnformattedText();
            InvSeeOverlay.attach(container, title, false);
        }
    }

    @SubscribeEvent
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (InvSeeAutoClose.isHidden(event.gui)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.gui instanceof GuiContainer) {
            InvSeeOverlay o = InvSeeOverlay.of((GuiContainer) event.gui);
            if (o != null) o.render(event.mouseX, event.mouseY);
        } else if (event.gui instanceof GuiChat && chatDrag) {
            moveTo(chatStartX + (int) Math.round(event.mouseX - chatMouseX), chatStartY + (int) Math.round(event.mouseY - chatMouseY));
        }
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        int button = Mouse.getEventButton();
        if (button != 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        double x = Mouse.getEventX() * event.gui.width / (double) mc.displayWidth;
        double y = event.gui.height - Mouse.getEventY() * event.gui.height / (double) mc.displayHeight - 1;
        boolean down = Mouse.getEventButtonState();
        if (event.gui instanceof GuiContainer) {
            InvSeeOverlay o = InvSeeOverlay.of((GuiContainer) event.gui);
            if (o == null) return;
            if (down ? o.startDrag(x, y) : o.stopDrag()) event.setCanceled(true);
        } else if (event.gui instanceof GuiChat) {
            if (down) {
                if (!enabled() || !hasContent() || x < x() || y < y() || x >= x() + width() || y >= y() + height()) return;
                chatDrag = true;
                chatMouseX = x;
                chatMouseY = y;
                chatStartX = x();
                chatStartY = y();
                event.setCanceled(true);
            } else if (chatDrag) {
                chatDrag = false;
                MyticClient.config().save();
                event.setCanceled(true);
            }
        }
    }

    /** Enter im Chat: gesendeten Befehl erkennen (1.8.9 hat dafür kein eigenes Ereignis) */
    @SubscribeEvent
    public void onKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || !Keyboard.getEventKeyState()) return;
        int key = Keyboard.getEventKey();
        if (key != Keyboard.KEY_RETURN && key != Keyboard.KEY_NUMPADENTER) return;
        try {
            GuiTextField field = ReflectionHelper.getPrivateValue(GuiChat.class, (GuiChat) event.gui, "inputField", "field_146415_a");
            String text = field == null ? null : field.getText().trim();
            if (text != null && text.startsWith("/")) onCommand(text.substring(1));
        } catch (RuntimeException ignored) {
            // Feld nicht gefunden: dann eben ohne Erkennung
        }
    }

    // ------------------------------------------------------------------------------------------ Versatz im Inventar

    int[] offset(String key) {
        Object all = MyticClient.config().settings.get(OFFSETS);
        if (all instanceof Map) {
            Object v = ((Map<?, ?>) all).get(key);
            if (v instanceof List && ((List<?>) v).size() == 2 && ((List<?>) v).get(0) instanceof Number && ((List<?>) v).get(1) instanceof Number) {
                return new int[]{((Number) ((List<?>) v).get(0)).intValue(), ((Number) ((List<?>) v).get(1)).intValue()};
            }
        }
        return new int[]{0, 0};
    }

    @SuppressWarnings("unchecked")
    void setOffset(String key, int x, int y) {
        Object all = MyticClient.config().settings.get(OFFSETS);
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        if (all instanceof Map) map.putAll((Map<String, Object>) all);
        map.put(key, Arrays.asList(x, y));
        MyticClient.config().settings.put(OFFSETS, map);
    }

    // ------------------------------------------------------------------------------------------ HUD

    private List<String> hudList() {
        return hudSameItems.get() ? items.get() : hudItems.get();
    }

    private InvSeeData.Snapshot shown(boolean preview) {
        if (last != null || !preview) return last;
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        stacks.add(new ItemStack(Items.golden_apple, 12, 0));
        stacks.add(new ItemStack(Items.golden_apple, 2, 1));
        stacks.add(new ItemStack(Items.diamond_helmet));
        ItemStack chest = new ItemStack(Items.diamond_chestplate);
        chest.setItemDamage(chest.getMaxDamage() / 3);
        stacks.add(chest);
        return InvSeeData.snapshot("Noah", stacks);
    }

    private List<InvSeeData.Row> rows(InvSeeData.Snapshot s) {
        List<InvSeeData.Row> rows = InvSeeData.itemRows(hudList(), s.stacks);
        if (showArmor.get()) rows.addAll(InvSeeData.armorRows(s.armor));
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

    private float[] size() {
        long now = System.nanoTime();
        if (sizeValue != null && now - sizeAt < 50_000_000L) return sizeValue;
        sizeAt = now;
        InvSeeData.Snapshot s = shown(true);
        List<InvSeeData.Row> rows = rows(s);
        float k = Compat.guiScale() * scaleFactor();
        sizeValue = new float[]{InvSeePanel.width(s.player, right(true), rows, itemNames.get(), k), InvSeePanel.height(rows)};
        return sizeValue;
    }

    @Override
    public boolean hasContent() {
        return hud.get() && last != null;
    }

    @Override
    public int baseWidth() {
        return Math.round(size()[0]);
    }

    @Override
    public int baseHeight() {
        return Math.round(size()[1]);
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        InvSeeData.Snapshot s = shown(preview);
        if (s == null) return;
        List<InvSeeData.Row> rows = rows(s);
        float k = de.myticlegacy.client.gui.ui.Draw.k(g);
        float w = InvSeePanel.width(s.player, right(preview), rows, itemNames.get(), k);
        InvSeePanel.drawList(g, s.player, right(preview), rows, w, itemNames.get(), background.get() ? (int) Math.round(backgroundOpacity.get()) : 0);
    }
}
