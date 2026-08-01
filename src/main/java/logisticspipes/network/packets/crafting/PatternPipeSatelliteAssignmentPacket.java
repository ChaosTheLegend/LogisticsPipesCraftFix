package logisticspipes.network.packets.crafting;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

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

@Setter
@Getter
@Accessors(chain = true)
public class PatternPipeSatelliteAssignmentPacket extends CoordinatesPacket {

    private int patternSlot;
    private int inputSlot;
    private int satelliteId;
    private String satelliteUuid = "";
    private boolean fluidTarget;

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
        fluidTarget = data.readBoolean();
    }

    /**
     * Stores a satellite assignment made inside the pattern pipe GUI.
     * <p>
     * Besides writing the assignment to the pattern item, this also links the selected satellite to the pipe so staged
     * ingredient requests can resolve the selected pipe as their destination.
     */
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
        if (fluidTarget) {
            ItemPattern.fromStack(pattern).setFluidSatelliteTargetForInputSlot(inputSlot, satelliteId, satelliteUuid);
            pipe.linkPatternFluidSatellite(satelliteId, satelliteUuid);
        } else {
            ItemPattern.fromStack(pattern).setSatelliteTargetForInputSlot(inputSlot, satelliteId, satelliteUuid);
            pipe.linkPatternSatellite(satelliteId, satelliteUuid);
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
        data.writeInt(inputSlot);
        data.writeInt(satelliteId);
        data.writeUTF(satelliteUuid == null ? "" : satelliteUuid);
        data.writeBoolean(fluidTarget);
    }

    @Override
    public ModernPacket template() {
        return new PatternPipeSatelliteAssignmentPacket(getId());
    }
}
