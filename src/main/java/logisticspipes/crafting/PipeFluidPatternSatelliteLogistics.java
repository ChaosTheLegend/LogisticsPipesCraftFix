package logisticspipes.crafting;

import logisticspipes.LogisticsPipes;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.network.GuiIDs;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.packets.satpipe.PatternSatelliteSetName;
import logisticspipes.network.packets.satpipe.SatPipeSetID;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class PipeFluidPatternSatelliteLogistics extends logisticspipes.pipes.PipeFluidSatellite {

    private static final Set<PipeFluidPatternSatelliteLogistics> ALL_PATTERN_FLUID_SATELLITES = Collections
        .newSetFromMap(new WeakHashMap<>());
    private static final String UUID_TAG = "patternFluidSatelliteUuid";
    private static final String NAME_TAG = "patternFluidSatelliteName";

    private String satelliteUuid = UUID.randomUUID().toString();
    private String satelliteName = "";

    public PipeFluidPatternSatelliteLogistics(Item item) {
        super(item);
    }

    public static void cleanup() {
        ALL_PATTERN_FLUID_SATELLITES.clear();
    }

    public static PipeFluidPatternSatelliteLogistics findById(int satelliteId) {
        if (satelliteId <= 0) {
            return null;
        }
        for (PipeFluidPatternSatelliteLogistics satellite : ALL_PATTERN_FLUID_SATELLITES) {
            if (satellite != null && satellite.satelliteId == satelliteId) {
                return satellite;
            }
        }
        return null;
    }

    public static PipeFluidPatternSatelliteLogistics findByUuid(String satelliteUuid) {
        if (satelliteUuid == null || satelliteUuid.isEmpty()) {
            return null;
        }
        for (PipeFluidPatternSatelliteLogistics satellite : ALL_PATTERN_FLUID_SATELLITES) {
            if (satellite != null && satelliteUuid.equals(satellite.satelliteUuid)) {
                return satellite;
            }
        }
        return null;
    }

    public static List<PatternSatelliteInfo> getKnownSatellitesFor(EntityPlayer player) {
        List<PatternSatelliteInfo> satellites = new ArrayList<>();
        int playerDimension = player != null && player.worldObj != null
            ? MainProxy.getDimensionForWorld(player.worldObj)
            : Integer.MIN_VALUE;
        for (PipeFluidPatternSatelliteLogistics satellite : ALL_PATTERN_FLUID_SATELLITES) {
            if (!isSelectableSatellite(satellite)) {
                continue;
            }
            int dimension = MainProxy.getDimensionForWorld(satellite.getWorld());
            satellites.add(
                new PatternSatelliteInfo(
                    satellite.satelliteId,
                    satellite.getX(),
                    satellite.getY(),
                    satellite.getZ(),
                    dimension,
                    getDistance(player, playerDimension, satellite, dimension),
                    false,
                    satellite.satelliteUuid,
                    satellite.getDisplayName(),
                    PatternSatelliteInfo.SatelliteType.FLUID));
        }
        satellites.sort((left, right) -> {
            boolean leftSameDimension = left.distance() >= 0;
            boolean rightSameDimension = right.distance() >= 0;
            if (leftSameDimension != rightSameDimension) {
                return leftSameDimension ? -1 : 1;
            }
            if (leftSameDimension && left.distance() != right.distance()) {
                return Integer.compare(left.distance(), right.distance());
            }
            return Integer.compare(left.id(), right.id());
        });
        return satellites;
    }

    private static boolean isSelectableSatellite(PipeFluidPatternSatelliteLogistics satellite) {
        return satellite != null && satellite.satelliteId > 0
            && satellite.container != null
            && !satellite.container.isInvalid()
            && satellite.getWorld() != null;
    }

    private static int getDistance(EntityPlayer player, int playerDimension,
                                   PipeFluidPatternSatelliteLogistics satellite, int satelliteDimension) {
        if (player == null || playerDimension != satelliteDimension) {
            return -1;
        }
        double dx = satellite.getX() + 0.5D - player.posX;
        double dy = satellite.getY() + 0.5D - player.posY;
        double dz = satellite.getZ() + 0.5D - player.posZ;
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    public String getSatelliteUuid() {
        return satelliteUuid;
    }

    public String getDisplayName() {
        return satelliteName == null || satelliteName.trim().isEmpty() ? Integer.toString(satelliteId)
            : satelliteName.trim();
    }

    /**
     * Returns the player-defined label without falling back to the internal numeric satellite id.
     */
    public String getSatelliteName() {
        return satelliteName == null ? "" : satelliteName.trim();
    }

    public void setSatelliteName(String satelliteName) {
        this.satelliteName = satelliteName == null ? "" : satelliteName.trim();
        ensureAllSatelliteStatus();
        if (container != null) {
            container.markDirty();
            container.sendUpdateToClient();
        }
    }

    /**
     * Retrieves cancelled craft fluid from adjacent tanks and routes it back into normal storage.
     *
     * @return amount that was drained and queued for storage routing
     */
    public int retrieveFluidToStorage(FluidIdentifier fluid, int amount) {
        if (fluid == null || amount <= 0 || MainProxy.isClient(getWorld())) {
            return 0;
        }
        int remaining = amount;
        int retrieved = 0;
        for (Pair<TileEntity, ForgeDirection> pair : getAdjacentTanks(false)) {
            if (remaining <= 0) {
                break;
            }
            if (!(pair.getValue1() instanceof IFluidHandler tank)) {
                continue;
            }
            ForgeDirection side = pair.getValue2().getOpposite();
            FluidTankInfo[] tanks = tank.getTankInfo(side);
            if (tanks == null) {
                continue;
            }
            for (FluidTankInfo tankInfo : tanks) {
                if (remaining <= 0) {
                    break;
                }
                if (tankInfo == null || tankInfo.fluid == null || !fluid.equals(FluidIdentifier.get(tankInfo.fluid))) {
                    continue;
                }
                int toDrain = Math.min(remaining, tankInfo.fluid.amount);
                FluidStack simulated = tank.drain(side, toDrain, false);
                if (simulated == null || simulated.amount <= 0 || !fluid.equals(FluidIdentifier.get(simulated))) {
                    continue;
                }
                FluidStack drained = tank.drain(side, simulated.amount, true);
                if (drained == null || drained.amount <= 0 || !fluid.equals(FluidIdentifier.get(drained))) {
                    continue;
                }
                queueFluidToStorage(drained, pair.getValue2());
                remaining -= drained.amount;
                retrieved += drained.amount;
            }
        }
        return retrieved;
    }

    private void queueFluidToStorage(FluidStack fluid, ForgeDirection from) {
        ItemIdentifierStack container = SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid);
        IRoutedItem routedItem = SimpleServiceLocator.routedItemHelper.createNewTravelItem(container);
        routedItem.setDestination(-1);
        routedItem.setTransportMode(TransportMode.Active);
        queueRoutedItem(routedItem, from == null ? ForgeDirection.UNKNOWN : from);
    }

    @Override
    public void enabledUpdateEntity() {
        super.enabledUpdateEntity();
        if (!MainProxy.isClient(getWorld()) && isNthTick(40)) {
            ensureAllSatelliteStatus();
        }
    }

    @Override
    protected void ensureAllSatelliteStatus() {
        if (MainProxy.isClient()) {
            return;
        }
        if (satelliteId == 0) {
            satelliteId = findId(1);
        }
        super.ensureAllSatelliteStatus();
        if (satelliteId == 0) {
            ALL_PATTERN_FLUID_SATELLITES.remove(this);
        } else {
            ALL_PATTERN_FLUID_SATELLITES.add(this);
            ensureUniqueDisplayNameInNetwork();
        }
    }

    @Override
    public void setSatelliteId(int satelliteId) {
        this.satelliteId = satelliteId;
        ensureAllSatelliteStatus();
    }

    @Override
    public void onWrenchClicked(EntityPlayer entityplayer) {
        ModernPacket idPacket = PacketHandler.getPacket(SatPipeSetID.class).setSatID(satelliteId).setPosX(getX())
            .setPosY(getY()).setPosZ(getZ());
        MainProxy.sendPacketToPlayer(idPacket, entityplayer);
        ModernPacket namePacket = PacketHandler.getPacket(PatternSatelliteSetName.class).setString(getSatelliteName())
            .setPosX(getX()).setPosY(getY()).setPosZ(getZ());
        MainProxy.sendPacketToPlayer(namePacket, entityplayer);
        entityplayer.openGui(LogisticsPipes.instance, GuiIDs.GUI_SatelitePipe_ID, getWorld(), getX(), getY(), getZ());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        satelliteUuid = nbttagcompound.hasKey(UUID_TAG) ? nbttagcompound.getString(UUID_TAG)
            : UUID.randomUUID().toString();
        satelliteName = nbttagcompound.getString(NAME_TAG);
        ensureAllSatelliteStatus();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        nbttagcompound.setString(UUID_TAG, satelliteUuid);
        nbttagcompound.setString(NAME_TAG, satelliteName == null ? "" : satelliteName);
    }

    @Override
    public void onAllowedRemoval() {
        super.onAllowedRemoval();
        if (!MainProxy.isClient(getWorld())) {
            ALL_PATTERN_FLUID_SATELLITES.remove(this);
        }
    }

    private void ensureUniqueDisplayNameInNetwork() {
        String displayName = getDisplayName();
        if (displayName.isEmpty()) {
            return;
        }
        int suffix = 2;
        String baseName = displayName;
        while (hasDisplayNameConflict(displayName)) {
            displayName = baseName + "-" + suffix++;
        }
        if (!displayName.equals(getDisplayName())) {
            satelliteName = displayName;
        }
    }

    private boolean hasDisplayNameConflict(String displayName) {
        for (PipeFluidPatternSatelliteLogistics satellite : ALL_PATTERN_FLUID_SATELLITES) {
            if (satellite == null || satellite == this || !isSelectableSatellite(satellite)) {
                continue;
            }
            if (displayName.equalsIgnoreCase(satellite.getDisplayName()) && isInSameNetwork(satellite)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInSameNetwork(PipeFluidPatternSatelliteLogistics other) {
        try {
            IRouter router = getRouter();
            IRouter otherRouter = other.getRouter();
            return router == otherRouter
                || (router != null && otherRouter != null && !router.getDistanceTo(otherRouter).isEmpty());
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
