package logisticspipes.crafting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractguis.GuiProvider;
import logisticspipes.utils.gui.DummyContainer;

public class PatternGuiProvider extends GuiProvider {

    private int inventorySlot;
    private List<PatternSatelliteInfo> satellites = new ArrayList<>();

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
        data.writeList(satellites, (stream, satellite) -> satellite.writeData(stream));
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        inventorySlot = data.readInt();
        satellites = data.readList(PatternSatelliteInfo::readData);
    }

    @Override
    public Object getClientGui(EntityPlayer player) {
        return new PatternGui(player, new PatternInventory(player, inventorySlot), satellites);
    }

    @Override
    public DummyContainer getContainer(EntityPlayer player) {
        PatternInventory inventory = new PatternInventory(player, inventorySlot);
        if (!inventory.isUseableByPlayer(player)) {
            satellites = new ArrayList<>();
            return null;
        }
        satellites = PipeItemsPatternSatelliteLogistics.getKnownSatellitesFor(player);
        PatternContainer dummy = new PatternContainer(player.inventory, inventory);
        addPatternSlots(dummy);
        dummy.addNormalSlotsForPlayerInventory(8, 92);
        return dummy;
    }

    static void addPatternSlots(DummyContainer dummy) {
        addPatternSlots(dummy, 26, 17, 116, 35);
    }

    static void addPatternSlots(DummyContainer dummy, int inputLeft, int inputTop, int outputLeft, int outputTop) {
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                dummy.addDummySlot(x + y * 3, inputLeft + x * 18, inputTop + y * 18);
            }
        }
        for (int i = 0; i < Pattern.RESULT_SLOTS; i++) {
            dummy.addDummySlot(Pattern.INGREDIENT_SLOTS + i, outputLeft + i * 18, outputTop);
        }
    }

    @Override
    public GuiProvider template() {
        return new PatternGuiProvider(getId());
    }
}
