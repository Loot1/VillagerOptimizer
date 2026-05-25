package me.xginko.villageroptimizer.modules.gameplay;

import com.cryptomorin.xseries.XEntityType;
import me.xginko.villageroptimizer.modules.VillagerOptimizerModule;
import me.xginko.villageroptimizer.struct.enums.OptimizationType;
import me.xginko.villageroptimizer.wrapper.WrappedVillager;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;

public class FixOptimisationAfterCure extends VillagerOptimizerModule implements Listener {

    public FixOptimisationAfterCure() {
        super("gameplay.fix-optimisation-after-cure");
        config.master().addComment(configPath + ".enable",
                "Re-applies the optimization state of a villager after it has been cured from a zombie villager,\n" +
                "since the curing process resets entity AI flags.");
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
        return config.getBoolean(configPath + ".enable", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onTransform(EntityTransformEvent event) {
        if (
                event.getTransformReason() == EntityTransformEvent.TransformReason.CURED
                && event.getTransformedEntity().getType() == XEntityType.VILLAGER.get()
        ) {
            Villager villager = (Villager) event.getTransformedEntity();
            scheduling.entitySpecificScheduler(villager).runDelayed(() -> {
                WrappedVillager wVillager = wrapperCache.get(villager, WrappedVillager::new);
                if (wVillager == null) return;
                OptimizationType currentType = wVillager.getOptimizationType();
                if (currentType != OptimizationType.NONE) {
                    wVillager.setOptimizationType(currentType);
                }
            }, null, 40L);
        }
    }
}