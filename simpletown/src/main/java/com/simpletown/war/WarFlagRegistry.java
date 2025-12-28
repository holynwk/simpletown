
package com.simpletown.war;

import com.simpletown.SimpleTownPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Registers craft recipes for the capture flags used during wars.
 */
public class WarFlagRegistry {

    private final SimpleTownPlugin plugin;

    public WarFlagRegistry(SimpleTownPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerRecipes() {
        registerCaptureFlag();
        registerCenterCaptureFlag();
    }

    private void registerCaptureFlag() {
        ItemStack result = new ItemStack(Material.RED_BANNER);
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Флаг Захвата");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Здоровье: 60/60",
                    ChatColor.GRAY + "Таймер: 120 сек."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            result.setItemMeta(meta);
        }

        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "capture_flag"), result);
        recipe.shape(
                "WWW",
                "WWW",
                " O "
        );
        recipe.setIngredient('W', Material.RED_WOOL);
        recipe.setIngredient('O', Material.OAK_LOG);
        plugin.getServer().addRecipe(recipe);
    }

    private void registerCenterCaptureFlag() {
        ItemStack result = new ItemStack(Material.GREEN_BANNER);
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Флаг Захвата Центра");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Здоровье: 150/150",
                    ChatColor.GRAY + "Таймер: 300 сек."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            result.setItemMeta(meta);
        }

        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "capture_flag_center"), result);
        recipe.shape(
                "WFW",
                "WWW",
                " O "
        );
        recipe.setIngredient('W', Material.GREEN_WOOL);
        recipe.setIngredient('F', Material.IRON_INGOT);
        recipe.setIngredient('O', Material.OAK_LOG);
        plugin.getServer().addRecipe(recipe);
    }
}