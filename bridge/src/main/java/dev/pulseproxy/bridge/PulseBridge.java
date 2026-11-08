/*
 * Copyright (c) 2026 Hassxn. All rights reserved / Todos los derechos reservados.
 * SPDX-License-Identifier: LicenseRef-PulseBridge-Use-Only
 *
 * English: Use only. Modification and distribution are prohibited without
 * prior written permission from Hassxn. See LICENSE for the full terms.
 *
 * Español: Solo uso. Se prohíbe modificar y distribuir sin autorización
 * previa y por escrito de Hassxn. Consulte LICENSE y LICENSE.es.md.
 */

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
    private String channel=CHANNEL;
    private final NetworkEventCache networkEvents=new NetworkEventCache();
    private final Map<String,Long> publishedTimers=new java.util.HashMap<String,Long>();
    private String lastTimer;
    private boolean timerApiWarned;
    private static final String COPYRIGHT = "PulseProxy Bridge (c) 2026 Hassxn - Solo uso / Use only. Prohibido modificar o distribuir / Modification or distribution prohibited.";

    private final Map<UUID, String> openSessions = new ConcurrentHashMap<UUID, String>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try { getServer().getMessenger().registerOutgoingPluginChannel(this, channel); }
        catch (IllegalArgumentException legacy) { channel="pulse:bridge"; getServer().getMessenger().registerOutgoingPluginChannel(this,channel); }
        getServer().getMessenger().registerIncomingPluginChannel(this, channel, this);
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) helloLater(p);
        getServer().getScheduler().runTaskTimer(this,new Runnable(){public void run(){publishHcfTimers();}},20L,Math.max(20,getConfig().getLong("hcf-timers.interval-ticks",100)));
        getLogger().info("PulseProxy Bridge activo. Los menús de PulseProxy se abrirán como inventario.");
        getLogger().info(COPYRIGHT);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (openSessions.containsKey(p.getUniqueId())) p.closeInventory();
        }
        openSessions.clear();
        networkEvents.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject bye = new JsonObject();
            bye.addProperty("type", "BYE");
            send(p, bye);
            break;
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this, channel);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, channel);
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
            p.sendPluginMessage(this, channel, bytes.toByteArray());
        } catch (Exception e) {
            getLogger().warning("No se pudo enviar al proxy: " + e.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] data) {
        if (!this.channel.equals(channel) || data.length > 32766) return;
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
        if ("NETWORK_EVENTS".equals(type)) {
            receiveEvents(o);
        } else if ("OPEN".equals(type)) {
            open(target, o);
        } else if ("CLOSE".equals(type)) {
            String session = o.has("session") ? o.get("session").getAsString() : "";
            if (session.equals(openSessions.get(target.getUniqueId()))) target.closeInventory();
        }
    }

    private void receiveEvents(JsonObject o) {
        if(!getConfig().getBoolean("network-events.enabled",true)){networkEvents.clear();return;}
        try {
            if(o.get("version").getAsInt()!=1 || o.getAsJsonArray("events").size()>64)return;
            List<NetworkEventCache.Event> incoming=new ArrayList<NetworkEventCache.Event>();
            long now=System.currentTimeMillis();
            for(JsonElement value:o.getAsJsonArray("events")){
                JsonObject e=value.getAsJsonObject();
                long deadline=e.get("deadline").getAsLong();
                if(deadline>now+366L*86400000L)return;
                incoming.add(new NetworkEventCache.Event(e.get("server").getAsString(),e.get("event").getAsString(),e.get("stage").getAsString(),deadline));
            }
            networkEvents.accept(o.get("origin").getAsString(),o.get("server").getAsString(),o.get("sentAt").getAsLong(),incoming,now);
        }catch(RuntimeException ignored){getLogger().fine("Ignored invalid event snapshot");}
    }

    /** Stable reflection API used by all Pulse scoreboards. Text is legacy colored. */
    public List<String> getNetworkEventLines(String format) {
        List<String> lines=new ArrayList<String>();
        if(!isEnabled() || !getConfig().getBoolean("network-events.enabled",true))return lines;
        long now=System.currentTimeMillis();
        String pattern=format==null||format.isEmpty()?getConfig().getString("network-events.format","&6{event} &7({server}) &f{time}"):format;
        boolean localOnly=getConfig().getString("network-events.scope","ALL").equalsIgnoreCase("LOCAL");
        boolean hcf=getServer().getPluginManager().isPluginEnabled("PulseHCF");
        int limit=Math.max(0,Math.min(15,getConfig().getInt("network-events.max-lines",3)));
        long ttl=Math.max(5,Math.min(60,getConfig().getLong("network-events.ttl-seconds",15)))*1000L;
        for(NetworkEventCache.Event e:networkEvents.active(now,ttl,getConfig().getBoolean("network-events.show-waiting",true),localOnly)){
            if(lines.size()>=limit)break;
            if(hcf&&getConfig().getBoolean("network-events.hide-local-hcf-timers",true)&&e.server.equalsIgnoreCase(networkEvents.localServer())&&!e.type.equals("RESTART"))continue;
            String label=getConfig().getString("network-events.labels."+e.type,e.type);
            String time=e.stage.equals("WAITING")?getConfig().getString("network-events.waiting-text","Restarting"):e.remaining(now);
            lines.add(org.bukkit.ChatColor.translateAlternateColorCodes('&',pattern.replace("{event}",label).replace("{server}",e.server).replace("{time}",time).replace("{state}",e.stage)));
        }
        return lines;
    }

    private void publishHcfTimers(){
        if(timerApiWarned||!getConfig().getBoolean("hcf-timers.enabled",true)||Bukkit.getOnlinePlayers().isEmpty())return;
        if(Bukkit.getPluginManager().isPluginEnabled("PulseHCFEvents"))return;
        org.bukkit.plugin.Plugin hcf=Bukkit.getPluginManager().getPlugin("PulseHCF");
        if(hcf==null||!hcf.isEnabled())return;
        try {
            Object engine=hcf.getClass().getMethod("getTimerEngine").invoke(hcf);
            if(engine==null)return;
            for(String id:new String[]{"eotw","sotw"}){
                Object timer=engine.getClass().getMethod("getGlobal",String.class).invoke(engine,id);
                if(timer!=null){long deadline=((Number)timer.getClass().getMethod("getExpiry").invoke(timer)).longValue();
                    if(deadline>System.currentTimeMillis()){
                        publishedTimers.put(id,deadline);lastTimer=id;
                        publishTimer(id,deadline,id.equals("sotw")?"PROTECTION":"COUNTDOWN");return;
                    }
                }
            }
            if(lastTimer!=null){Long deadline=publishedTimers.remove(lastTimer);if(deadline!=null)publishTimer(lastTimer,deadline,System.currentTimeMillis()>=deadline?"LIVE":"CANCELLED");lastTimer=null;}
        }catch(ReflectiveOperationException e){if(!timerApiWarned){getLogger().warning("PulseHCF timer API unavailable; native timer publication disabled until reload.");timerApiWarned=true;}}
    }
    private void publishTimer(String type,long deadline,String stage){
        Player carrier=Bukkit.getOnlinePlayers().iterator().next();JsonObject packet=new JsonObject();packet.addProperty("type","NETWORK_EVENT");packet.addProperty("event",type.toUpperCase(java.util.Locale.ROOT));packet.addProperty("deadline",deadline);packet.addProperty("stage",stage);send(carrier,packet);
    }
    @Override public boolean onCommand(org.bukkit.command.CommandSender sender,org.bukkit.command.Command command,String label,String[] args){
        if(!sender.hasPermission("pulsebridge.admin")){sender.sendMessage("§cNo permission.");return true;}
        if(args.length==1&&args[0].equalsIgnoreCase("reload")){reloadConfig();networkEvents.clear();timerApiWarned=false;sender.sendMessage("§aPulseBridge reloaded.");return true;}
        sender.sendMessage("§e/pulsebridge reload");return true;
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
