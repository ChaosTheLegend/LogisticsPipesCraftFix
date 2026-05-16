package logisticspipes.network.packets.crafting;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.Pattern;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.ModernPacket;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class PatternSatelliteAssignmentPacket extends ModernPacket {

    @Getter
    @Setter
    private int inventorySlot;
    @Getter
    @Setter
    private int inputSlot;
    @Getter
    @Setter
    private int satelliteId;

    public PatternSatelliteAssignmentPacket(int id) {
        super(id);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        inventorySlot = data.readInt();
        inputSlot = data.readInt();
        satelliteId = data.readInt();
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (inventorySlot < 0 || inventorySlot >= player.inventory.mainInventory.length) {
            return;
        }
        ItemStack pattern = player.inventory.mainInventory[inventorySlot];
        if (pattern == null || pattern.getItem() != LogisticsPipes.LogisticsPattern) {
            return;
        }
        Pattern.fromStack(pattern).setSatelliteIdForInputSlot(inputSlot, satelliteId);
        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeInt(inventorySlot);
        data.writeInt(inputSlot);
        data.writeInt(satelliteId);
    }

    @Override
    public ModernPacket template() {
        return new PatternSatelliteAssignmentPacket(getId());
    }
}
