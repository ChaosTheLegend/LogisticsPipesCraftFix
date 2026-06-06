package logisticspipes.gui.MUI;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryType;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class LogisticsModuleData extends PlayerInventoryGuiData {

    public final LogisticsModule module;

    public LogisticsModuleData(@NotNull EntityPlayer player, @NotNull InventoryType inventoryType, int slotIndex, @NotNull LogisticsModule module) {
        super(player, inventoryType, slotIndex);
        this.module = module;
    }
}
