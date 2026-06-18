package logisticspipes.crafting;

import logisticspipes.LogisticsPipes;
import logisticspipes.network.GuiIDs;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.packets.satpipe.PatternSatelliteSetName;
import logisticspipes.network.packets.satpipe.SatPipeSetID;
import logisticspipes.pipes.PipeItemsSatelliteLogistics;
import logisticspipes.proxy.MainProxy;
import logisticspipes.routing.IRouter;
import logisticspipes.security.SecuritySettings;
import lombok.Getter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.WeakHashMap;

public class PipeItemsPatternSatelliteLogistics extends PipeItemsSatelliteLogistics {

    private static final Set<PipeItemsPatternSatelliteLogistics> ALL_PATTERN_SATELLITES = Collections
            .newSetFromMap(new WeakHashMap<>());
    private static final String UUID_TAG = "patternSatelliteUuid";
    private static final String NAME_TAG = "patternSatelliteName";

    @Getter
    private String satelliteUuid = UUID.randomUUID().toString();
    private String satelliteName = "";

    public PipeItemsPatternSatelliteLogistics(Item item) {
        super(item);
    }

    public static void cleanup() {
        ALL_PATTERN_SATELLITES.clear();
    }

    public static PipeItemsPatternSatelliteLogistics findById(int satelliteId) {
        if (satelliteId <= 0) {
            return null;
        }
        for (PipeItemsPatternSatelliteLogistics satellite : ALL_PATTERN_SATELLITES) {
            if (satellite != null && satellite.satelliteId == satelliteId) {
                return satellite;
            }
        }
        return null;
    }

    public static PipeItemsPatternSatelliteLogistics findByUuid(String satelliteUuid) {
        if (satelliteUuid == null || satelliteUuid.isEmpty()) {
            return null;
        }
        for (PipeItemsPatternSatelliteLogistics satellite : ALL_PATTERN_SATELLITES) {
            if (satellite != null && satelliteUuid.equals(satellite.satelliteUuid)) {
                return satellite;
            }
        }
        return null;
    }

    public static List<Integer> getKnownSatelliteIds() {
        TreeSet<Integer> ids = new TreeSet<>();
        for (PipeItemsPatternSatelliteLogistics satellite : ALL_PATTERN_SATELLITES) {
            if (satellite != null && satellite.satelliteId > 0) {
                ids.add(satellite.satelliteId);
            }
        }
        return new ArrayList<>(ids);
    }

