package logisticspipes.pipes.upgrades;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.LogisticsPipes;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.pipes.PipeCraftingChassi;

public class CraftingChassiUpgradeManager implements ISlotUpgradeManager {

    private final IPipeUpgrade[] upgrades = new IPipeUpgrade[4];
    private final PipeCraftingChassi pipe;

    private ForgeDirection sneakyOrientation = ForgeDirection.UNKNOWN;
    private boolean isAdvancedCrafter = false;
    private boolean isFuzzyUpgrade = false;
    private int liquidCrafter = 0;
    private boolean hasByproductExtractor = false;
    private boolean hasPatternUpgrade = false;
    private int craftingCleanup = 0;

    public CraftingChassiUpgradeManager(PipeCraftingChassi pipe) {
        this.pipe = pipe;
    }

    @Override
    public boolean hasPatternUpgrade() {
        return hasPatternUpgrade || pipe.getOriginalUpgradeManager().hasPatternUpgrade();
    }

    @Override
    public boolean isAdvancedSatelliteCrafter() {
        return isAdvancedCrafter || pipe.getOriginalUpgradeManager().isAdvancedSatelliteCrafter();
    }

    @Override
    public boolean hasByproductExtractor() {
        return hasByproductExtractor || pipe.getOriginalUpgradeManager().hasByproductExtractor();
    }

    @Override
    public int getFluidCrafter() {
        return Math.min(liquidCrafter + pipe.getOriginalUpgradeManager().getFluidCrafter(), ItemUpgrade.MAX_LIQUID_CRAFTER);
    }

    @Override
    public boolean isFuzzyUpgrade() {
        return isFuzzyUpgrade || pipe.getOriginalUpgradeManager().isFuzzyUpgrade();
    }

    @Override
    public int getCrafterCleanup() {
        return Math.min(craftingCleanup + pipe.getOriginalUpgradeManager().getCrafterCleanup(), ItemUpgrade.MAX_CRAFTING_CLEANUP);
    }

    @Override
    public boolean hasSneakyUpgrade() {
        if (sneakyOrientation != ForgeDirection.UNKNOWN) {
            return true;
        }
        return pipe.getOriginalUpgradeManager().hasSneakyUpgrade();
    }

    @Override
    public ForgeDirection getSneakyOrientation() {
        if (sneakyOrientation != ForgeDirection.UNKNOWN) {
            return sneakyOrientation;
        }
        return pipe.getOriginalUpgradeManager().getSneakyOrientation();
    }

    @Override
    public boolean hasOwnSneakyUpgrade() {
        return sneakyOrientation != ForgeDirection.UNKNOWN;
    }

    public void updateUpgrades() {
        boolean needUpdate = false;
        var inv = pipe.getUpgradeInventory();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack item = inv.getStackInSlot(i);
            if (item != null) {
                needUpdate |= updateModule(i, upgrades, inv);
            } else if (upgrades[i] != null) {
                needUpdate |= removeUpgrade(i, upgrades);
            }
        }
        // update sneaky direction, speed upgrade count and disconnection
        sneakyOrientation = ForgeDirection.UNKNOWN;
        isAdvancedCrafter = false;
        isFuzzyUpgrade = false;
        liquidCrafter = 0;
        hasByproductExtractor = false;
        hasPatternUpgrade = false;
        craftingCleanup = 0;
        for (int i = 0; i < upgrades.length; i++) {
            IPipeUpgrade upgrade = upgrades[i];
            if (upgrade instanceof SneakyUpgrade && sneakyOrientation == ForgeDirection.UNKNOWN) {
                sneakyOrientation = ((SneakyUpgrade) upgrade).getSneakyOrientation();
            } else if (upgrade instanceof AdvancedSatelliteUpgrade) {
                isAdvancedCrafter = true;
            } else if (upgrade instanceof FuzzyUpgrade) {
                isFuzzyUpgrade = true;
            } else if (upgrade instanceof FluidCraftingUpgrade) {
                liquidCrafter += inv.getStackInSlot(i).stackSize;
            } else if (upgrade instanceof CraftingByproductUpgrade) {
                hasByproductExtractor = true;
            } else if (upgrade instanceof PatternUpgrade) {
                hasPatternUpgrade = true;
            } else if (upgrade instanceof CraftingCleanupUpgrade) {
                craftingCleanup += inv.getStackInSlot(i).stackSize;
            }
        }
        liquidCrafter = Math.min(liquidCrafter, ItemUpgrade.MAX_LIQUID_CRAFTER);
        craftingCleanup = Math.min(craftingCleanup, ItemUpgrade.MAX_CRAFTING_CLEANUP);
        if (needUpdate) {
            pipe.connectionUpdate();
            if (pipe.container != null) {
                pipe.container.sendUpdateToClient();
            }
        }
    }

    private boolean updateModule(int slot, IPipeUpgrade[] upgrades, com.cleanroommc.modularui.utils.item.IItemHandlerModifiable inv) {
        upgrades[slot] = LogisticsPipes.UpgradeItem.getUpgradeForItem(inv.getStackInSlot(slot), upgrades[slot]);
        if (upgrades[slot] == null) {
            inv.setStackInSlot(slot, null);
            return false;
        } else {
            return upgrades[slot].needsUpdate();
        }
    }

    private boolean removeUpgrade(int slot, IPipeUpgrade[] upgrades) {
        boolean needUpdate = upgrades[slot].needsUpdate();
        upgrades[slot] = null;
        return needUpdate;
    }
}
