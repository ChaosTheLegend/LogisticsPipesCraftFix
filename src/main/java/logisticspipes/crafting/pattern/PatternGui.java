package logisticspipes.crafting.pattern;

import logisticspipes.crafting.PatternSatelliteInfo;
import logisticspipes.crafting.PatternSatelliteSelectorGui;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.PatternSatelliteAssignmentPacket;
import logisticspipes.network.packets.gui.PatternSlotActionPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SmallGuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PatternGui extends LogisticsBaseGuiScreen {

    private final PatternInventory patternInventory;
    private final List<PatternSatelliteInfo> satellites;
    public static final int CLEAR_BUTTON_ID = 0;
    public static final int MULTIPLE_BUTTON_ID = 1;
    public static final int ORE_DICT_BUTTON_ID = 2;
    private static final int INGREDIENT_LEFT = 25;
    private static final int INGREDIENT_TOP = 16;
    private static final int OUTPUT_LEFT = 115;
    private static final int OUTPUT_TOP = 34;
    private static final int SLOT_SIZE = 18;
    private static final int SATELLITE_ICON_SIZE = 7;

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
        GuiButton clearButton = new SmallGuiButton(CLEAR_BUTTON_ID, guiLeft + 100, guiTop + 18, 46, 12, "Clear");
        addButton(clearButton);

        GuiButton multiplierButton = new SmallGuiButton(MULTIPLE_BUTTON_ID, guiLeft + 100, guiTop + 34, 46, 12, "2x");
        addButton(multiplierButton);

    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
        GuiGraphics.drawPlayerInventoryBackground(mc, guiLeft + 8, guiTop + 92);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                GuiGraphics.drawSlotBackground(
                    mc,
                    guiLeft + INGREDIENT_LEFT + x * SLOT_SIZE,
                        guiTop + INGREDIENT_TOP + y * SLOT_SIZE);
            }
        }
        for (int i = 0; i < ItemPattern.RESULT_SLOTS; i++) {
            GuiGraphics.drawSlotBackground(mc, guiLeft + OUTPUT_LEFT + i * SLOT_SIZE, guiTop + OUTPUT_TOP);
        }
        drawSatelliteIcons();
        mc.fontRenderer.drawString("Pattern", guiLeft + 8, guiTop + 6, 0x404040);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (!hasSubGui()) {
            drawSatelliteButtonTooltip(mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && !hasSubGui()) {
            int inputSlot = getSatelliteHotspotSlot(mouseX, mouseY);
            if (inputSlot >= 0) {
                openSatelliteSelector(inputSlot);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case CLEAR_BUTTON_ID:
                ItemPattern.fromStack(patternInventory.getPatternStack()).clear();
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(PatternSlotActionPacket.class)
                                .setInventorySlot(patternInventory.getInventorySlot())
                                .setAction(PatternSlotActionPacket.Action.CLEAR.ordinal()));
                break;
            case MULTIPLE_BUTTON_ID:
                ItemPattern.fromStack(patternInventory.getPatternStack()).multiply(2);
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(PatternSlotActionPacket.class)
                                .setInventorySlot(patternInventory.getInventorySlot())
                                .setAction(PatternSlotActionPacket.Action.MULTIPLY_TWO.ordinal()));
                break;

        }
    }

    private void openSatelliteSelector(int inputSlot) {
        int currentSatelliteId = ItemPattern.fromStack(patternInventory.getPatternStack())
                .getSatelliteIdForInputSlot(inputSlot);
        setSubGui(
                new PatternSatelliteSelectorGui(
                        inputSlot,
                        currentSatelliteId,
                        satellites,
                    (satelliteId,
                     satelliteUuid) -> setSatelliteForInputSlot(inputSlot, satelliteId, satelliteUuid)));
    }

    private void setSatelliteForInputSlot(int inputSlot, int satelliteId, String satelliteUuid) {
        ItemPattern.fromStack(patternInventory.getPatternStack())
                .setSatelliteTargetForInputSlot(inputSlot, satelliteId, satelliteUuid);
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(PatternSatelliteAssignmentPacket.class)
                        .setInventorySlot(patternInventory.getInventorySlot()).setInputSlot(inputSlot)
                    .setSatelliteId(satelliteId).setSatelliteUuid(satelliteUuid));
    }

    private void drawSatelliteButtonTooltip(int mouseX, int mouseY) {
        int inputSlot = getSatelliteHotspotSlot(mouseX, mouseY);
        if (inputSlot < 0) {
            return;
        }
        int satelliteId = ItemPattern.fromStack(patternInventory.getPatternStack())
            .getSatelliteIdForInputSlot(inputSlot);
        String satelliteUuid = ItemPattern.fromStack(patternInventory.getPatternStack())
                .getSatelliteUuidForInputSlot(inputSlot);
        List<String> tooltip = new ArrayList<>();
        if (satelliteId <= 0 && satelliteUuid.isEmpty()) {
            tooltip.add("Local inventory");
        } else {
            PatternSatelliteInfo satellite = getSatelliteInfo(satelliteId, satelliteUuid);
            tooltip.add("Pattern satellite " + (satellite == null ? "#" + satelliteId : satellite.displayName()));
            if (satellite != null) {
                tooltip.add(
                    "Dim " + satellite
                        .dimension() + " at " + satellite.x() + ", " + satellite.y() + ", " + satellite.z());
                tooltip.add(satellite.distance() >= 0 ? satellite.distance() + "m away" : "Other dimension");
                if (satellite.favorite()) {
                    tooltip.add("Stored on memory chip");
                }
            } else {
                tooltip.add("Not loaded in this GUI snapshot");
            }
        }
        GuiGraphics.drawToolTip(mouseX, mouseY, tooltip, EnumChatFormatting.WHITE);
    }

    private PatternSatelliteInfo getSatelliteInfo(int satelliteId, String satelliteUuid) {
        for (PatternSatelliteInfo satellite : satellites) {
            if ((!satelliteUuid.isEmpty() && satelliteUuid.equals(satellite.uuid()))
                    || (satelliteUuid.isEmpty() && satellite.id() == satelliteId)) {
                return satellite;
            }
        }
        return null;
    }

    private void drawSatelliteIcons() {
        for (int inputSlot = 0; inputSlot < getInputSize(); inputSlot++) {
            int satelliteId = ItemPattern.fromStack(patternInventory.getPatternStack())
                .getSatelliteIdForInputSlot(inputSlot);
            String satelliteUuid = ItemPattern.fromStack(patternInventory.getPatternStack())
                    .getSatelliteUuidForInputSlot(inputSlot);
            int x = guiLeft + INGREDIENT_LEFT + (inputSlot % 3) * SLOT_SIZE;
            int y = guiTop + INGREDIENT_TOP + (inputSlot / 3) * SLOT_SIZE;
            boolean assigned = satelliteId > 0 || !satelliteUuid.isEmpty();
            Gui.drawRect(x, y, x + SATELLITE_ICON_SIZE, y + SATELLITE_ICON_SIZE, assigned ? 0xff2b6ee8 : 0xff777777);
            mc.fontRenderer.drawString(assigned ? "S" : "+", x + 1, y, 0xffffff);
        }
    }

    private int getSatelliteHotspotSlot(int mouseX, int mouseY) {
        for (int inputSlot = 0; inputSlot < getInputSize(); inputSlot++) {
            int x = guiLeft + INGREDIENT_LEFT + (inputSlot % 3) * SLOT_SIZE;
            int y = guiTop + INGREDIENT_TOP + (inputSlot / 3) * SLOT_SIZE;
            if (mouseX >= x && mouseX < x + SATELLITE_ICON_SIZE && mouseY >= y && mouseY < y + SATELLITE_ICON_SIZE) {
                return inputSlot;
            }
        }
        return -1;
    }

    public int getInputSize() {
        return 9;
    }

}
