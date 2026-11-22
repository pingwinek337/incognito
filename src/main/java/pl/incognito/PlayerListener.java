package pl.incognito;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class PlayerListener implements Listener {

    private final IncognitoPlugin plugin;
    private final IncognitoManager manager;

    public PlayerListener(IncognitoPlugin plugin, IncognitoManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        manager.onJoin(player);

        String nick = manager.nickOf(id);
        if (nick == null) {
            return;
        }
        event.joinMessage(Component.translatable("multiplayer.player.joined", Component.text(nick))
                .color(NamedTextColor.YELLOW));

        // lekkie opoznienie, zeby gracz zdazyl sie w pelni zaladowac
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String current = manager.nickOf(id);
            if (player.isOnline() && current != null) {
                manager.applyVisuals(player, current);
                player.sendMessage(plugin.msg("enabled", current));
            }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String nick = manager.nickOf(event.getPlayer().getUniqueId());
        if (nick != null) {
            event.quitMessage(Component.translatable("multiplayer.player.left", Component.text(nick))
                    .color(NamedTextColor.YELLOW));
        }
    }

    /** Na czacie: ranga incognito + losowy nick zamiast prawdziwej rangi i nicku. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        String nick = manager.nickOf(event.getPlayer().getUniqueId());
        if (nick == null) {
            return;
        }
        Component prefix = manager.prefix();
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                prefix.append(Component.text(nick, NamedTextColor.WHITE))
                        .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                        .append(message.colorIfAbsent(NamedTextColor.GRAY))));
    }
}
