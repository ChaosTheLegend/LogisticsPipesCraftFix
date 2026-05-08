package logisticspipes.crafting;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;

public class PatternGui extends LogisticsBaseGuiScreen {

    private final PatternInventory patternInventory;

    public PatternGui(EntityPlayer player, IInventory inventory) {
        super(176, 168, 0, 0);
        patternInventory = (PatternInventory) inventory;
        DummyContainer dummy = new DummyContainer(player.inventory, inventory);
        PatternGuiProvider.addPatternSlots(dummy);
        dummy.addNormalSlotsForPlayerInventory(8, 86);
        inventorySlots = dummy;
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
}
