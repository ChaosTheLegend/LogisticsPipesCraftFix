package logisticspipes.network.packets.gui;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.Pattern;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.ModernPacket;

public class PatternTypeMode extends ModernPacket {

    private int inventorySlot;
    private int patternType;

    public PatternTypeMode(int id) {
        super(id);
    }

    public PatternTypeMode setInventorySlot(int inventorySlot) {
        this.inventorySlot = inventorySlot;
        return this;
    }

    public PatternTypeMode setPatternType(int patternType) {
        this.patternType = patternType;
        return this;
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeInt(inventorySlot);
        data.writeInt(patternType);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        inventorySlot = data.readInt();
        patternType = data.readInt();
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (inventorySlot < 0 || inventorySlot >= player.inventory.mainInventory.length) {
            return;
        }
        ItemStack stack = player.inventory.mainInventory[inventorySlot];
        if (stack == null || stack.getItem() != LogisticsPipes.LogisticsPattern) {
            return;
        }
        Pattern.PatternType[] values = Pattern.PatternType.values();
        Pattern.setPatternType(stack, values[Math.max(0, Math.min(values.length - 1, patternType))]);
    }

    @Override
    public ModernPacket template() {
        return new PatternTypeMode(getId());
    }
}
