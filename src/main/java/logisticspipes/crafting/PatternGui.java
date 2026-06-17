package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.EnumChatFormatting;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.PatternSatelliteAssignmentPacket;
import logisticspipes.network.packets.gui.PatternSlotActionPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SmallGuiButton;

public class PatternGui extends LogisticsBaseGuiScreen {

    private final PatternInventory patternInventory;
    private final List<PatternSatelliteInfo> satellites;
    public static final int CLEAR_BUTTON_ID = 0;
    public static final int MULTIPLE_BUTTON_ID = 1;
    public static final int ORE_DICT_BUTTON_ID = 2;
    private static final int SATELLITE_BUTTON_OFFSET = 100;

    public PatternGui(EntityPlayer player, IInventory inventory) {
        this(player, inventory, Collections.emptyList());
    }

    public PatternGui(EntityPlayer player, IInventory inventory, List<PatternSatelliteInfo> satellites) {
        super(176, 174, 0, 0);
        patternInventory = (PatternInventory) inventory;
        this.satellites = satellites == null ? Collections.emptyList() : new ArrayList<>(satellites);
        PatternContainer dummy = new PatternContainer(player.inventory, inventory);
        PatternGuiProvider.addPatternSlots(dummy);
        dummy.addNormalSlotsForPlayerInventory(8, 92);
        inventorySlots = dummy;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        addActionButtons();
    }

