package logisticspipes.gui.hud;

import java.util.List;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

import logisticspipes.interfaces.IHUDConfig;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.ItemStackRenderer;
import logisticspipes.utils.item.ItemStackRenderer.DisplayAmount;

public class HUDPatternCrafting extends BasicHUDGui {

    private static final int COLUMNS = 3;

    private final PipeItemsPatternCraftingLogistics pipe;

    public HUDPatternCrafting(PipeItemsPatternCraftingLogistics pipe) {
        this.pipe = pipe;
    }

    @Override
    public void renderHeadUpDisplay(double d, boolean day, boolean shifted, Minecraft mc, IHUDConfig config) {
        List<ItemIdentifierStack> results = pipe.getHudCraftResults();
        boolean hasResults = !results.isEmpty();
        int resultRows = rowsFor(results.size(), COLUMNS);
        int todoRows = rowsFor(pipe.displayList.size(), COLUMNS);
        boolean hasTodo = !pipe.displayList.isEmpty();
        int bottom = hasTodo ? 12 + (resultRows + todoRows) * 20 : 4 + resultRows * 20;

        if (day) {
            GL11.glColor4b((byte) 64, (byte) 64, (byte) 64, (byte) 64);
        } else {
            GL11.glColor4b((byte) 127, (byte) 127, (byte) 127, (byte) 64);
        }
        GuiGraphics.drawGuiBackGround(mc, -50, -28, 50, bottom, 0, false);
        if (day) {
            GL11.glColor4b((byte) 64, (byte) 64, (byte) 64, (byte) 127);
        } else {
            GL11.glColor4b((byte) 127, (byte) 127, (byte) 127, (byte) 127);
        }

        GL11.glTranslatef(0.0F, 0.0F, -0.005F);
        GL11.glScalef(1.5F, 1.5F, 0.0001F);
        if (hasResults) {
            mc.fontRenderer.drawString("Result:", -28, -10, 0);
        }
        if (hasTodo) {
            mc.fontRenderer.drawString("Todo:", -28, hasResults ? -10 + resultRows * 14 + 14 : -10, 0);
        }
        GL11.glScalef(0.8F, 0.8F, -1F);

        if (hasResults) {
            ItemStackRenderer.renderItemIdentifierStackListIntoGui(
                    results,
                    null,
                    0,
                    -26,
                    -1,
                    COLUMNS,
                    Math.max(1, results.size()),
                    18,
                    18,
                    100.0F,
                    DisplayAmount.ALWAYS,
                    true,
                    false,
                    shifted);
        }
        if (hasTodo) {
            ItemStackRenderer.renderItemIdentifierStackListIntoGui(
                    pipe.displayList,
                    null,
                    0,
                    -26,
                    hasResults ? -1 + resultRows * 20 + 20 : -1,
                    COLUMNS,
                    Math.max(1, pipe.displayList.size()),
                    18,
                    18,
                    100.0F,
                    DisplayAmount.ALWAYS,
                    true,
                    false,
                    shifted);
        }
    }

    @Override
    public boolean display(IHUDConfig config) {
        return config.isHUDCrafting() && (!pipe.getHudCraftResults().isEmpty() || !pipe.displayList.isEmpty());
    }

    @Override
    public boolean cursorOnWindow(int x, int y) {
        int resultRows = rowsFor(pipe.getHudCraftResults().size(), COLUMNS);
        int todoRows = rowsFor(pipe.displayList.size(), COLUMNS);
        boolean hasTodo = !pipe.displayList.isEmpty();
        int bottom = hasTodo ? 12 + (resultRows + todoRows) * 20 : 4 + resultRows * 20;
        return -50 < x && x < 50 && -28 < y && y < bottom;
    }

    private int rowsFor(int size, int columns) {
        if (size <= 0) {
            return 0;
        }
        return (size + columns - 1) / columns;
    }
}
