package me.xginko.villageroptimizer.modules.optimization;

import com.cryptomorin.xseries.XEntityType;
import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import me.xginko.villageroptimizer.VillagerOptimizer;
import me.xginko.villageroptimizer.modules.VillagerOptimizerModule;
import me.xginko.villageroptimizer.struct.enums.OptimizationType;
import me.xginko.villageroptimizer.struct.models.BlockRegion2D;
import me.xginko.villageroptimizer.wrapper.WrappedVillager;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityInteractEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class OptimizeByActivity extends VillagerOptimizerModule implements Listener {

    protected static class RegionData {

        public final BlockRegion2D region;
        public final AtomicInteger pathfindCount, entityInteractCount;
        public final AtomicBoolean regionBusy;

        public RegionData(BlockRegion2D region) {
            this.region = region;
            this.pathfindCount = new AtomicInteger();
            this.entityInteractCount = new AtomicInteger();
            this.regionBusy = new AtomicBoolean(false);
        }
    }

    private final Cache<BlockRegion2D, RegionData> regionDataCache;
    private final double checkRadius;
    private final int pathfindLimit, entityInteractLimit;
    private final boolean notifyPlayers, doLogging;

    public OptimizeByActivity() {
        super("optimization-methods.regional-activity");
        config.master().addComment(configPath + ".enable",
                "When enabled, villagers in a region will be automatically optimized if the number of\n" +
                "pathfind or entity-interact events exceeds the configured limits within the data keep time.");

        this.checkRadius = config.getDouble(configPath + ".check-radius-blocks", 500.0,
                "The radius in blocks in which activity will be grouped together and measured.");
        this.regionDataCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMillis(
                config.getInt(configPath + ".data-keep-time-millis", 10000,
                        """
                                The time in milliseconds before a region and its data will be expired
                                if no activity has been detected.
                                For proper functionality, needs to be at least as long as your pause time."""))).build();

        this.pathfindLimit = config.getInt(configPath + ".limits.pathfind-event", 150);
        this.entityInteractLimit = config.getInt(configPath + ".limits.interact-event", 50);

        this.notifyPlayers = config.getBoolean(configPath + ".notify-players", true,
                "Sends players a message to any player near an auto-optimized villager.");
        this.doLogging = config.getBoolean(configPath + ".log", false);
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

    private @NotNull RegionData getOrCreateRegionData(@NotNull Location location) {
        for (RegionData data : regionDataCache.asMap().values()) {
            if (data.region.contains(location)) {
                return data;
            }
        }
        BlockRegion2D region = BlockRegion2D.of(location.getWorld(), location.getX(), location.getZ(), checkRadius);
        RegionData data = new RegionData(region);
        regionDataCache.put(region, data);
        return data;
    }

    private void triggerRegionOptimization(@NotNull RegionData regionData, @NotNull String logReason,
                                           int activityCount, int limit) {
        regionData.region.getEntities().thenAccept(entities -> {
            // Running on the region scheduler thread (see BlockRegion2D.getEntities())
            int optimizedCount = 0;
            List<Player> playersToNotify = notifyPlayers ? new ArrayList<>() : null;

            for (Entity entity : entities) {
                if (entity.getType() == XEntityType.VILLAGER.get()) {
                    WrappedVillager wrappedVillager = wrapperCache.get((Villager) entity, WrappedVillager::new);
                    if (wrappedVillager != null && !wrappedVillager.isOptimized()) {
                        // setOptimizationType dispatches internally to the entity scheduler
                        wrappedVillager.setOptimizationType(OptimizationType.REGIONAL_ACTIVITY);
                        optimizedCount++;
                    }
                } else if (playersToNotify != null && entity.getType() == XEntityType.PLAYER.get()) {
                    playersToNotify.add((Player) entity);
                }
            }

            if (playersToNotify != null && !playersToNotify.isEmpty()) {
                final TextReplacementConfig amount = TextReplacementConfig.builder()
                        .matchLiteral("%amount%")
                        .replacement(String.valueOf(optimizedCount))
                        .build();
                for (Player player : playersToNotify) {
                    scheduling.entitySpecificScheduler(player).run(() ->
                            VillagerOptimizer.getLang(player.locale()).activity_optimize_success
                                    .forEach(line -> player.sendMessage(line.replaceText(amount))),
                            null);
                }
            }

            if (doLogging) {
                info("Optimized " + optimizedCount + " villagers in a radius of " + checkRadius +
                        " blocks from center at x=" + regionData.region.getCenterX() +
                        ", z=" + regionData.region.getCenterZ() +
                        " because of " + logReason + ": " + activityCount + " (limit: " + limit + ")");
            }

            regionDataCache.invalidate(regionData.region);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onEntityPathfind(EntityPathfindEvent event) {
        if (event.getEntityType() != XEntityType.VILLAGER.get()) return;

        RegionData regionData = getOrCreateRegionData(event.getEntity().getLocation());
        if (regionData.regionBusy.get()) return;

        int count = regionData.pathfindCount.incrementAndGet();
        if (count <= pathfindLimit) return;

        // compareAndSet ensures only one thread triggers the optimization, even under concurrent events
        if (!regionData.regionBusy.compareAndSet(false, true)) return;

        triggerRegionOptimization(regionData, "pathfinding activity", count, pathfindLimit);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onEntityInteract(EntityInteractEvent event) {
        if (event.getEntityType() != XEntityType.VILLAGER.get()) return;

        RegionData regionData = getOrCreateRegionData(event.getEntity().getLocation());
        if (regionData.regionBusy.get()) return;

        int count = regionData.entityInteractCount.incrementAndGet();
        if (count <= entityInteractLimit) return;

        if (!regionData.regionBusy.compareAndSet(false, true)) return;

        triggerRegionOptimization(regionData, "entity-interact events", count, entityInteractLimit);
    }
}