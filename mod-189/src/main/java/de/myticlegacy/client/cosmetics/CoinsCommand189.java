package de.myticlegacy.client.cosmetics;

import de.myticlegacy.client.friends.Friends;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.ClientCommandHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * /mytic coins – Kontostand; für Admins /mytic coins give|take <Name> <Menge> [Grund] (1.8.9, Forge-Client-Befehl;
 * der Friends-Server prüft das Admin-Recht).
 */
public final class CoinsCommand189 {
    private CoinsCommand189() {
    }

    public static void register() {
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override
            public String getCommandName() {
                return "mytic";
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/mytic coins [give|take <Name> <Menge> [Grund]]";
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {
                if (args.length == 0 || !args[0].equalsIgnoreCase("coins")) {
                    Friends.notice("§7" + getCommandUsage(sender));
                    return;
                }
                if (args.length == 1) {
                    if (!Friends.available()) {
                        Friends.notice("§7Coins gibt es nur, wenn du über den Mytic Launcher startest.");
                        return;
                    }
                    int b = CoinsCore.balance();
                    Friends.notice(b < 0 ? "§7Kontostand wird geladen – gleich noch einmal." : "§6⛃ " + CoinsCore.fmt(b) + " Coins §8(Rechts-Shift → Kosmetik → Coins)");
                    return;
                }
                String word = args[1].toLowerCase(java.util.Locale.ROOT);
                if ((!word.equals("give") && !word.equals("take")) || args.length < 4) {
                    Friends.notice("§7/mytic coins give|take <Name> <Menge> [Grund]");
                    return;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    amount = 0;
                }
                if (amount < 1 || amount > 1000000) {
                    Friends.notice("§cMenge: 1 bis 1000000");
                    return;
                }
                StringBuilder reason = new StringBuilder();
                for (int i = 4; i < args.length; i++) reason.append(i > 4 ? " " : "").append(args[i]);
                if (!Friends.available()) {
                    Friends.notice("§7Nur möglich, wenn du über den Mytic Launcher startest.");
                    return;
                }
                if (!CosmeticsCore.isAdmin()) {
                    Friends.notice("§cNur für Admins.");
                    return;
                }
                CoinsCore.adminBook(args[2], word.equals("take") ? -amount : amount, reason.length() == 0 ? null : reason.toString(), new CoinsCore.Result() {
                    public void done(boolean ok, String message) {
                        Friends.notice((ok ? "§a" : "§c") + message);
                    }
                });
            }

            @Override
            public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
                if (args.length == 1) return getListOfStringsMatchingLastWord(args, "coins");
                if (args.length == 2) return getListOfStringsMatchingLastWord(args, "give", "take");
                if (args.length == 3) {
                    List<String> names = new ArrayList<String>();
                    for (Friends.Friend f : Friends.friends()) if (f.name != null) names.add(f.name);
                    return getListOfStringsMatchingLastWord(args, names);
                }
                return null;
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
}
