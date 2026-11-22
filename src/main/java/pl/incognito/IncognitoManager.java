package pl.incognito;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Trzyma stan incognito graczy i historie. Uprawnien nie rusza - zmienia tylko
 * nick (czat, tab, nad glowa) i prefix.
 */
public final class IncognitoManager {

    public static final class Entry {
        public final String nick;
        public final long start;
        public long end; // 0 = nadal aktywne

        Entry(String nick, long start, long end) {
            this.nick = nick;
            this.start = start;
            this.end = end;
        }
    }

    private static final class Data {
        String realName;
        final List<Entry> history = new ArrayList<>();

        Entry active() {
            if (history.isEmpty()) {
                return null;
            }
            Entry last = history.get(history.size() - 1);
            return last.end == 0 ? last : null;
        }
    }

    private final IncognitoPlugin plugin;
    private final File file;
    private final Map<UUID, Data> data = new ConcurrentHashMap<>();
    private final TabHook tabHook;
    private volatile Component prefix = Component.empty();

    public IncognitoManager(IncognitoPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.tabHook = new TabHook(plugin, this);
        reloadPrefix();
    }

    public void reloadPrefix() {
        prefix = IncognitoPlugin.LEGACY.deserialize(plugin.getConfig().getString("prefix", ""));
    }

    public Component prefix() {
        return prefix;
    }

    // ---------------------------------------------------------------- stan

    public boolean isActive(UUID id) {
        return nickOf(id) != null;
    }

    /** Aktualny nick incognito albo null, gdy tryb jest wylaczony. */
    public String nickOf(UUID id) {
        Data d = data.get(id);
        if (d == null) {
            return null;
        }
        Entry a = d.active();
        return a == null ? null : a.nick;
    }

    public List<Entry> historyNewestFirst(UUID id) {
        Data d = data.get(id);
        if (d == null) {
            return new ArrayList<>();
        }
        List<Entry> copy = new ArrayList<>(d.history);
        Collections.reverse(copy);
        return copy;
    }

    /** Wolane przy wejsciu gracza - wtedy getName() to jeszcze prawdziwy nick. */
    public void onJoin(Player player) {
        Data d = data.get(player.getUniqueId());
        if (d != null) {
            d.realName = player.getName();
            save();
        }
    }

    // ------------------------------------------------------ wlacz / wylacz

    /** Wlacza incognito z podanym nickiem (jesli juz bylo wlaczone - podmienia nick). */
    public void enable(Player player, String nick) {
        Data d = data.computeIfAbsent(player.getUniqueId(), k -> new Data());
        Entry current = d.active();
        if (current == null || d.realName == null) {
            d.realName = player.getName();
        }
        long now = System.currentTimeMillis();
        if (current != null) {
            current.end = now;
        }
        d.history.add(new Entry(nick, now, 0));

        int limit = Math.max(1, plugin.getConfig().getInt("history-limit", 100));
        while (d.history.size() > limit) {
            d.history.remove(0);
        }
        save();
        applyVisuals(player, nick);
    }

    /** @return false, jesli incognito nie bylo wlaczone */
    public boolean disable(Player player) {
        Data d = data.get(player.getUniqueId());
        Entry current = d == null ? null : d.active();
        if (current == null) {
            return false;
        }
        current.end = System.currentTimeMillis();
        save();
        restoreVisuals(player);
        return true;
    }

    /** Usuwa zakonczone wpisy, aktywny zostaje. */
    public void clearHistory(UUID id) {
        Data d = data.get(id);
        if (d == null) {
            return;
        }
        Entry active = d.active();
        d.history.clear();
        if (active != null) {
            d.history.add(active);
        }
        save();
    }

    // -------------------------------------------------------------- wyglad

    public void reapply(Player player) {
        String nick = nickOf(player.getUniqueId());
        if (nick != null) {
            applyVisuals(player, nick);
        }
    }

    public void applyVisuals(Player player, String nick) {
        player.displayName(Component.text(nick));
        player.playerListName(prefix.append(Component.text(nick)));
        if (plugin.getConfig().getBoolean("change-real-name", true)) {
            setProfileName(player, nick);
        }
        tabHook.apply(player, nick);
    }

    public void restoreVisuals(Player player) {
        Data d = data.get(player.getUniqueId());
        if (d == null || d.realName == null) {
            return;
        }
        player.displayName(null);
        player.playerListName(null);
        if (plugin.getConfig().getBoolean("change-real-name", true)) {
            setProfileName(player, d.realName);
        }
        tabHook.reset(player);
    }

