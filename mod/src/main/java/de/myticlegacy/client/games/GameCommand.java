package de.myticlegacy.client.games;

import com.mojang.brigadier.arguments.StringArgumentType;
import de.myticlegacy.client.friends.Friends;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * /myticgame – Mytic Games im Chat (nur auf dem eigenen Client, funktioniert auf jedem Server):
 *   /myticgame <name>       Schach-Herausforderung (Fenster mit Bedenkzeit und Farbe)
 *   /myticgame annehmen     neueste Herausforderung annehmen
 *   /myticgame ablehnen     neueste Herausforderung ablehnen
 *   /myticgame              laufende Partie öffnen
 */
public final class GameCommand {
    private GameCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommandManager.literal("myticgame")
                        .executes(ctx -> open())
                        .then(ClientCommandManager.literal("annehmen").executes(ctx -> accept()))
                        .then(ClientCommandManager.literal("ablehnen").executes(ctx -> decline()))
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    // Spieler auf dem Server (Tab-Liste) und Freunde
                                    Set<String> names = new LinkedHashSet<>(ctx.getSource().getOnlinePlayerNames());
                                    for (Friends.Friend f : Friends.friends()) if (f.name != null) names.add(f.name);
                                    return SharedSuggestionProvider.suggest(names, builder);
                                })
                                .executes(ctx -> challenge(StringArgumentType.getString(ctx, "name"))))));
    }

    private static boolean ready() {
        if (Games.available()) return true;
        Games.notice("§7Mytic Games braucht die Freundesliste – starte das Spiel über den Mytic Launcher.");
        return false;
    }

    private static int open() {
        if (!ready()) return 0;
        Games.Game active = Games.active();
        if (active != null) {
            Games.openLater(Games.screenFor(active));
            return 1;
        }
        Games.Game invite = Games.incomingInvite();
        if (invite != null) Games.notice("§d" + invite.inviterName + "§7 wartet auf dich – §f/myticgame annehmen");
        else Games.notice("§7Keine laufende Partie. Herausfordern: §f/myticgame <name>");
        return 1;
    }

    private static int challenge(String name) {
        if (!ready()) return 0;
        if (name.equalsIgnoreCase(net.minecraft.client.Minecraft.getInstance().getUser().getName())) {
            Games.notice("§7Du kannst nicht gegen dich selbst spielen.");
            return 0;
        }
        Games.openLater(new ChallengeScreen(name));
        return 1;
    }

    private static int accept() {
        if (!ready()) return 0;
        Games.Game invite = Games.incomingInvite();
        if (invite == null) {
            Games.notice("§7Keine offene Herausforderung.");
            return 0;
        }
        Games.accept(invite.id, game -> Games.openLater(Games.screenFor(game)), error -> Games.notice("§c" + error));
        return 1;
    }

    private static int decline() {
        if (!ready()) return 0;
        Games.Game invite = Games.incomingInvite();
        if (invite == null) {
            Games.notice("§7Keine offene Herausforderung.");
            return 0;
        }
        Games.decline(invite.id);
        Games.notice("§7Herausforderung von §d" + invite.inviterName + "§7 abgelehnt.");
        return 1;
    }
}
