package pl.incognito;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Integracja z pluginem TAB (przez refleksje, wiec nie trzeba zadnej zaleznosci przy budowaniu).
 * TAB sam ustawia range nad glowa i w tabie, wiec bez tego pokazywalaby sie prawdziwa ranga.
 * Zmiany robione przez API TAB sa tymczasowe - nic nie zapisuje sie na stale w plikach TAB.
 */
public final class TabHook {

    private static final long[] RETRY_DELAYS = {2L, 20L, 60L, 120L};

    private final IncognitoPlugin plugin;
    private final IncognitoManager manager;

    private Object api;

    public TabHook(IncognitoPlugin plugin, IncognitoManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("tab-hook", true)
                && Bukkit.getPluginManager().getPlugin("TAB") != null;
    }

    private boolean init() {
        if (api != null) {
            return true;
        }
        try {
            Class<?> tabApi = Class.forName("me.neznamy.tab.api.TabAPI");
            api = tabApi.getMethod("getInstance").invoke(null);
            return api != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ustawia prefix incognito w TAB (nad glowa + tab). TAB laduje graczy z opoznieniem, wiec ponawia. */
    public void apply(Player player, String nick) {
        if (!enabled()) {
            return;
        }
        UUID id = player.getUniqueId();
        for (long delay : RETRY_DELAYS) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // gracz mogl w miedzyczasie wyjsc albo wylaczyc incognito / zmienic nick
                if (!player.isOnline() || !nick.equals(manager.nickOf(id))) {
                    return;
                }
                applyNow(id, nick);
            }, delay);
        }
    }

    /** Przywraca normalna range z TAB. */
    public void reset(Player player) {
        if (!enabled()) {
            return;
        }
        UUID id = player.getUniqueId();
        if (!plugin.isEnabled()) {
            resetNow(id); // wylaczanie pluginu - nie da sie juz planowac zadan
            return;
        }
        for (long delay : new long[]{1L, 20L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && manager.nickOf(id) == null) {
                    resetNow(id);
                }
            }, delay);
        }
    }

    private void applyNow(UUID id, String nick) {
        try {
            if (!init()) {
                return;
            }
            Object tabPlayer = tabPlayer(id);
            if (tabPlayer == null) {
                return; // TAB jeszcze nie zaladowal gracza - kolejna proba za chwile
            }
            String prefix = plugin.getConfig().getString("prefix", "");

            Object nameTags = manager(api, "getNameTagManager");
            if (nameTags != null) {
                boolean ok = call(nameTags, "setPrefix", tabPlayer, prefix);
                call(nameTags, "setSuffix", tabPlayer, "");
                if (!ok) {
                    plugin.getLogger().warning("TAB: nie znaleziono NameTagManager#setPrefix");
                }
            }
            Object tabList = manager(api, "getTabListFormatManager");
            if (tabList != null) {
                call(tabList, "setPrefix", tabPlayer, prefix);
                call(tabList, "setSuffix", tabPlayer, "");
                call(tabList, "setName", tabPlayer, nick);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Integracja z TAB nie powiodla sie: " + t);
        }
    }

    private void resetNow(UUID id) {
        try {
            if (!init()) {
                return;
            }
            Object tabPlayer = tabPlayer(id);
            if (tabPlayer == null) {
                return;
            }
            // w API TAB reset = przekazanie null do settera (starsze wersje maja resetX)
            Object nameTags = manager(api, "getNameTagManager");
            if (nameTags != null) {
                clear(nameTags, "Prefix", tabPlayer);
                clear(nameTags, "Suffix", tabPlayer);
            }
            Object tabList = manager(api, "getTabListFormatManager");
            if (tabList != null) {
                clear(tabList, "Prefix", tabPlayer);
                clear(tabList, "Suffix", tabPlayer);
                clear(tabList, "Name", tabPlayer);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Przywracanie rangi w TAB nie powiodlo sie: " + t);
        }
    }

    // ------------------------------------------------------------ refleksja

    private Object tabPlayer(UUID id) throws Exception {
        Method getPlayer = api.getClass().getMethod("getPlayer", UUID.class);
        getPlayer.setAccessible(true);
        return getPlayer.invoke(api, id);
    }

    private static Object manager(Object target, String getter) {
        try {
            Method m = target.getClass().getMethod(getter);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) {
            return null; // ta funkcja TAB jest wylaczona albo nie istnieje w tej wersji
        }
    }

    /** Przywraca wartosc z TAB: setX(player, null), a gdy nie ma - resetX(player). */
    private void clear(Object target, String what, Object tabPlayer) throws Exception {
        if (!call(target, "set" + what, tabPlayer, null)) {
            if (!call(target, "reset" + what, tabPlayer)) {
                plugin.getLogger().warning("TAB: nie znaleziono metody do przywrocenia '" + what + "'");
            }
        }
    }

    /** @return true, jesli znaleziono pasujaca metode i ja wywolano */
    private static boolean call(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] types = m.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < types.length; i++) {
                boolean ok = args[i] == null ? !types[i].isPrimitive() : types[i].isInstance(args[i]);
                if (!ok) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                m.setAccessible(true);
                m.invoke(target, args);
                return true;
            }
        }
        return false;
    }
}
