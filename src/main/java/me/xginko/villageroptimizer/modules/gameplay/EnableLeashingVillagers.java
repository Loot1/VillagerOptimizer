package me.xginko.villageroptimizer.modules.gameplay;

import com.cryptomorin.xseries.XEntityType;
import me.xginko.villageroptimizer.modules.VillagerOptimizerModule;
import me.xginko.villageroptimizer.utils.LocationUtil;
import me.xginko.villageroptimizer.wrapper.WrappedVillager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class EnableLeashingVillagers extends VillagerOptimizerModule implements Listener {

    private final boolean only_optimized, log_enabled;

    public EnableLeashingVillagers() {
        super("gameplay.villagers-can-be-leashed");
        config.master().addComment(configPath + ".enable",
                "Enable leashing of villagers, enabling players to easily move villagers to where they want them to be.");
        this.only_optimized = config.getBoolean(configPath + ".only-optimized", false,
                "If set to true, only optimized villagers can be leashed.");
        this.log_enabled = config.getBoolean(configPath + ".log", false);
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public boolean shouldEnable() {
        return config.getBoolean(configPath + ".enable", false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onLeash(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() != XEntityType.VILLAGER.get()) return;
        final Player player = event.getPlayer();
        final ItemStack handItem = player.getInventory().getItem(event.getHand());
        if (handItem.getType() != Material.LEAD) return;

        final Villager villager = (Villager) event.getRightClicked();
        if (villager.isLeashed()) return;

        if (only_optimized) {
            WrappedVillager wrapped = wrapperCache.get(villager, WrappedVillager::new);
            if (wrapped == null || !wrapped.isOptimized()) return;
        }

        event.setCancelled(true); // Cancel the event, so we don't interact with the villager

        // Use the 4-arg constructor (available since 1.19.2, the 3-arg is deprecated for removal)
        final EquipmentSlot hand = event.getHand();
        PlayerLeashEntityEvent leashEvent = new PlayerLeashEntityEvent(villager, player, player, hand);

        // If canceled by any plugin, do nothing
        if (!leashEvent.callEvent()) return;

        scheduling.entitySpecificScheduler(villager).run(leash -> {
            if (!villager.setLeashHolder(player)) return;
            if (player.getGameMode().equals(GameMode.SURVIVAL))
                handItem.subtract(1); // Manually consume for survival players

            if (log_enabled) {
                info(player.getName() + " leashed a villager at " + LocationUtil.toString(villager.getLocation()));
            }
        }, null);
    }
}
