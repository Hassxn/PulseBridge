package dev.pulseproxy.bridge;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class BridgeHolder implements InventoryHolder {

    private final String session;

    BridgeHolder(String session) {
        this.session = session;
    }

    String session() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
