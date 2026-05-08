package logisticspipes.network.packets;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import logisticspipes.LogisticsPipes;
import logisticspipes.blocks.crafting.LogisticsCraftingTableTileEntity;
import logisticspipes.crafting.Pattern;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeBlockRequestTable;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class NEISetCraftingRecipe extends CoordinatesPacket {

    @Getter
    @Setter
    private ItemStack[] content = new ItemStack[9];
    @Getter
    @Setter
    private int patternInventorySlot = -1;
    @Getter
    @Setter
    private ItemStack result;

    public NEISetCraftingRecipe(int id) {
        super(id);
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (patternInventorySlot >= 0) {
            handlePatternRecipePacket(player);
            return;
        }
        TileEntity tile = getTile(player.worldObj, TileEntity.class);
        if (tile instanceof LogisticsCraftingTableTileEntity) {
            ((LogisticsCraftingTableTileEntity) tile).handleNEIRecipePacket(getContent());
        } else if (tile instanceof LogisticsTileGenericPipe
                && ((LogisticsTileGenericPipe) tile).pipe instanceof PipeBlockRequestTable) {
                    ((PipeBlockRequestTable) ((LogisticsTileGenericPipe) tile).pipe)
                            .handleNEIRecipePacket(getContent());
                }
    }

    @Override
    public ModernPacket template() {
        return new NEISetCraftingRecipe(getId());
    }

    private void handlePatternRecipePacket(EntityPlayer player) {
        if (patternInventorySlot >= player.inventory.mainInventory.length) {
            return;
        }
        ItemStack pattern = player.inventory.mainInventory[patternInventorySlot];
        if (pattern == null || pattern.getItem() != LogisticsPipes.LogisticsPattern) {
            return;
        }
        ItemStack[] recipeContent = content != null ? content : new ItemStack[0];
        for (int i = 0; i < Pattern.INGREDIENT_SLOTS; i++) {
            Pattern.setStackInSlot(pattern, i, i < recipeContent.length ? copy(recipeContent[i]) : null);
        }
        for (int i = 0; i < Pattern.RESULT_SLOTS; i++) {
            Pattern.setStackInSlot(pattern, Pattern.INGREDIENT_SLOTS + i, i == 0 ? copy(result) : null);
        }
        Pattern.setPatternType(pattern, Pattern.PatternType.CRAFTING);
        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);

        data.writeInt(patternInventorySlot);
        writeItemStack(data, result);
        ItemStack[] recipeContent = content != null ? content : new ItemStack[0];
        data.writeInt(recipeContent.length);

        for (int i = 0; i < recipeContent.length; i++) {
            final ItemStack itemstack = recipeContent[i];

            if (itemstack != null) {
                data.writeByte(i);
                data.writeInt(Item.getIdFromItem(itemstack.getItem()));
                data.writeInt(itemstack.stackSize);
                data.writeInt(itemstack.getItemDamage());
                data.writeNBTTagCompound(itemstack.getTagCompound());
            }
        }
        data.writeByte(-1); // mark packet end
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);

        patternInventorySlot = data.readInt();
        result = readItemStack(data);
        content = new ItemStack[data.readInt()];

        byte index = data.readByte();

        while (index != -1) { // read until the end
            final int itemID = data.readInt();
            int stackSize = data.readInt();
            int damage = data.readInt();
            ItemStack stack = new ItemStack(Item.getItemById(itemID), stackSize, damage);
            stack.setTagCompound(data.readNBTTagCompound());
            content[index] = stack;
            index = data.readByte(); // read the next slot
        }
    }

    private static void writeItemStack(LPDataOutputStream data, ItemStack stack) throws IOException {
        data.writeBoolean(stack != null);
        if (stack == null) {
            return;
        }
        data.writeInt(Item.getIdFromItem(stack.getItem()));
        data.writeInt(stack.stackSize);
        data.writeInt(stack.getItemDamage());
        data.writeNBTTagCompound(stack.getTagCompound());
    }

    private static ItemStack readItemStack(LPDataInputStream data) throws IOException {
        if (!data.readBoolean()) {
            return null;
        }
        Item item = Item.getItemById(data.readInt());
        int stackSize = data.readInt();
        int damage = data.readInt();
        if (item == null || stackSize <= 0) {
            return null;
        }
        ItemStack stack = new ItemStack(item, stackSize, damage);
        stack.setTagCompound(data.readNBTTagCompound());
        return stack;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack != null && stack.stackSize > 0 ? stack.copy() : null;
    }
}
