package de.myticlegacy.client.cosmetics;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.myticlegacy.client.friends.Friends;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * /mytic coins – Kontostand; für Admins /mytic coins give|take <Name> <Menge> [Grund] (nur auf dem eigenen Client,
 * funktioniert auf jedem Server; der Friends-Server prüft das Admin-Recht).
 */
public final class CoinsCommand {
    private CoinsCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommandManager.literal("mytic").then(ClientCommandManager.literal("coins")
                        .executes(ctx -> show())
                        .then(book("give", 1))
                        .then(book("take", -1)))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> book(String word, final int sign) {
        return ClientCommandManager.literal(word).then(ClientCommandManager.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    Set<String> names = new LinkedHashSet<>(ctx.getSource().getOnlinePlayerNames());
                    for (Friends.Friend f : Friends.friends()) if (f.name != null) names.add(f.name);
                    return SharedSuggestionProvider.suggest(names, builder);
                })
                .then(ClientCommandManager.argument("menge", IntegerArgumentType.integer(1, 1_000_000))
                        .executes(ctx -> run(StringArgumentType.getString(ctx, "name"), sign * IntegerArgumentType.getInteger(ctx, "menge"), null))
                        .then(ClientCommandManager.argument("grund", StringArgumentType.greedyString())
                                .executes(ctx -> run(StringArgumentType.getString(ctx, "name"), sign * IntegerArgumentType.getInteger(ctx, "menge"),
                                        StringArgumentType.getString(ctx, "grund"))))));
    }

    private static int show() {
        if (!Friends.available()) {
            Friends.notice("§7Coins gibt es nur, wenn du über den Mytic Launcher startest.");
            return 0;
        }
        int b = CoinsCore.balance();
        Friends.notice(b < 0 ? "§7Kontostand wird geladen – gleich noch einmal." : "§6⛃ " + CoinsCore.fmt(b) + " Coins §8(Rechts-Shift → Kosmetik → Coins)");
        return 1;
    }

    /** Geben/Nehmen (Admin) – dieselbe Logik wie im Reiter „Admin“ */
    public static int run(String name, int delta, String reason) {
        if (!Friends.available()) {
            Friends.notice("§7Nur möglich, wenn du über den Mytic Launcher startest.");
            return 0;
        }
        if (!CosmeticsCore.isAdmin()) {
            Friends.notice("§cNur für Admins.");
            return 0;
        }
        if (CoinsCore.busy()) {
            Friends.notice("§7Einen Moment – die letzte Buchung läuft noch.");
            return 0;
        }
        CoinsCore.adminBook(name, delta, reason, (ok, msg) -> Friends.notice((ok ? "§a" : "§c") + msg));
        return 1;
    }
}
