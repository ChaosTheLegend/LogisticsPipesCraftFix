package logisticspipes.crafting;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;

public class PatternGui extends LogisticsBaseGuiScreen {

    private final PatternInventory patternInventory;
    public static final int CLEAR_BUTTON_ID = 0;
    public static final int MULTIPLE_BUTTON_ID = 1;
    public static final int ORE_DICT_BUTTON_ID = 2;

    public PatternGui(EntityPlayer player, IInventory inventory) {
        super(176, 204, 0, 0);
        patternInventory = (PatternInventory) inventory;
        PatternContainer dummy = new PatternContainer(player.inventory, inventory);
        PatternGuiProvider.addPatternSlots(dummy);
        dummy.addNormalSlotsForPlayerInventory(8, 122);

        addActionButtons();

        inventorySlots = dummy;
    }

    private void addActionButtons() {
        var clearButton = new GuiButton(CLEAR_BUTTON_ID, guiLeft + 50, guiTop + 50, 5, 5, "X");
        addButton(clearButton);

        var multiplierButton = new GuiButton(MULTIPLE_BUTTON_ID,guiLeft + 55, guiTop + 20,5,5, "2x");
        addButton(multiplierButton);

        var oreDictButton = new GuiButton(ORE_DICT_BUTTON_ID,guiLeft + 55, guiTop + 60,5,5, "d");
        addButton(oreDictButton);

    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
        GuiGraphics.drawPlayerInventoryBackground(mc, guiLeft + 8, guiTop + 122);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                GuiGraphics.drawSlotBackground(mc, guiLeft + 25 + x * 18, guiTop + 16 + y * 18);
            }
        }
        for (int i = 0; i < Pattern.RESULT_SLOTS; i++) {
            GuiGraphics.drawSlotBackground(mc, guiLeft + 115 + i * 18, guiTop + 34);
        }
        for (int i = 0; i < Pattern.FLUID_INPUT_SLOTS; i++) {
            GuiGraphics.drawSlotBackground(mc, guiLeft + 25 + i * 18, guiTop + 71);
        }
        for (int i = 0; i < Pattern.FLUID_RESULT_SLOTS; i++) {
            GuiGraphics.drawSlotBackground(mc, guiLeft + 115 + i * 18, guiTop + 71);
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
                break;
            case MULTIPLE_BUTTON_ID:
                Pattern.multiply(patternInventory.getPattern(), 2);
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
