package logisticspipes.pipes;

import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.gui.modularUI.modules.PipeFluidBasicMui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.LogisticsPipes;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.network.GuiIDs;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.transport.PipeFluidTransportLogistics;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.tuples.Pair;
import net.minecraftforge.fluids.IFluidTank;

public class PipeFluidBasic extends FluidRoutedPipe implements IFluidSink, IMUICompatiblePipeV2 {

    public final FluidTank filterTank = new FluidTank(1);
    private final PlayerCollectionList guiOpenedBy = new PlayerCollectionList();

    public PipeFluidBasic(Item item) {
        super(item);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_LIQUID_BASIC;
    }

    @Override
    public boolean canInsertFromSideToTanks() {
        return true;
    }

    @Override
    public void onWrenchClicked(EntityPlayer entityplayer) {
        entityplayer.openGui(LogisticsPipes.instance, GuiIDs.GUI_Fluid_Basic_ID, getWorld(), getX(), getY(), getZ());
    }

    @Override
    public int sinkAmount(FluidStack stack) {
        if (!guiOpenedBy.isEmpty()) {
            return 0; // Don't sink when the gui is open
        }

        if(filterTank.getFluid() == null) return 0;
        if(!filterTank.getFluid().isFluidEqual(stack)) return 0;

        FluidIdentifier ident = FluidIdentifier.get(stack);

        // using long for our internal calculations avoids an overflow when tanks report
        // a capacity of Integer.MAX_VALUE (notably gt5 super tank when set to void fluids)
        long onTheWay = (long) this.countOnRoute(ident);
        long freeSpace = -onTheWay;
        long internalCapacity = (long) ((PipeFluidTransportLogistics) transport).getSideCapacity();

        for (Pair<TileEntity, ForgeDirection> pair : getAdjacentTanks(true)) {
            if (!(pair.getValue1() instanceof IFluidHandler handler)) {
                continue;
            }

            ForgeDirection dir = pair.getValue2().getOpposite();

            // ensure we are actually able to fill this handler, and it's not some output tank or such
            int simulatedFill = handler.fill(dir, stack, false);
            if (simulatedFill <= 0) {
                continue;
            }

            FluidTank tank = ((PipeFluidTransportLogistics) transport).sideTanks[pair.getValue2().ordinal()];
            long internalFreeSpace = (long) ident.getFreeSpaceInsideTank(tank);
            long externalFreeSpace = (long) ident.getFreeSpaceInsideTank(handler, dir);

            // don't count this entity if we have enough in our internal buffer to fill it
            if (internalCapacity - internalFreeSpace > externalFreeSpace) {
                continue;
            }

            freeSpace += internalFreeSpace;
            freeSpace += externalFreeSpace;
        }

        int clampedFreeSpace = (int) Math.min(freeSpace, (long) Integer.MAX_VALUE);
        return Math.min(clampedFreeSpace, stack.amount);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        filterTank.writeToNBT(nbttagcompound);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        filterTank.readFromNBT(nbttagcompound);
    }

    @Override
    public boolean canInsertToTanks() {
        return true;
    }

    public void guiOpenedByPlayer(EntityPlayer player) {
        guiOpenedBy.add(player);
    }

    public void guiClosedByPlayer(EntityPlayer player) {
        guiOpenedBy.remove(player);
    }

    @Override
    public boolean canReceiveFluid() {
        return false;
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return PipeGuiFactory.fromMui(new PipeFluidBasicMui(this));
    }

    public String getFluidName() {
        if(filterTank.getFluid() == null) return "None";
        return filterTank.getFluid().getLocalizedName();
    }
}