    private void addActionButtons() {
        GuiButton clearButton = new GuiButton(CLEAR_BUTTON_ID, guiLeft + 100, guiTop + 20, 50, 5, "X");
        addButton(clearButton);

        GuiButton multiplierButton = new GuiButton(MULTIPLE_BUTTON_ID, guiLeft + 100, guiTop + 30, 50, 5, "2x");
        addButton(multiplierButton);

        GuiButton oreDictButton = new GuiButton(ORE_DICT_BUTTON_ID, guiLeft + 100, guiTop + 40, 50, 5, "d");
        addButton(oreDictButton);

        for (int slot = 0; slot < getInputSize(); slot++) {
            int x = slot % 3;
            int y = slot / 3;
            GuiButton satelliteButton = new SmallGuiButton(
                    SATELLITE_BUTTON_OFFSET + slot,
                    guiLeft + 25 + x * 18,
                    guiTop + 70 + y * 8,
                    16,
                    8,
                    satelliteButtonLabel(slot));
            addButton(satelliteButton);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
        GuiGraphics.drawPlayerInventoryBackground(mc, guiLeft + 8, guiTop + 92);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                GuiGraphics.drawSlotBackground(mc, guiLeft + 25 + x * 18, guiTop + 16 + y * 18);
            }
        }
        for (int i = 0; i < Pattern.RESULT_SLOTS; i++) {
            GuiGraphics.drawSlotBackground(mc, guiLeft + 115 + i * 18, guiTop + 34);
        }
        mc.fontRenderer.drawString("Pattern", guiLeft + 8, guiTop + 6, 0x404040);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (!hasSubGui()) {
            drawSatelliteButtonTooltip(mouseX, mouseY);
        }
    }

    public int getInventorySlot() {
        return patternInventory.getInventorySlot();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case CLEAR_BUTTON_ID:
                Pattern.fromStack(patternInventory.getPatternStack()).clear();
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(PatternSlotActionPacket.class)
                                .setInventorySlot(patternInventory.getInventorySlot())
                                .setAction(PatternSlotActionPacket.Action.CLEAR.ordinal()));
                break;
            case MULTIPLE_BUTTON_ID:
                Pattern.fromStack(patternInventory.getPatternStack()).multiply(2);
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(PatternSlotActionPacket.class)
                                .setInventorySlot(patternInventory.getInventorySlot())
                                .setAction(PatternSlotActionPacket.Action.MULTIPLY_TWO.ordinal()));
                break;

        }
        if (button.id >= SATELLITE_BUTTON_OFFSET && button.id < SATELLITE_BUTTON_OFFSET + getInputSize()) {
            int inputSlot = button.id - SATELLITE_BUTTON_OFFSET;
            openSatelliteSelector(inputSlot);
        }
    }

    private void openSatelliteSelector(int inputSlot) {
        int currentSatelliteId = Pattern.fromStack(patternInventory.getPatternStack())
                .getSatelliteIdForInputSlot(inputSlot);
        setSubGui(
                new PatternSatelliteSelectorGui(
                        inputSlot,
                        currentSatelliteId,
                        satellites,
                        satelliteId -> setSatelliteForInputSlot(inputSlot, satelliteId)));
    }

    private void setSatelliteForInputSlot(int inputSlot, int satelliteId) {
        Pattern.fromStack(patternInventory.getPatternStack())
                .setSatelliteIdForInputSlot(inputSlot, satelliteId);
        updateSatelliteButtonLabels();
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(PatternSatelliteAssignmentPacket.class)
                        .setInventorySlot(patternInventory.getInventorySlot()).setInputSlot(inputSlot)
                        .setSatelliteId(satelliteId));
    }

    private void updateSatelliteButtonLabels() {
        for (Object entry : buttonList) {
            GuiButton button = (GuiButton) entry;
            if (button.id >= SATELLITE_BUTTON_OFFSET && button.id < SATELLITE_BUTTON_OFFSET + getInputSize()) {
                button.displayString = satelliteButtonLabel(button.id - SATELLITE_BUTTON_OFFSET);
            }
        }
    }

    private void drawSatelliteButtonTooltip(int mouseX, int mouseY) {
        for (Object entry : buttonList) {
            GuiButton button = (GuiButton) entry;
            if (!button.visible
                    || button.id < SATELLITE_BUTTON_OFFSET
                    || button.id >= SATELLITE_BUTTON_OFFSET + getInputSize()
                    || mouseX < button.xPosition
                    || mouseX >= button.xPosition + button.width
                    || mouseY < button.yPosition
                    || mouseY >= button.yPosition + button.height) {
                continue;
            }
            int inputSlot = button.id - SATELLITE_BUTTON_OFFSET;
            int satelliteId = Pattern.fromStack(patternInventory.getPatternStack()).getSatelliteIdForInputSlot(inputSlot);
            List<String> tooltip = new ArrayList<>();
            if (satelliteId <= 0) {
                tooltip.add("Local inventory");
            } else {
                PatternSatelliteInfo satellite = getSatelliteInfo(satelliteId);
                tooltip.add("Pattern satellite #" + satelliteId);
                if (satellite != null) {
                    tooltip.add("Dim " + satellite.getDimension() + " at " + satellite.getX() + ", "
                            + satellite.getY() + ", " + satellite.getZ());
                    tooltip.add(satellite.getDistance() >= 0 ? satellite.getDistance() + "m away" : "Other dimension");
                    if (satellite.isFavorite()) {
                        tooltip.add("Stored on memory chip");
                    }
                } else {
                    tooltip.add("Not loaded in this GUI snapshot");
                }
            }
            GuiGraphics.drawToolTip(mouseX, mouseY, tooltip, EnumChatFormatting.WHITE);
            return;
        }
    }

    private PatternSatelliteInfo getSatelliteInfo(int satelliteId) {
        for (PatternSatelliteInfo satellite : satellites) {
            if (satellite.getId() == satelliteId) {
                return satellite;
            }
        }
        return null;
    }

    private String satelliteButtonLabel(int inputSlot) {
        int satelliteId = Pattern.fromStack(patternInventory.getPatternStack()).getSatelliteIdForInputSlot(inputSlot);
        if (satelliteId <= 0) {
            return "-";
        }
        return satelliteId <= 999 ? Integer.toString(satelliteId) : "+";
    }

    public int getInputSize() {
        return 9;
    }

    public int getOutputSize() {
        return 3;
    }
}
