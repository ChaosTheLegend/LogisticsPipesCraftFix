package logisticspipes.network.packets.crafting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.pattern.AbstractPattern;
import logisticspipes.crafting.pattern.DefaultPattern;
import logisticspipes.crafting.pattern.ItemPattern;
import logisticspipes.crafting.pattern.PatternContainer;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Getter
@Accessors(chain = true)
public class NEISetPatternCraftingRecipe extends CoordinatesPacket {

    private List<IPatternStack> inputs = new ArrayList<>();
    private List<Integer> indices = new ArrayList<>();
    private List<IPatternStack> outputs = new ArrayList<>();
    private int patternInventorySlot = -1;
    private ItemStack result;

    public NEISetPatternCraftingRecipe(int id) {
        super(id);
    }

    @Override
    public void processPacket(EntityPlayer player) {

        if (patternInventorySlot >= 0) {

            importRecipe(player, patternInventorySlot, inputs, indices, outputs);
        }

        // TileEntity tile = getTile(player.worldObj, TileEntity.class);
        // if (tile instanceof LogisticsCraftingTableTileEntity) {
        // ((LogisticsCraftingTableTileEntity) tile).handleNEIRecipePacket(getInputs());
        // } else if (tile instanceof LogisticsTileGenericPipe
        // && ((LogisticsTileGenericPipe) tile).pipe instanceof PipeBlockRequestTable) {
        // ((PipeBlockRequestTable) ((LogisticsTileGenericPipe) tile).pipe)
        // .handleNEIRecipePacket(getInputs());
        // }
    }

    public void importRecipe(EntityPlayer player, int patternInventorySlot, @NonNull List<IPatternStack> inputs,
            @NonNull List<Integer> indices, @NonNull List<IPatternStack> outputs) {
        if (patternInventorySlot < 0 || patternInventorySlot >= player.inventory.mainInventory.length) return;

        ItemStack patternStack = player.inventory.mainInventory[patternInventorySlot];
        if (patternStack == null || patternStack.getItem() != LogisticsPipes.LogisticsPattern) return;

        boolean processingPattern = outputs.size() > DefaultPattern.RESULT_SLOTS
                || inputs.size() > DefaultPattern.INGREDIENT_SLOTS
                || usesProcessingInputSlot(indices);
        ItemPattern.setProcessingPattern(patternStack, processingPattern);

        AbstractPattern pattern = ItemPattern.fromStack(patternStack);
        pattern.setInputsAndOutputs(inputs, indices, outputs);

        // reload the gui from the new pattern
        if (!(player.openContainer instanceof PatternContainer container)) return;
        container.reloadFromPattern(pattern);
    }

    private boolean usesProcessingInputSlot(List<Integer> indices) {
        for (Integer index : indices) {
            if (index != null && index >= DefaultPattern.INGREDIENT_SLOTS) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ModernPacket template() {
        return new NEISetPatternCraftingRecipe(getId());
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);

        data.writeInt(patternInventorySlot);
        data.writeList(inputs, (data1, object) -> {
            var nbt = new NBTTagCompound();
            object.writeToNBT(nbt);
            data1.writeNBTTagCompound(nbt);
        });

        var indicesNBT = new NBTTagCompound();
        for (int i = 0; i < indices.size(); i++) {
            indicesNBT.setInteger(String.valueOf(i), indices.get(i));
        }
        data.writeNBTTagCompound(indicesNBT);

        data.writeList(outputs, (data1, object) -> {
            var nbt = new NBTTagCompound();
            object.writeToNBT(nbt);
            data1.writeNBTTagCompound(nbt);
        });
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);

        patternInventorySlot = data.readInt();
        inputs = data.readList(data1 -> IPatternStack.readFromNBT(data1.readNBTTagCompound()));
        var indicesNBT = data.readNBTTagCompound();
        for (int i = 0; i < inputs.size(); i++) {
            indices.add(indicesNBT.getInteger(String.valueOf(i)));
        }
        outputs = data.readList(data1 -> IPatternStack.readFromNBT(data1.readNBTTagCompound()));
    }
}
