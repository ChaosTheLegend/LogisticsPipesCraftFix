package logisticspipes.network.packets.gui;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.AbstractPattern;
import logisticspipes.crafting.Pattern;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class PatternPipeSlotActionPacket extends CoordinatesPacket {

    @Getter
    @Setter
    private int patternSlot;
    @Getter
    @Setter
    private int action;

    public PatternPipeSlotActionPacket(int id) {
        super(id);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        patternSlot = data.readInt();
        action = data.readInt();
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof PipeItemsPatternCraftingLogistics)) {
            return;
        }
        PipeItemsPatternCraftingLogistics pipe = (PipeItemsPatternCraftingLogistics) tile.pipe;
        ItemStack pattern = pipe.getPatternModule().getPatternItemStack(patternSlot);
        if (pattern == null || pattern.getItem() != LogisticsPipes.LogisticsPattern) {
            return;
        }
        AbstractPattern configuredPattern = Pattern.fromStack(pattern);
        if (action == PatternSlotActionPacket.Action.CLEAR.ordinal()) {
            configuredPattern.clear();
        } else if (action == PatternSlotActionPacket.Action.MULTIPLY_TWO.ordinal()) {
            configuredPattern.multiply(2);
        }
        pipe.getPatternModule().markPatternInventoryDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(patternSlot);
        data.writeInt(action);
    }

    @Override
    public ModernPacket template() {
        return new PatternPipeSlotActionPacket(getId());
    }
}
