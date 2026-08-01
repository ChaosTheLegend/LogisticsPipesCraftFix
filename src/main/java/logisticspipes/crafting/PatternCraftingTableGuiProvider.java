package logisticspipes.crafting;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.LogisticsPipes;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractguis.CoordinatesGuiProvider;
import logisticspipes.network.abstractguis.GuiProvider;
import logisticspipes.utils.gui.DummyContainer;

public class PatternCraftingTableGuiProvider extends CoordinatesGuiProvider {

    private NBTTagCompound updatePayload;

    public PatternCraftingTableGuiProvider(int id) {
        super(id);
    }

    @Override
    public Object getClientGui(EntityPlayer player) {
        PatternLogisticsCraftingTableTileEntity tile = getTile(
                player.getEntityWorld(),
                PatternLogisticsCraftingTableTileEntity.class);
        if (tile == null) {
            return null;
        }
        if (updatePayload != null) {
            tile.readUpdatePayload(updatePayload);
        }
        PatternCraftingTableGui gui = new PatternCraftingTableGui(player, tile);
        gui.inventorySlots = getContainer(player);
        return gui;
    }

    @Override
    public DummyContainer getContainer(EntityPlayer player) {
        PatternLogisticsCraftingTableTileEntity tile = getTile(
                player.getEntityWorld(),
                PatternLogisticsCraftingTableTileEntity.class);
        if (tile == null) {
            return null;
        }
        DummyContainer dummy = new DummyContainer(player.inventory, tile.getInputInventory());
        addInputSlots(dummy, tile);
        addOutputSlots(dummy, tile);
        addUpgradeSlots(dummy, tile);
        dummy.addNormalSlotsForPlayerInventory(8, 102);
        return dummy;
    }

    static void addInputSlots(DummyContainer dummy, PatternLogisticsCraftingTableTileEntity tile) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                dummy.addRestrictedSlot(
                        y * 3 + x,
                        tile.getInputInventory(),
                        26 + x * 18,
                        18 + y * 18,
                        tile::canPlayerInsertInput);
            }
        }
    }

    static void addOutputSlots(DummyContainer dummy, PatternLogisticsCraftingTableTileEntity tile) {
        for (int i = 0; i < 3; i++) {
            dummy.addRestrictedSlot(i, tile.getOutputInventory(), 116 + i * 18, 36, stack -> false);
        }
    }

    static void addUpgradeSlots(DummyContainer dummy, PatternLogisticsCraftingTableTileEntity tile) {
        for (int i = 0; i < 3; i++) {
            dummy.addRestrictedSlot(
                    i,
                    tile.getUpgradeInventory(),
                    116 + i * 18,
                    69,
                    stack -> stack != null && stack.getItem() == LogisticsPipes.UpgradeItem
                            && PatternLogisticsCraftingTableTileEntity.isSpeedUpgrade(stack));
        }
    }

    public PatternCraftingTableGuiProvider setCraftingTable(PatternLogisticsCraftingTableTileEntity tile) {
        setTilePos(tile);
        updatePayload = new NBTTagCompound();
        tile.writeUpdatePayload(updatePayload);
        return this;
    }

    @Override
    public GuiProvider template() {
        return new PatternCraftingTableGuiProvider(getId());
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeNBTTagCompound(updatePayload);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        updatePayload = data.readNBTTagCompound();
    }
}
