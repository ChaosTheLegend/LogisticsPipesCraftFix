package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import logisticspipes.LogisticsPipes;
import logisticspipes.pipes.PipeItemsSatelliteLogistics;
import logisticspipes.proxy.MainProxy;
import logisticspipes.security.SecuritySettings;

public class PipeItemsPatternSatelliteLogistics extends PipeItemsSatelliteLogistics {

    private static final Set<PipeItemsPatternSatelliteLogistics> ALL_PATTERN_SATELLITES = Collections
            .newSetFromMap(new WeakHashMap<>());

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
        List<PatternSatelliteInfo> satellites = new ArrayList<>();
        int playerDimension = player != null && player.worldObj != null ? MainProxy.getDimensionForWorld(player.worldObj)
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
                            favoriteIds.contains(satellite.satelliteId)));
        }
        satellites.sort(new Comparator<PatternSatelliteInfo>() {

            @Override
            public int compare(PatternSatelliteInfo left, PatternSatelliteInfo right) {
                if (left.isFavorite() != right.isFavorite()) {
                    return left.isFavorite() ? -1 : 1;
                }
                boolean leftSameDimension = left.getDistance() >= 0;
                boolean rightSameDimension = right.getDistance() >= 0;
                if (leftSameDimension != rightSameDimension) {
                    return leftSameDimension ? -1 : 1;
                }
                if (leftSameDimension && left.getDistance() != right.getDistance()) {
                    return Integer.compare(left.getDistance(), right.getDistance());
                }
                return Integer.compare(left.getId(), right.getId());
            }
        });
        return satellites;
    }

    private static boolean isSelectableSatellite(PipeItemsPatternSatelliteLogistics satellite) {
        return satellite != null
                && satellite.satelliteId > 0
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

    private static int getDistance(EntityPlayer player, int playerDimension, PipeItemsPatternSatelliteLogistics satellite,
            int satelliteDimension) {
        if (player == null || playerDimension != satelliteDimension) {
            return -1;
        }
        double dx = satellite.getX() + 0.5D - player.posX;
        double dy = satellite.getY() + 0.5D - player.posY;
        double dz = satellite.getZ() + 0.5D - player.posZ;
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
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
        }
    }

    @Override
    public boolean handleClick(EntityPlayer player, SecuritySettings settings) {
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() == LogisticsPipes.LogisticsMemoryChip) {
            if (MainProxy.isServer(getWorld())) {
                if (settings == null || settings.openGui) {
                    ensureAllSatelliteStatus();
                    boolean added = ItemMemoryChip.addPatternSatelliteId(held, satelliteId);
                    player.addChatComponentMessage(
                            new ChatComponentText(
                                    (added ? "Stored" : "Already stored") + " pattern satellite "
                                            + satelliteId
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
    public void onAllowedRemoval() {
        if (MainProxy.isClient(getWorld())) {
            return;
        }
        ALL_PATTERN_SATELLITES.remove(this);
    }
}
