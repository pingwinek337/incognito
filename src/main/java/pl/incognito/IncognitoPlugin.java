package pl.incognito;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class IncognitoPlugin extends JavaPlugin {

    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private IncognitoManager manager;
    private IncognitoGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        manager = new IncognitoManager(this);
        manager.load();
        gui = new IncognitoGui(this, manager);

        IncognitoCommand command = new IncognitoCommand(this, manager, gui);
        PluginCommand pluginCommand = getCommand("incognito");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this, manager), this);
        getServer().getPluginManager().registerEvents(gui, this);

        // po /reload: gracze, ktorzy sa online i maja incognito, dostaja nick ponownie
        for (Player player : Bukkit.getOnlinePlayers()) {
            manager.reapply(player);
        }
    }

    @Override
    public void onDisable() {
        if (manager == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (manager.isActive(player.getUniqueId())) {
                manager.restoreVisuals(player);
            }
        }
        manager.save();
    }

    /** Wiadomosc z config.yml (sekcja messages), {nick} jest podmieniany. */
    public Component msg(String key, String nick) {
        String raw = getConfig().getString("messages." + key, key);
        return LEGACY.deserialize(raw.replace("{nick}", nick == null ? "" : nick));
    }

    public Component msg(String key) {
        return msg(key, null);
    }
}
