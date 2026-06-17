package logisticspipes.network.packets.crafting;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.pattern.ItemPattern;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.io.IOException;

@Setter
@Getter
@Accessors(chain = true)
public class PatternPipeSatelliteAssignmentPacket extends CoordinatesPacket {

    private int patternSlot;
    private int inputSlot;
    private int satelliteId;
    private String satelliteUuid = "";

    public PatternPipeSatelliteAssignmentPacket(int id) {
        super(id);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        patternSlot = data.readInt();
        inputSlot = data.readInt();
        satelliteId = data.readInt();
        satelliteUuid = data.readUTF();
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof PipeItemsPatternCraftingLogistics pipe)) {
            return;
        }
        ItemStack pattern = pipe.getPatternModule().getPatternItemStack(patternSlot);
        if (pattern == null || pattern.getItem() != LogisticsPipes.LogisticsPattern) {
            return;
        }
        ItemPattern.fromStack(pattern).setSatelliteTargetForInputSlot(inputSlot, satelliteId, satelliteUuid);
        pipe.getPatternModule().markPatternInventoryDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(patternSlot);
        data.writeInt(inputSlot);
        data.writeInt(satelliteId);
        data.writeUTF(satelliteUuid == null ? "" : satelliteUuid);
    }

    @Override
    public ModernPacket template() {
        return new PatternPipeSatelliteAssignmentPacket(getId());
    }
}
