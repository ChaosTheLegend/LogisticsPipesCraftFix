package logisticspipes.network.packets.gui;

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
public class PatternSlotActionPacket extends ModernPacket {

    public enum Action {
        CLEAR,
        MULTIPLY_TWO
    }

    @Getter
    @Setter
    private int inventorySlot;

    @Getter
    @Setter
    private int action;

    public PatternSlotActionPacket(int id) {
        super(id);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        inventorySlot = data.readInt();
        action = data.readInt();
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
        if (action == Action.CLEAR.ordinal()) {
            Pattern.clear(pattern);
        } else if (action == Action.MULTIPLY_TWO.ordinal()) {
            Pattern.multiply(pattern, 2);
        }
        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeInt(inventorySlot);
        data.writeInt(action);
    }

    @Override
    public ModernPacket template() {
        return new PatternSlotActionPacket(getId());
    }
}
