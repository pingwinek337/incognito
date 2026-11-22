package pl.incognito;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class IncognitoCommand implements CommandExecutor, TabCompleter {

    private final IncognitoPlugin plugin;
    private final IncognitoManager manager;
    private final IncognitoGui gui;

    public IncognitoCommand(IncognitoPlugin plugin, IncognitoManager manager, IncognitoGui gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

        if (sub.equals("reload")) {
            if (!sender.hasPermission("incognito.admin")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            plugin.reloadConfig();
            manager.reloadPrefix();
            sender.sendMessage(plugin.msg("reloaded"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("incognito.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        UUID id = player.getUniqueId();
        switch (sub) {
            case "" -> {
                if (manager.isActive(id)) {
                    manager.disable(player);
                    player.sendMessage(plugin.msg("disabled"));
                } else {
                    enableRandom(player);
                }
            }
            case "on" -> enableRandom(player);
            case "off" -> {
                if (manager.disable(player)) {
                    player.sendMessage(plugin.msg("disabled"));
                } else {
                    player.sendMessage(plugin.msg("already-off"));
                }
            }
            case "gui", "menu", "historia" -> gui.open(player, 0);
            case "nick" -> {
                if (!player.hasPermission("incognito.nick")) {
                    player.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                if (args.length < 2 || !IncognitoManager.isValidNick(args[1])) {
                    player.sendMessage(plugin.msg("nick-invalid"));
                    return true;
                }
                if (!manager.isFree(args[1], id)) {
                    player.sendMessage(plugin.msg("nick-taken", args[1]));
                    return true;
                }
                manager.enable(player, args[1]);
                player.sendMessage(plugin.msg("enabled", args[1]));
            }
            default -> player.sendMessage(plugin.msg("usage"));
        }
        return true;
    }

    private void enableRandom(Player player) {
        String nick = manager.generate(player.getUniqueId());
        manager.enable(player, nick);
        player.sendMessage(plugin.msg("enabled", nick));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>(List.of("gui", "on", "off"));
            if (sender.hasPermission("incognito.nick")) {
                options.add("nick");
            }
            if (sender.hasPermission("incognito.admin")) {
                options.add("reload");
            }
            for (String option : options) {
                if (option.startsWith(typed)) {
                    out.add(option);
                }
            }
        }
        return out;
    }
}