    private void setProfileName(Player player, String name) {
        try {
            PlayerProfile old = player.getPlayerProfile();
            PlayerProfile fresh = Bukkit.createProfile(player.getUniqueId(), name);
            Set<ProfileProperty> props = new HashSet<>(old.getProperties());
            fresh.setProperties(props); // zachowuje skina
            player.setPlayerProfile(fresh);
            plugin.getLogger().info("Profil gracza " + player.getUniqueId() + ": ustawiono nick '" + name
                    + "', odczytany z profilu: '" + player.getPlayerProfile().getName() + "'");

            // jesli inny plugin (np. SkinsRestorer) przywroci stary nick, zaloguj to i ustaw ponownie
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    String expected = nickOf(player.getUniqueId());
                    if (expected == null) {
                        expected = realNameOf(player.getUniqueId());
                    }
                    if (!name.equals(expected)) {
                        return; // w miedzyczasie zmieniono tryb
                    }
                    String actual = player.getPlayerProfile().getName();
                    if (!name.equals(actual)) {
                        plugin.getLogger().warning("Nick w profilu zostal zmieniony z '" + name + "' na '"
                                + actual + "' przez inny plugin - ustawiam ponownie.");
                        setProfileNameQuiet(player, name);
                    }
                }, 20L);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "Nie udalo sie zmienic nicku nad glowa (zmieniony zostaje tylko czat/tab): " + t);
        }
    }

    private String realNameOf(UUID id) {
        Data d = data.get(id);
        return d == null ? null : d.realName;
    }

    /** Ustawia nick w profilu bez dalszych kontroli i logow (ponowna proba). */
    private void setProfileNameQuiet(Player player, String name) {
        try {
            PlayerProfile old = player.getPlayerProfile();
            PlayerProfile fresh = Bukkit.createProfile(player.getUniqueId(), name);
            fresh.setProperties(new HashSet<>(old.getProperties()));
            player.setPlayerProfile(fresh);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Ponowne ustawienie nicku w profilu nie powiodlo sie: " + t);
        }
    }

    // --------------------------------------------------------------- nicki

    public static boolean isValidNick(String nick) {
        return nick.matches("[A-Za-z0-9_]{3,16}");
    }

    public boolean isFree(String nick, UUID self) {
        Player online = Bukkit.getPlayerExact(nick);
        if (online != null && !online.getUniqueId().equals(self)) {
            return false;
        }
        for (Map.Entry<UUID, Data> e : data.entrySet()) {
            if (e.getKey().equals(self)) {
                continue;
            }
            Data d = e.getValue();
            Entry a = d.active();
            if (a != null && a.nick.equalsIgnoreCase(nick)) {
                return false;
            }
            if (d.realName != null && d.realName.equalsIgnoreCase(nick)) {
                return false;
            }
        }
        OfflinePlayer known = Bukkit.getOfflinePlayerIfCached(nick);
        return known == null || known.getUniqueId().equals(self);
    }

    /** Losowy nick w stylu ukryty16245 / macius1246pl, max 16 znakow. */
    public String generate(UUID self) {
        FileConfiguration c = plugin.getConfig();
        List<String> words = c.getStringList("nick.words");
        if (words.isEmpty()) {
            words = List.of("gracz");
        }
        int min = c.getInt("nick.min-number", 10);
        int max = Math.max(min, c.getInt("nick.max-number", 99999));
        int chance = c.getInt("nick.chance-pl", 40);
        ThreadLocalRandom r = ThreadLocalRandom.current();

        for (int i = 0; i < 100; i++) {
            String word = words.get(r.nextInt(words.size()));
            String number = String.valueOf(r.nextInt(min, max + 1));
            String suffix = r.nextInt(100) < chance ? "pl" : "";
            int budget = 16 - number.length() - suffix.length();
            if (budget < 1) {
                continue;
            }
            if (word.length() > budget) {
                word = word.substring(0, budget);
            }
            String nick = word + number + suffix;
            if (isValidNick(nick) && isFree(nick, self)) {
                return nick;
            }
        }
        return "gracz" + r.nextInt(10000, 99999);
    }

    // ------------------------------------------------------------- zapis

    public void load() {
        data.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Data d = new Data();
            d.realName = players.getString(key + ".real-name");
            for (Map<?, ?> m : players.getMapList(key + ".history")) {
                Object nick = m.get("nick");
                Object start = m.get("start");
                Object end = m.get("end");
                if (nick == null || !(start instanceof Number)) {
                    continue;
                }
                d.history.add(new Entry(String.valueOf(nick), ((Number) start).longValue(),
                        end instanceof Number ? ((Number) end).longValue() : 0L));
            }
            data.put(id, d);
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Data> e : data.entrySet()) {
            String base = "players." + e.getKey();
            Data d = e.getValue();
            yaml.set(base + ".real-name", d.realName);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Entry h : d.history) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("nick", h.nick);
                m.put("start", h.start);
                m.put("end", h.end);
                list.add(m);
            }
            yaml.set(base + ".history", list);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Nie udalo sie zapisac data.yml", ex);
        }
    }
}
