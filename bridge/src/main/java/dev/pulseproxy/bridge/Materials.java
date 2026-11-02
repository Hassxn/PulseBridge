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

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class Materials {

    private Materials() {
    }

    /**
     * Resuelve "NOMBRE_MODERNO|NOMBRE_1_8:dato" probando cada opción hasta encontrar una que exista
     * en la versión del servidor.
     */
    @SuppressWarnings("deprecation")
    static ItemStack item(String spec, int amount) {
        if (spec == null || spec.trim().isEmpty()) return null;
        int count = Math.max(1, Math.min(64, amount));
        for (String option : spec.split("\\|")) {
            String name = option.trim();
            short data = 0;
            int colon = name.indexOf(':');
            if (colon > 0) {
                try {
                    data = Short.parseShort(name.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                }
                name = name.substring(0, colon);
            }
            Material material = Material.matchMaterial(name);
            if (material == null || material == Material.AIR) continue;
            return data > 0 ? new ItemStack(material, count, data) : new ItemStack(material, count);
        }
        return null;
    }
}
