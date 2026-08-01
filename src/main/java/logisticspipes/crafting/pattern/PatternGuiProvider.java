package logisticspipes.crafting.pattern;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.PatternSatelliteInfo;
import logisticspipes.crafting.PipeFluidPatternSatelliteLogistics;
import logisticspipes.crafting.PipeItemsPatternSatelliteLogistics;
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

    static void addPatternSlots(DummyContainer dummy, AbstractPattern pattern) {
        addPatternSlots(dummy, pattern, 26, 17, 116, 35);
    }

    public static void addPatternSlots(DummyContainer dummy, AbstractPattern pattern, int inputLeft, int inputTop,
            int outputLeft, int outputTop) {
        if (pattern == null) {
            pattern = ItemPattern.fromStack(null);
        }
        if (dummy instanceof PatternContainer) {
            ((PatternContainer) dummy).addPatternSlots(pattern, inputLeft, inputTop, outputLeft, outputTop);
            return;
        }
        PatternSlotLayout layout = new PatternSlotLayout(pattern, inputLeft, inputTop, outputLeft, outputTop);
        for (int slot = 0; slot < pattern.getIngredientSlotCount(); slot++) {
            dummy.addDummySlot(slot, layout.inputX(slot), layout.inputY(slot));
        }
        for (int slot = 0; slot < pattern.getResultSlotCount(); slot++) {
            dummy.addDummySlot(pattern.getResultSlotStart() + slot, layout.outputX(slot), layout.outputY(slot));
        }
    }

    @Override
    public DummyContainer getContainer(EntityPlayer player) {
        PatternInventory inventory = new PatternInventory(player, inventorySlot);
        if (!inventory.isUseableByPlayer(player)) {
            satellites = new ArrayList<>();
            return null;
        }
        satellites = PipeItemsPatternSatelliteLogistics.getKnownSatellitesFor(player);
        satellites.addAll(PipeFluidPatternSatelliteLogistics.getKnownSatellitesFor(player));
        PatternContainer dummy = new PatternContainer(player.inventory, inventory);
        addPatternSlots(dummy, ItemPattern.fromStack(inventory.getPatternStack()));
        dummy.addNormalSlotsForPlayerInventory(8, 116);
        return dummy;
    }

    @Override
    public GuiProvider template() {
        return new PatternGuiProvider(getId());
    }
}
