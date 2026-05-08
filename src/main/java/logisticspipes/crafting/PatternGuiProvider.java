package logisticspipes.crafting;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractguis.GuiProvider;
import logisticspipes.utils.gui.DummyContainer;

public class PatternGuiProvider extends GuiProvider {

    private int inventorySlot;

    public PatternGuiProvider(int id) {
        super(id);
    }

    public PatternGuiProvider setInventorySlot(int inventorySlot) {
        this.inventorySlot = inventorySlot;
        return this;
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeInt(inventorySlot);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        inventorySlot = data.readInt();
    }

    @Override
    public Object getClientGui(EntityPlayer player) {
        return new PatternGui(player, new PatternInventory(player, inventorySlot));
    }

    @Override
    public DummyContainer getContainer(EntityPlayer player) {
        PatternInventory inventory = new PatternInventory(player, inventorySlot);
        if (!inventory.isUseableByPlayer(player)) {
            return null;
        }
        DummyContainer dummy = new DummyContainer(player.inventory, inventory);
        addPatternSlots(dummy);
        dummy.addNormalSlotsForPlayerInventory(8, 86);
        return dummy;
    }

    static void addPatternSlots(DummyContainer dummy) {
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                dummy.addDummySlot(x + y * 3, 26 + x * 18, 17 + y * 18);
            }
        }
        for (int i = 0; i < Pattern.RESULT_SLOTS; i++) {
            dummy.addDummySlot(Pattern.INGREDIENT_SLOTS + i, 116 + i * 18, 35);
        }
    }

    @Override
    public GuiProvider template() {
        return new PatternGuiProvider(getId());
    }
}
