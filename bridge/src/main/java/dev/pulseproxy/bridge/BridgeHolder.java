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
