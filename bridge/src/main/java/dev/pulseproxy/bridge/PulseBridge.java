package dev.pulseproxy.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PulseBridge extends JavaPlugin implements PluginMessageListener, Listener {

    static final String CHANNEL = "pulseproxy:bridge";
    private static final String COPYRIGHT = "PulseProxy Bridge (c) 2026 Hassxn - Todos los derechos reservados / All rights reserved. "
            + "Prohibida su redistribucion. Licencia: %%__USER__%% / %%__NONCE__%%";

    private final Map<UUID, String> openSessions = new ConcurrentHashMap<UUID, String>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) helloLater(p);
        getLogger().info("PulseProxy Bridge activo. Los menús de PulseProxy se abrirán como inventario.");
        getLogger().info("(c) 2026 Hassxn. Todos los derechos reservados.");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (openSessions.containsKey(p.getUniqueId())) p.closeInventory();
        }
        openSessions.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject bye = new JsonObject();
            bye.addProperty("type", "BYE");
            send(p, bye);
            break;
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
    }

    private void helloLater(final Player p) {
        getServer().getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                JsonObject hello = new JsonObject();
                hello.addProperty("type", "HELLO");
                hello.addProperty("version", getDescription().getVersion());
                send(p, hello);
            }
        }, 30L);
    }

    void send(Player p, JsonObject payload) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new DataOutputStream(bytes).writeUTF(payload.toString());
            p.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
        } catch (Exception e) {
            getLogger().warning("No se pudo enviar al proxy: " + e.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] data) {
        if (!CHANNEL.equals(channel)) return;
        JsonObject o;
        try {
            String json = new DataInputStream(new ByteArrayInputStream(data)).readUTF();
            o = new JsonParser().parse(json).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        Player target = carrier;
        if (o.has("player")) {
            try {
                Player p = Bukkit.getPlayer(UUID.fromString(o.get("player").getAsString()));
                if (p != null) target = p;
            } catch (IllegalArgumentException ignored) {
            }
        }
        String type = o.has("type") ? o.get("type").getAsString() : "";
        if ("OPEN".equals(type)) {
            open(target, o);
        } else if ("CLOSE".equals(type)) {
            String session = o.has("session") ? o.get("session").getAsString() : "";
            if (session.equals(openSessions.get(target.getUniqueId()))) target.closeInventory();
        }
    }

    private void open(Player player, JsonObject o) {
        int rows = Math.max(1, Math.min(6, o.has("rows") ? o.get("rows").getAsInt() : 3));
        String title = o.has("title") ? o.get("title").getAsString() : "";
        if (title.length() > 32) title = title.substring(0, 32);
        String session = o.get("session").getAsString();
        Inventory inv = Bukkit.createInventory(new BridgeHolder(session), rows * 9, title);

        if (o.has("filler")) {
            ItemStack filler = Materials.item(o.get("filler").getAsString(), 1);
            if (filler != null) {
                ItemMeta meta = filler.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(" ");
                    filler.setItemMeta(meta);
                }
                for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
            }
        }

        if (o.has("items")) {
            JsonArray items = o.getAsJsonArray("items");
            for (JsonElement el : items) {
                JsonObject it = el.getAsJsonObject();
                int slot = it.get("slot").getAsInt();
                if (slot < 0 || slot >= inv.getSize()) continue;
                int amount = it.has("amount") ? it.get("amount").getAsInt() : 1;
                ItemStack stack = Materials.item(it.get("mat").getAsString(), amount);
                if (stack == null) stack = Materials.item("PAPER", amount);
                if (stack == null) continue;
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    if (it.has("name")) meta.setDisplayName(it.get("name").getAsString());
                    if (it.has("lore")) {
                        List<String> lore = new ArrayList<String>();
                        for (JsonElement l : it.getAsJsonArray("lore")) lore.add(l.getAsString());
                        meta.setLore(lore);
                    }
                    if (it.has("glow") && it.get("glow").getAsBoolean()) {
                        Enchantment[] all = Enchantment.values();
                        if (all.length > 0 && all[0] != null) meta.addEnchant(all[0], 1, true);
                    }
                    try {
                        meta.addItemFlags(ItemFlag.values());
                    } catch (Throwable ignored) {
                    }
                    if (it.has("head") && meta instanceof SkullMeta) {
                        try {
                            ((SkullMeta) meta).setOwner(it.get("head").getAsString());
                        } catch (Throwable ignored) {
                        }
                    }
                    stack.setItemMeta(meta);
                }
                inv.setItem(slot, stack);
            }
        }
        player.openInventory(inv);
        openSessions.put(player.getUniqueId(), session);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (top == null || !(top.getHolder() instanceof BridgeHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;
        JsonObject click = new JsonObject();
        click.addProperty("type", "CLICK");
        click.addProperty("session", ((BridgeHolder) top.getHolder()).session());
        click.addProperty("slot", slot);
        click.addProperty("click", event.getClick().name());
        send((Player) event.getWhoClicked(), click);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (top != null && top.getHolder() instanceof BridgeHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (top == null || !(top.getHolder() instanceof BridgeHolder)) return;
        if (!(event.getPlayer() instanceof Player)) return;
        String session = ((BridgeHolder) top.getHolder()).session();
        UUID id = event.getPlayer().getUniqueId();
        if (session.equals(openSessions.get(id))) openSessions.remove(id);
        JsonObject close = new JsonObject();
        close.addProperty("type", "CLOSE");
        close.addProperty("session", session);
        send((Player) event.getPlayer(), close);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        helloLater(event.getPlayer());
    }
}
