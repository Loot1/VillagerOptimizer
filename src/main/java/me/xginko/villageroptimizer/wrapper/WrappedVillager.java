package me.xginko.villageroptimizer.wrapper;

import me.xginko.villageroptimizer.VillagerOptimizer;
import me.xginko.villageroptimizer.struct.enums.Keyring;
import me.xginko.villageroptimizer.struct.enums.OptimizationType;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WrappedVillager extends PDCWrapper {

    private final @NotNull PDCWrapper[] pdcWrappers;

    private final @NotNull PDCWrapper primaryWrapper;

    public WrappedVillager(@NotNull Villager villager) {
        super(villager);
        this.pdcWrappers = PDCWrapper.forVillager(villager);
        this.primaryWrapper = pdcWrappers[0];
    }

    /**
     * Returns a number between 0 and 24000
     * is affected by /time set
     */
    public long currentDayTimeTicks() {
        return villager.getWorld().getTime();
    }

    /**
     * Returns the tick time of the world
     * is affected by /time set
     */
    public long currentFullTimeTicks() {
        return villager.getWorld().getFullTime();
    }

    /**
     * Restock all trading recipes.
     */
    public void restock() {
        VillagerOptimizer.scheduling().entitySpecificScheduler(villager).run(() -> {
            for (MerchantRecipe merchantRecipe : villager.getRecipes()) {
                VillagerReplenishTradeEvent restockRecipeEvent = new VillagerReplenishTradeEvent(villager, merchantRecipe);
                if (restockRecipeEvent.callEvent()) {
                    restockRecipeEvent.getRecipe().setUses(0);
                }
            }
        }, null);
    }

    /**
     * @return The level between 1-5 calculated from the villagers experience.
     */
    public int calculateLevel() {
        // https://minecraft.fandom.com/wiki/Trading#Mechanics
        int vilEXP = villager.getVillagerExperience();
        if (vilEXP >= 250) return 5;
        if (vilEXP >= 150) return 4;
        if (vilEXP >= 70) return 3;
        if (vilEXP >= 10) return 2;
        return 1;
    }

    /**
     * @return true if the villager can lose its acquired profession by having its workstation destroyed.
     */
    public boolean canLooseProfession() {
        // A villager with a level of 1 and no trading experience is liable to lose its profession.
        return villager.getVillagerLevel() <= 1 && villager.getVillagerExperience() <= 0;
    }

    public void sayNo() {
        try {
            villager.shakeHead();
        } catch (NoSuchMethodError e) {
            villager.getWorld().playSound(villager.getEyeLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
        }
    }

    public @Nullable Location getJobSite() {
        return villager.getMemory(MemoryKey.JOB_SITE);
    }

    @Override
    public Keyring.Space getSpace() {
        return Keyring.Space.VillagerOptimizer;
    }

    @Override
    public boolean isOptimized() {
        if (pdcWrappers.length == 1) return primaryWrapper.isOptimized();
        for (PDCWrapper pdcWrapper : pdcWrappers) {
            if (pdcWrapper.isOptimized()) return true;
        }
        return false;
    }

    @Override
    public boolean canOptimize(long cooldown_millis) {
        if (pdcWrappers.length == 1) return primaryWrapper.canOptimize(cooldown_millis);
        for (PDCWrapper pdcWrapper : pdcWrappers) {
            if (!pdcWrapper.canOptimize(cooldown_millis)) return false;
        }
        return true;
    }

    @Override
    public void setOptimizationType(OptimizationType type) {
        if (pdcWrappers.length == 1) { primaryWrapper.setOptimizationType(type); return; }
        for (PDCWrapper pdcWrapper : pdcWrappers) {
            pdcWrapper.setOptimizationType(type);
        }
    }

    @Override
    public @NotNull OptimizationType getOptimizationType() {
        if (pdcWrappers.length == 1) return primaryWrapper.getOptimizationType();
        OptimizationType result = OptimizationType.NONE;
        for (PDCWrapper pdcWrapper : pdcWrappers) {
            OptimizationType type = pdcWrapper.getOptimizationType();
            if (type != OptimizationType.NONE) {
                if (pdcWrapper.getSpace() == Keyring.Space.VillagerOptimizer) {
                    return type;
                } else {
                    result = type;
                }
            }
        }
        return result;
    }

    @Override
    public void saveOptimizeTime() {
        if (pdcWrappers.length == 1) { primaryWrapper.saveOptimizeTime(); return; }
        for (PDCWrapper pdcWrapper : pdcWrappers) {
            pdcWrapper.saveOptimizeTime();
        }
    }

    @Override
    public long getOptimizeCooldownMillis(long cooldown_millis) {
        if (pdcWrappers.length == 1) return primaryWrapper.getOptimizeCooldownMillis(cooldown_millis);
        long cooldown = 0L;
        for (PDCWrapper pdcWrapper : pdcWrappers) {
            cooldown = Math.max(cooldown, pdcWrapper.getOptimizeCooldownMillis(cooldown_millis));
        }
        return cooldown;
    }

    @Override
    public long getLastRestockFullTime() {
        if (pdcWrappers.length == 1) return primaryWrapper.getLastRestockFullTime();
        long cooldown = 0L;
        for (PDCWrapper pdcWrapper : pdcWrappers) {
            cooldown = Math.max(cooldown, pdcWrapper.getLastRestockFullTime());
        }
        return cooldown;
    }

    @Override
    public void saveRestockTime() {
        if (pdcWrappers.length == 1) { primaryWrapper.saveRestockTime(); return; }
        for (PDCWrapper pdcWrapper : pdcWrappers) {
            pdcWrapper.saveRestockTime();
        }
    }

    @Override
    public boolean canLevelUp(long cooldown_millis) {
        if (pdcWrappers.length == 1) return primaryWrapper.canLevelUp(cooldown_millis);
        for (PDCWrapper pdcWrapper : pdcWrappers) {
            if (!pdcWrapper.canLevelUp(cooldown_millis)) return false;
        }
        return true;
    }

    @Override
    public void saveLastLevelUp() {
        if (pdcWrappers.length == 1) { primaryWrapper.saveLastLevelUp(); return; }
        for (PDCWrapper pdcWrapper : pdcWrappers) {
            pdcWrapper.saveLastLevelUp();
        }
    }

    @Override
    public long getLevelCooldownMillis(long cooldown_millis) {
        if (pdcWrappers.length == 1) return primaryWrapper.getLevelCooldownMillis(cooldown_millis);
        long cooldown = cooldown_millis;
        for (PDCWrapper pdcWrapper : pdcWrappers) {
            cooldown = Math.max(cooldown, pdcWrapper.getLevelCooldownMillis(cooldown_millis));
        }
        return cooldown;
    }
}