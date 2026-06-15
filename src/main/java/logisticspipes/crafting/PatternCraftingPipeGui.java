package logisticspipes.crafting;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.gui.PatternCraftingPipeCancel;
import logisticspipes.network.packets.gui.PatternCraftingPipeMode;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.proxy.MainProxy;
import logisticspipes.renderer.PatternItemRenderer;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SmallGuiButton;

public class PatternCraftingPipeGui extends LogisticsBaseGuiScreen {

    private static final int MODE_BUTTON = 0;
    private static final int CANCEL_BUTTON_BASE = 10;

    private final PipeItemsPatternCraftingLogistics pipe;

    public PatternCraftingPipeGui(EntityPlayer player, PipeItemsPatternCraftingLogistics pipe) {
        super(176, 166, 0, 0);
        this.pipe = pipe;
        DummyContainer dummy = new DummyContainer(player.inventory, pipe.getPatternModule().getPatternInventory());
        PatternCraftingPipeGuiProvider.addPatternSlots(dummy, pipe);
        dummy.addNormalSlotsForPlayerInventory(8, 84);
        inventorySlots = dummy;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        GuiButton modeButton = new SmallGuiButton(MODE_BUTTON, guiLeft + 8, guiTop + 66, 70, 12, modeLabel());
        modeButton.enabled = !pipe.isBlockingModeFixed();
        buttonList.add(modeButton);
        for (int slot = 0; slot < 9; slot++) {
            buttonList.add(new SmallGuiButton(
                    CANCEL_BUTTON_BASE + slot,
                    guiLeft + 8 + slot * 18,
                    guiTop + 50,
                    16,
                    10,
                    "x"));
        }
        updateCancelButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == MODE_BUTTON && !pipe.isBlockingModeFixed()) {
            PipeItemsPatternCraftingLogistics.BlockingMode[] values = PipeItemsPatternCraftingLogistics.BlockingMode
                    .values();
            PipeItemsPatternCraftingLogistics.BlockingMode next = values[(pipe.getBlockingMode().ordinal() + 1)
                    % values.length];
            pipe.setBlockingMode(next);
            button.displayString = modeLabel();
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(PatternCraftingPipeMode.class).setMode(next.ordinal())
                            .setTilePos(pipe.container));
        } else if (button.id >= CANCEL_BUTTON_BASE && button.id < CANCEL_BUTTON_BASE + 9) {
            int slot = button.id - CANCEL_BUTTON_BASE;
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(PatternCraftingPipeCancel.class).setInteger(slot)
                            .setTilePos(pipe.container));
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        updateCancelButtons();
        GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
        GuiGraphics.drawPlayerInventoryBackground(mc, guiLeft + 8, guiTop + 84);
        for (int i = 0; i < 9; i++) {
            GuiGraphics.drawSlotBackground(mc, guiLeft + 7 + i * 18, guiTop + 27);
        }
        mc.fontRenderer.drawString("Pattern Crafting Pipe", guiLeft + 8, guiTop + 6, 0x404040);
    }

    @Override
    protected void func_146977_a(Slot slot) {
        boolean renderPatternResult = slot != null && slot.inventory == pipe.getPatternModule().getPatternInventory();
        PatternItemRenderer.setForceResultRender(renderPatternResult);
        try {
            super.func_146977_a(slot);
        } finally {
            PatternItemRenderer.clearForceResultRender();
        }
    }

    private String modeLabel() {
        switch (pipe.getBlockingMode()) {
            case BLOCKING:
                return "Blocking";
            case SMART:
                return "Smart";
            case OFF:
            default:
                return "No Block";
        }
    }

    private void updateCancelButtons() {
        for (Object buttonObject : buttonList) {
            if (!(buttonObject instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) buttonObject;
            if (button.id < CANCEL_BUTTON_BASE || button.id >= CANCEL_BUTTON_BASE + 9) {
                continue;
            }
            int slot = button.id - CANCEL_BUTTON_BASE;
            button.enabled = pipe.getPatternModule().getPatternStack(slot) != null;
        }
    }
}
