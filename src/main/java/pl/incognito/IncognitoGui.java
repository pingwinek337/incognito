package pl.incognito;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Menu: przycisk wlacz/wylacz, losowanie nicku i historia uzytych nickow (ze stronami). */
public final class IncognitoGui implements Listener {

    private static final int SIZE = 54;
    private static final int FIRST_HISTORY_SLOT = 18;
    private static final int PER_PAGE = 27; // sloty 18-44

    private static final int SLOT_REROLL = 2;
    private static final int SLOT_TOGGLE = 4;
    private static final int SLOT_CLEAR = 6;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 53;

    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        int page;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final IncognitoPlugin plugin;
    private final IncognitoManager manager;

    public IncognitoGui(IncognitoPlugin plugin, IncognitoManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void open(Player player, int page) {
        Holder holder = new Holder();
        holder.page = page;
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Component.text("Incognito", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;
        render(inventory, holder, player);
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------ rysowanie

    private void render(Inventory inv, Holder holder, Player player) {
        UUID id = player.getUniqueId();
        String active = manager.nickOf(id);
        List<IncognitoManager.Entry> history = manager.historyNewestFirst(id);
        int pages = Math.max(1, (history.size() + PER_PAGE - 1) / PER_PAGE);
        holder.page = Math.max(0, Math.min(holder.page, pages - 1));

        inv.clear();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 18; i++) {
            inv.setItem(i, filler);
        }
        for (int i = 45; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(SLOT_REROLL, item(Material.NETHER_STAR,
                Component.text("Losuj nowy nick", NamedTextColor.AQUA),
                List.of(Component.text("Wlacza incognito z nowym", NamedTextColor.GRAY),
                        Component.text("losowym nickiem.", NamedTextColor.GRAY))));

        if (active != null) {
            inv.setItem(SLOT_TOGGLE, item(Material.LIME_DYE,
                    Component.text("Incognito: WLACZONE", NamedTextColor.GREEN),
                    List.of(Component.text("Twoj nick: ", NamedTextColor.GRAY)
                                    .append(Component.text(active, NamedTextColor.WHITE)),
                            Component.empty(),
                            Component.text("Kliknij, aby wylaczyc", NamedTextColor.YELLOW))));
        } else {
            inv.setItem(SLOT_TOGGLE, item(Material.GRAY_DYE,
                    Component.text("Incognito: WYLACZONE", NamedTextColor.RED),
                    List.of(Component.text("Kliknij, aby wlaczyc", NamedTextColor.YELLOW),
                            Component.text("(losowy nick)", NamedTextColor.GRAY))));
        }

        inv.setItem(SLOT_CLEAR, item(Material.BARRIER,
                Component.text("Wyczysc historie", NamedTextColor.RED),
                List.of(Component.text("Usuwa zakonczone wpisy.", NamedTextColor.GRAY))));

        int from = holder.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < history.size(); i++) {
            inv.setItem(FIRST_HISTORY_SLOT + i, historyItem(history.get(from + i)));
        }

        inv.setItem(SLOT_PAGE, item(Material.BOOK,
                Component.text("Strona " + (holder.page + 1) + "/" + pages, NamedTextColor.WHITE),
                List.of(Component.text("Historia: " + history.size() + " wpisow", NamedTextColor.GRAY))));
        if (holder.page > 0) {
            inv.setItem(SLOT_PREV, item(Material.ARROW,
                    Component.text("Poprzednia strona", NamedTextColor.YELLOW), List.of()));
        }
        if (holder.page < pages - 1) {
            inv.setItem(SLOT_NEXT, item(Material.ARROW,
                    Component.text("Nastepna strona", NamedTextColor.YELLOW), List.of()));
        }
    }

    private ItemStack historyItem(IncognitoManager.Entry entry) {
        boolean isActive = entry.end == 0;
        long until = isActive ? System.currentTimeMillis() : entry.end;

        List<Component> lore = new ArrayList<>();
        lore.add(line("Wlaczono: ", format(entry.start)));
        lore.add(line("Wylaczono: ", isActive ? "aktywne teraz" : format(entry.end)));
        lore.add(line("Czas: ", duration(until - entry.start)));
        lore.add(Component.empty());
        lore.add(isActive
                ? Component.text("Aktualnie uzywany nick", NamedTextColor.GREEN)
                : Component.text("Kliknij, aby uzyc ponownie", NamedTextColor.YELLOW));

        return item(isActive ? Material.EMERALD : Material.NAME_TAG,
                Component.text(entry.nick, NamedTextColor.WHITE), lore);
    }

    private Component line(String label, String value) {
        return Component.text(label, NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private String format(long millis) {
        ZoneId zone;
        try {
            zone = ZoneId.of(plugin.getConfig().getString("timezone", "Europe/Warsaw"));
        } catch (Exception ex) {
            zone = ZoneId.systemDefault();
        }
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(zone)
                .format(Instant.ofEpochMilli(millis));
    }

    private String duration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return h + "h " + m + "min";
        }
        if (m > 0) {
            return m + "min " + s + "s";
        }
        return s + "s";
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> fixed = new ArrayList<>();
        for (Component c : lore) {
            fixed.add(c.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(fixed);
        stack.setItemMeta(meta);
        return stack;
    }

    // ------------------------------------------------------------- klikniecia

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        if (!player.hasPermission("incognito.use")) {
            player.closeInventory();
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }
        UUID id = player.getUniqueId();

        if (slot == SLOT_TOGGLE) {
            if (manager.isActive(id)) {
                manager.disable(player);
                player.sendMessage(plugin.msg("disabled"));
            } else {
                enable(player, manager.generate(id));
            }
        } else if (slot == SLOT_REROLL) {
            enable(player, manager.generate(id));
        } else if (slot == SLOT_CLEAR) {
            manager.clearHistory(id);
            holder.page = 0;
            player.sendMessage(plugin.msg("history-cleared"));
        } else if (slot == SLOT_PREV) {
            holder.page--;
        } else if (slot == SLOT_NEXT) {
            holder.page++;
        } else if (slot >= FIRST_HISTORY_SLOT && slot < FIRST_HISTORY_SLOT + PER_PAGE) {
            List<IncognitoManager.Entry> history = manager.historyNewestFirst(id);
            int index = holder.page * PER_PAGE + (slot - FIRST_HISTORY_SLOT);
            if (index < history.size()) {
                IncognitoManager.Entry entry = history.get(index);
                if (entry.end != 0) {
                    if (manager.isFree(entry.nick, id)) {
                        enable(player, entry.nick);
                    } else {
                        player.sendMessage(plugin.msg("nick-taken", entry.nick));
                    }
                }
            }
        }
        render(event.getInventory(), holder, player);
    }

    private void enable(Player player, String nick) {
        manager.enable(player, nick);
        player.sendMessage(plugin.msg("enabled", nick));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
