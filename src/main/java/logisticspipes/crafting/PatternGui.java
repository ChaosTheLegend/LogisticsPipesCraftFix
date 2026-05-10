package logisticspipes.crafting;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.gui.PatternSlotActionPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;

public class PatternGui extends LogisticsBaseGuiScreen {

    private final PatternInventory patternInventory;
    public static final int CLEAR_BUTTON_ID = 0;
    public static final int MULTIPLE_BUTTON_ID = 1;
    public static final int ORE_DICT_BUTTON_ID = 2;

    public PatternGui(EntityPlayer player, IInventory inventory) {
        super(176, 168, 0, 0);
        patternInventory = (PatternInventory) inventory;
        PatternContainer dummy = new PatternContainer(player.inventory, inventory);
        PatternGuiProvider.addPatternSlots(dummy);
        dummy.addNormalSlotsForPlayerInventory(8, 86);

        addActionButtons();

        inventorySlots = dummy;
    }

    private void addActionButtons() {
        GuiButton clearButton = new GuiButton(CLEAR_BUTTON_ID, guiLeft + 50, guiTop + 50, 5, 5, "X");
        addButton(clearButton);

        GuiButton multiplierButton = new GuiButton(MULTIPLE_BUTTON_ID, guiLeft + 55, guiTop + 20, 5, 5, "2x");
        addButton(multiplierButton);

        GuiButton oreDictButton = new GuiButton(ORE_DICT_BUTTON_ID, guiLeft + 55, guiTop + 60, 5, 5, "d");
        addButton(oreDictButton);

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
                Pattern.clear(patternInventory.getPattern());
                MainProxy.sendPacketToServer(PacketHandler.getPacket(PatternSlotActionPacket.class)
                        .setInventorySlot(patternInventory.getInventorySlot())
                        .setAction(PatternSlotActionPacket.Action.CLEAR.ordinal()));
                break;
            case MULTIPLE_BUTTON_ID:
                Pattern.multiply(patternInventory.getPattern(), 2);
                MainProxy.sendPacketToServer(PacketHandler.getPacket(PatternSlotActionPacket.class)
                        .setInventorySlot(patternInventory.getInventorySlot())
                        .setAction(PatternSlotActionPacket.Action.MULTIPLY_TWO.ordinal()));
                break;

        }
    }

    public int getInputSize() {
        return 9;
    }

    public int getOutputSize() {
        return 3;
    }
}
