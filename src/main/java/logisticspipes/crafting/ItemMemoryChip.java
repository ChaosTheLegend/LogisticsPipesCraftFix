package logisticspipes.crafting;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.items.LogisticsItem;
import logisticspipes.utils.string.ChatColor;
import net.minecraft.world.World;

public class ItemMemoryChip extends LogisticsItem {

    private static final String PATTERN_SATELLITE_IDS_TAG = "patternSatelliteIds";

    public static int[] getPatternSatelliteIds(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemMemoryChip) || !stack.hasTagCompound()) {
            return new int[0];
        }
        return stack.getTagCompound().getIntArray(PATTERN_SATELLITE_IDS_TAG);
    }

    @Override
    public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
        return true;
    }

    public static boolean addPatternSatelliteId(ItemStack stack, int satelliteId) {
        if (stack == null || !(stack.getItem() instanceof ItemMemoryChip) || satelliteId <= 0) {
            return false;
        }
        TreeSet<Integer> ids = new TreeSet<>();
        for (int id : getPatternSatelliteIds(stack)) {
            if (id > 0) {
                ids.add(id);
            }
        }
        boolean added = ids.add(satelliteId);
        NBTTagCompound tag = getOrCreateTag(stack);
        int[] storedIds = new int[ids.size()];
        int index = 0;
        for (Integer id : ids) {
            storedIds[index++] = id;
        }
        tag.setIntArray(PATTERN_SATELLITE_IDS_TAG, storedIds);
        return added;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        int[] ids = getPatternSatelliteIds(stack);
        if (ids.length == 0) {
            tooltip.add(ChatColor.GRAY + "No pattern satellites stored");
            return;
        }
        tooltip.add(ChatColor.AQUA + "Pattern satellites: " + ChatColor.WHITE + Arrays.toString(ids));
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }
}