    public static List<PatternSatelliteInfo> getKnownSatellitesFor(EntityPlayer player) {
        Set<Integer> favoriteIds = getFavoriteSatelliteIds(player);
        Set<String> favoriteUuids = getFavoriteSatelliteUuids(player);
        List<PatternSatelliteInfo> satellites = new ArrayList<>();
        int playerDimension = player != null && player.worldObj != null
            ? MainProxy.getDimensionForWorld(player.worldObj)
                : Integer.MIN_VALUE;
        for (PipeItemsPatternSatelliteLogistics satellite : ALL_PATTERN_SATELLITES) {
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
                            favoriteIds.contains(satellite.satelliteId)
                                    || favoriteUuids.contains(satellite.satelliteUuid),
                            satellite.satelliteUuid,
                            satellite.getDisplayName()));
        }
        satellites.sort((left, right) -> {
            if (left.favorite() != right.favorite()) {
                return left.favorite() ? -1 : 1;
            }
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

    private static boolean isSelectableSatellite(PipeItemsPatternSatelliteLogistics satellite) {
        return satellite != null && satellite.satelliteId > 0
                && satellite.container != null
                && !satellite.container.isInvalid()
                && satellite.getWorld() != null;
    }

    private static Set<Integer> getFavoriteSatelliteIds(EntityPlayer player) {
        Set<Integer> favoriteIds = new HashSet<>();
        if (player == null || player.inventory == null) {
            return favoriteIds;
        }
        for (ItemStack stack : player.inventory.mainInventory) {
            for (int id : ItemMemoryChip.getPatternSatelliteIds(stack)) {
                if (id > 0) {
                    favoriteIds.add(id);
                }
            }
        }
        return favoriteIds;
    }

    private static Set<String> getFavoriteSatelliteUuids(EntityPlayer player) {
        Set<String> favoriteUuids = new HashSet<>();
        if (player == null || player.inventory == null) {
            return favoriteUuids;
        }
        for (ItemStack stack : player.inventory.mainInventory) {
            for (ItemMemoryChip.StoredPatternSatellite satellite : ItemMemoryChip.getPatternSatellites(stack)) {
                if (!satellite.uuid().isEmpty()) {
                    favoriteUuids.add(satellite.uuid());
                }
            }
        }
        return favoriteUuids;
    }

    private static int getDistance(EntityPlayer player, int playerDimension,
                                   PipeItemsPatternSatelliteLogistics satellite, int satelliteDimension) {
        if (player == null || playerDimension != satelliteDimension) {
            return -1;
        }
        double dx = satellite.getX() + 0.5D - player.posX;
        double dy = satellite.getY() + 0.5D - player.posY;
        double dz = satellite.getZ() + 0.5D - player.posZ;
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
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

    @Override
    public void enabledUpdateEntity() {
        super.enabledUpdateEntity();
        if (!MainProxy.isClient(getWorld()) && isNthTick(40)) {
            ensureAllSatelliteStatus();
        }
    }

    public void setSatelliteName(String satelliteName) {
        this.satelliteName = satelliteName == null ? "" : satelliteName.trim();
        ensureAllSatelliteStatus();
        if (container != null) {
            container.markDirty();
            container.sendUpdateToClient();
        }
    }

    @Override
    protected int findId(int increment) {
        if (MainProxy.isClient(getWorld())) {
            return satelliteId;
        }
        int potentialId = satelliteId;
        boolean conflict = true;
        while (conflict) {
            potentialId += increment;
            if (potentialId < 0) {
                return 0;
            }
            conflict = false;
            for (PipeItemsPatternSatelliteLogistics satellite : ALL_PATTERN_SATELLITES) {
                if (satellite != this && satellite.satelliteId == potentialId) {
                    conflict = true;
                    break;
                }
            }
        }
        return potentialId;
    }

    @Override
    protected void ensureAllSatelliteStatus() {
        if (MainProxy.isClient()) {
            return;
        }
        if (satelliteId == 0) {
            satelliteId = findId(1);
        }
        if (satelliteId == 0) {
            ALL_PATTERN_SATELLITES.remove(this);
        } else {
            ALL_PATTERN_SATELLITES.add(this);
            ensureUniqueDisplayNameInNetwork();
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
        for (PipeItemsPatternSatelliteLogistics satellite : ALL_PATTERN_SATELLITES) {
            if (satellite == null || satellite == this || !isSelectableSatellite(satellite)) {
                continue;
            }
            if (displayName.equalsIgnoreCase(satellite.getDisplayName()) && isInSameNetwork(satellite)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInSameNetwork(PipeItemsPatternSatelliteLogistics other) {
        try {
            IRouter router = getRouter();
            IRouter otherRouter = other.getRouter();
            return router == otherRouter
                || (router != null && otherRouter != null && !router.getDistanceTo(otherRouter).isEmpty());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public boolean handleClick(EntityPlayer player, SecuritySettings settings) {
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() == LogisticsPipes.LogisticsMemoryChip) {
            if (MainProxy.isServer(getWorld())) {
                if (settings == null || settings.openGui) {
                    ensureAllSatelliteStatus();
                    if (held.hasDisplayName()) {
                        setSatelliteName(held.getDisplayName());
                    }
                    boolean added = ItemMemoryChip.addPatternSatellite(held, this);
                    player.addChatComponentMessage(
                            new ChatComponentText(
                                    (added ? "Stored" : "Already stored") + " pattern satellite "
                                            + getDisplayName()
                                            + " on memory chip"));
                } else {
                    player.addChatComponentMessage(
                            new net.minecraft.util.ChatComponentTranslation("lp.chat.permissiondenied"));
                }
            }
            return true;
        }
        return super.handleClick(player, settings);
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
        if (MainProxy.isClient(getWorld())) {
            return;
        }
        ALL_PATTERN_SATELLITES.remove(this);
    }
}
