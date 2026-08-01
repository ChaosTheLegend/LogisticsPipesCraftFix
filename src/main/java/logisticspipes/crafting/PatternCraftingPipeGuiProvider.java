package logisticspipes.crafting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.pattern.ItemPattern;
import logisticspipes.crafting.pattern.PatternContainer;
import logisticspipes.crafting.pattern.PatternGuiProvider;
import logisticspipes.crafting.pattern.PipePatternInventory;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractguis.GuiProvider;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.gui.DummyContainer;

public class PatternCraftingPipeGuiProvider extends ModuleCoordinatesGuiProvider {

    private int blockingMode;
    private int selectedPatternSlot;
    private List<PatternSatelliteInfo> satellites = new ArrayList<>();
    private PatternCraftingHudState hudState = PatternCraftingHudState.empty();

    public PatternCraftingPipeGuiProvider(int id) {
        super(id);
    }

    public PatternCraftingPipeGuiProvider setBlockingMode(int blockingMode) {
        this.blockingMode = blockingMode;
        return this;
    }

    @Override
    public Object getClientGui(EntityPlayer player) {
        PipeItemsPatternCraftingLogistics pipe = getPatternPipe(player);
        if (pipe == null) {
            return null;
        }
        PipeItemsPatternCraftingLogistics.BlockingMode[] values = PipeItemsPatternCraftingLogistics.BlockingMode
                .values();
        pipe.setBlockingMode(values[Math.max(0, Math.min(values.length - 1, blockingMode))]);
        pipe.setHudState(hudState);
        return new PatternCraftingPipeGui(player, pipe, selectedPatternSlot, satellites);
    }

    @Override
    public DummyContainer getContainer(EntityPlayer player) {
        PipeItemsPatternCraftingLogistics pipe = getPatternPipe(player);
        if (pipe == null) {
            return null;
        }
        selectedPatternSlot = findInitialPatternSlot(pipe, selectedPatternSlot);
        satellites = PipeItemsPatternSatelliteLogistics.getKnownSatellitesFor(player);
        satellites.addAll(PipeFluidPatternSatelliteLogistics.getKnownSatellitesFor(player));
        hudState = pipe.getPatternModule().getHudState();
        PatternContainer dummy = new PatternContainer(
                player.inventory,
                new PipePatternInventory(pipe, selectedPatternSlot));
        PatternGuiProvider.addPatternSlots(
                dummy,
                ItemPattern.fromStack(pipe.getPatternModule().getPatternItemStack(selectedPatternSlot)),
                27,
                57,
                117,
                75);
        addPatternSlots(dummy, pipe);
        dummy.addNormalSlotsForPlayerInventory(32, 156);
        return dummy;
    }

    static void addPatternSlots(DummyContainer dummy, PipeItemsPatternCraftingLogistics pipe) {
        for (int i = 0; i < 9; i++) {
            dummy.addRestrictedSlot(
                    i,
                    pipe.getPatternModule().getPatternInventory(),
                    32 + i * 18,
                    20,
                    stack -> stack != null && stack.getItem() == LogisticsPipes.LogisticsPattern);
        }
    }

    public PatternCraftingPipeGuiProvider setSelectedPatternSlot(int selectedPatternSlot) {
        this.selectedPatternSlot = selectedPatternSlot;
        return this;
    }

    private int findInitialPatternSlot(PipeItemsPatternCraftingLogistics pipe, int preferredSlot) {
        if (isPatternSlotUsable(pipe, preferredSlot)) {
            return preferredSlot;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (isPatternSlotUsable(pipe, slot)) {
                return slot;
            }
        }
        return 0;
    }

    private boolean isPatternSlotUsable(PipeItemsPatternCraftingLogistics pipe, int slot) {
        ItemStack pattern = pipe.getPatternModule().getPatternItemStack(slot);
        return pattern != null && pattern.getItem() == LogisticsPipes.LogisticsPattern;
    }

    private PipeItemsPatternCraftingLogistics getPatternPipe(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof PipeItemsPatternCraftingLogistics)) {
            return null;
        }
        return (PipeItemsPatternCraftingLogistics) tile.pipe;
    }

    @Override
    public GuiProvider template() {
        return new PatternCraftingPipeGuiProvider(getId());
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(blockingMode);
        data.writeInt(selectedPatternSlot);
        data.writeList(satellites, (stream, satellite) -> satellite.writeData(stream));
        hudState.writeData(data);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        blockingMode = data.readInt();
        selectedPatternSlot = data.readInt();
        satellites = data.readList(PatternSatelliteInfo::readData);
        hudState = PatternCraftingHudState.readData(data);
    }
}
