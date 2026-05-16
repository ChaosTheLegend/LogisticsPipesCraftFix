package logisticspipes.crafting;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.PatternSatelliteAssignmentPacket;
import logisticspipes.network.packets.gui.PatternSlotActionPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;

public class PatternGui extends LogisticsBaseGuiScreen {

    private final PatternInventory patternInventory;
    public static final int CLEAR_BUTTON_ID = 0;
    public static final int MULTIPLE_BUTTON_ID = 1;
    public static final int ORE_DICT_BUTTON_ID = 2;
    private static final int SATELLITE_BUTTON_OFFSET = 100;

    public PatternGui(EntityPlayer player, IInventory inventory) {
        super(176, 168, 0, 0);
        patternInventory = (PatternInventory) inventory;
        PatternContainer dummy = new PatternContainer(player.inventory, inventory);
        PatternGuiProvider.addPatternSlots(dummy);
        dummy.addNormalSlotsForPlayerInventory(8, 86);
        inventorySlots = dummy;
    }

    @Override
    public void initGui() {
        super.initGui();
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
            GuiButton satelliteButton = new GuiButton(
                    SATELLITE_BUTTON_OFFSET + slot,
                    guiLeft + 25 + x * 18,
                    guiTop + 70 + y * 6,
                    16,
                    6,
                    satelliteButtonLabel(slot));
            addButton(satelliteButton);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
        GuiGraphics.drawPlayerInventoryBackground(mc, guiLeft + 8, guiTop + 86);
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

    public int getInventorySlot() {
        return patternInventory.getInventorySlot();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case CLEAR_BUTTON_ID:
                Pattern.fromStack(patternInventory.getPatternStack()).clear();
                MainProxy.sendPacketToServer(PacketHandler.getPacket(PatternSlotActionPacket.class)
                        .setInventorySlot(patternInventory.getInventorySlot())
                        .setAction(PatternSlotActionPacket.Action.CLEAR.ordinal()));
                break;
            case MULTIPLE_BUTTON_ID:
                Pattern.fromStack(patternInventory.getPatternStack()).multiply(2);
                MainProxy.sendPacketToServer(PacketHandler.getPacket(PatternSlotActionPacket.class)
                        .setInventorySlot(patternInventory.getInventorySlot())
                        .setAction(PatternSlotActionPacket.Action.MULTIPLY_TWO.ordinal()));
                break;

        }
        if (button.id >= SATELLITE_BUTTON_OFFSET && button.id < SATELLITE_BUTTON_OFFSET + getInputSize()) {
            int inputSlot = button.id - SATELLITE_BUTTON_OFFSET;
            int nextSatelliteId = nextSatelliteId(Pattern.fromStack(patternInventory.getPatternStack())
                    .getSatelliteIdForInputSlot(inputSlot));
            Pattern.fromStack(patternInventory.getPatternStack()).setSatelliteIdForInputSlot(inputSlot, nextSatelliteId);
            button.displayString = satelliteButtonLabel(inputSlot);
            MainProxy.sendPacketToServer(PacketHandler.getPacket(PatternSatelliteAssignmentPacket.class)
                    .setInventorySlot(patternInventory.getInventorySlot())
                    .setInputSlot(inputSlot)
                    .setSatelliteId(nextSatelliteId));
        }
    }

    private int nextSatelliteId(int currentSatelliteId) {
        java.util.List<Integer> knownIds = PipeItemsPatternSatelliteLogistics.getKnownSatelliteIds();
        if (knownIds.isEmpty()) {
            return currentSatelliteId >= 64 ? 0 : currentSatelliteId + 1;
        }
        if (currentSatelliteId == 0) {
            return knownIds.get(0);
        }
        int index = knownIds.indexOf(currentSatelliteId);
        return index < 0 || index + 1 >= knownIds.size() ? 0 : knownIds.get(index + 1);
    }

    private String satelliteButtonLabel(int inputSlot) {
        int satelliteId = Pattern.fromStack(patternInventory.getPatternStack()).getSatelliteIdForInputSlot(inputSlot);
        return satelliteId > 0 ? "S" + satelliteId : "-";
    }

    public int getInputSize() {
        return 9;
    }

    public int getOutputSize() {
        return 3;
    }
}
