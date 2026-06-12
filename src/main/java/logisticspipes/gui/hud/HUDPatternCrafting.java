package logisticspipes.gui.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import logisticspipes.crafting.PatternCraftingHudState;
import logisticspipes.crafting.PatternCraftingHudState.IngredientInfo;
import logisticspipes.crafting.PatternCraftingHudState.PatternInfo;
import logisticspipes.interfaces.IHUDConfig;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.SimpleGraphics;
import logisticspipes.utils.gui.hud.BasicHUDButton;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.ItemStackRenderer;
import logisticspipes.utils.item.ItemStackRenderer.DisplayAmount;
import logisticspipes.utils.string.StringUtils;

public class HUDPatternCrafting extends BasicHUDGui {

    private static final int WINDOW_LEFT = -78;
    private static final int WINDOW_TOP = -60;
    private static final int WINDOW_RIGHT = 78;
    private static final int WINDOW_BOTTOM = 70;
    private static final int INGREDIENT_LEFT = -61;
    private static final int INGREDIENT_TOP = -28;
    private static final int OUTPUT_LEFT = 25;
    private static final int OUTPUT_TOP = -10;
    private static final int SLOT_SIZE = 18;
    private static final int BUFFER_BLUE = 0xff55aaff;
    private static final int TEXT_COLOR = 0;
    private static final int MAX_STATUS_LINES = 2;

    private final PipeItemsPatternCraftingLogistics pipe;
    private int page = 0;

    public HUDPatternCrafting(PipeItemsPatternCraftingLogistics pipe) {
        this.pipe = pipe;
        addButton(new BasicHUDButton("<", WINDOW_LEFT + 4, WINDOW_TOP + 4, 8, 8) {

            @Override
            public void clicked() {
                if (page > 0) {
                    page--;
                }
            }

            @Override
            public boolean shouldRenderButton() {
                return getMaxPage() > 1;
            }

            @Override
            public boolean buttonEnabled() {
                return page > 0;
            }
        });
        addButton(new BasicHUDButton(">", WINDOW_RIGHT - 12, WINDOW_TOP + 4, 8, 8) {

            @Override
            public void clicked() {
                if (page + 1 < getMaxPage()) {
                    page++;
                }
            }

            @Override
            public boolean shouldRenderButton() {
                return getMaxPage() > 1;
            }

            @Override
            public boolean buttonEnabled() {
                return page + 1 < getMaxPage();
            }
        });
    }

    @Override
    public void renderHeadUpDisplay(double d, boolean day, boolean shifted, Minecraft mc, IHUDConfig config) {
        PatternCraftingHudState state = pipe.getHudState();
        List<PatternInfo> patterns = state.getPatterns();
        normalizePage(patterns.size());

        if (day) {
            GL11.glColor4b((byte) 64, (byte) 64, (byte) 64, (byte) 64);
        } else {
            GL11.glColor4b((byte) 127, (byte) 127, (byte) 127, (byte) 64);
        }
        GuiGraphics.drawGuiBackGround(mc, WINDOW_LEFT, WINDOW_TOP, WINDOW_RIGHT, WINDOW_BOTTOM, 0, false);
        if (day) {
            GL11.glColor4b((byte) 64, (byte) 64, (byte) 64, (byte) 127);
        } else {
            GL11.glColor4b((byte) 127, (byte) 127, (byte) 127, (byte) 127);
        }

        GL11.glTranslatef(0.0F, 0.0F, -0.01F);
        super.renderHeadUpDisplay(d, day, shifted, mc, config);
        GL11.glTranslatef(0.0F, 0.0F, -0.005F);
        var topString = "Mode: " + formatMode(state.getBlockingMode());

        if (patterns.isEmpty()) {
            topString += "    No Patterns";
        } else {
            PatternInfo pattern = patterns.get(page);
            topString += " - Pattern " + (page + 1) + "/" + patterns.size() + (pattern.isActive() ? "  Active" : "");
        }

        mc.fontRenderer.drawString(topString, WINDOW_LEFT + 14, WINDOW_TOP + 6, TEXT_COLOR);

        if (patterns.isEmpty()) return;

        mc.fontRenderer.drawString("In", INGREDIENT_LEFT, WINDOW_TOP + 28, TEXT_COLOR);
        mc.fontRenderer.drawString("Out", OUTPUT_LEFT, WINDOW_TOP + 28, TEXT_COLOR);
        mc.fontRenderer.drawString("->", 3, -4, TEXT_COLOR);

        PatternInfo pattern = patterns.get(page);
        ItemStackRenderer renderer = new ItemStackRenderer(0, 0, 100.0F, true, false, true);
        renderer.setDisplayAmount(DisplayAmount.HIDE_ONE);
        renderIngredients(mc, renderer, pattern.getIngredients());
        renderOutputs(renderer, pattern.getOutputs());
        drawWrappedStatus(mc.fontRenderer, pattern.getStatus(), WINDOW_LEFT + 8, 42, WINDOW_RIGHT - WINDOW_LEFT - 16);
    }

    @Override
    public boolean display(IHUDConfig config) {
        return config.isHUDCrafting() && !pipe.getHudState().getPatterns().isEmpty();
    }

    @Override
    public boolean cursorOnWindow(int x, int y) {
        return WINDOW_LEFT < x && x < WINDOW_RIGHT && WINDOW_TOP < y && y < WINDOW_BOTTOM;
    }

    private void renderIngredients(Minecraft mc, ItemStackRenderer renderer, List<IngredientInfo> ingredients) {
        for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
            IngredientInfo ingredient = ingredients.get(i);
            int x = INGREDIENT_LEFT + (i % 3) * SLOT_SIZE;
            int y = INGREDIENT_TOP + (i / 3) * SLOT_SIZE;
            renderStack(renderer, ingredient.getStack(), x, y);
            if (ingredient.getBufferedAmount() > 0) {
                drawBufferedAmount(mc, ingredient.getBufferedAmount(), x, y);
            }
        }
    }

    private void renderOutputs(ItemStackRenderer renderer, List<ItemIdentifierStack> outputs) {
        for (int i = 0; i < Math.min(outputs.size(), 3); i++) {
            renderStack(renderer, outputs.get(i), OUTPUT_LEFT + i * SLOT_SIZE, OUTPUT_TOP);
        }
    }

    private void renderStack(ItemStackRenderer renderer, ItemIdentifierStack stack, int x, int y) {
        if (stack == null) {
            return;
        }
        renderer.setItemIdentifierStack(stack).setPosX(x).setPosY(y);
        renderer.renderInGui();
    }

    private void drawBufferedAmount(Minecraft mc, int amount, int x, int y) {
        String amountString = StringUtils.getFormatedStackSize(amount, true);
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glTranslatef(0.0F, 0.0F, 145.0F);
        GL11.glScalef(0.5F, 0.5F, 1.0F);
        SimpleGraphics.drawStringWithTranslatedShadow(
            mc.fontRenderer,
            amountString,
            (x + 17) * 2 - mc.fontRenderer.getStringWidth(amountString),
            (y - 2) * 2,
            BUFFER_BLUE);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private void drawWrappedStatus(FontRenderer fontRenderer, String status, int x, int y, int width) {
        List<String> lines = wrap(fontRenderer, status == null ? "" : status, width, MAX_STATUS_LINES);
        for (int i = 0; i < lines.size(); i++) {
            fontRenderer.drawString(lines.get(i), x, y + i * 10, TEXT_COLOR);
        }
    }

    private List<String> wrap(FontRenderer fontRenderer, String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String remaining = text.trim();
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            String line = remaining;
            while (fontRenderer.getStringWidth(line) > width && line.contains(" ")) {
                line = line.substring(0, line.lastIndexOf(' '));
            }
            if (fontRenderer.getStringWidth(line) > width) {
                line = trimToWidth(fontRenderer, line, width);
            }
            remaining = remaining.substring(line.length()).trim();
            if (!remaining.isEmpty() && lines.size() + 1 == maxLines) {
                line = trimToWidth(fontRenderer, line + "...", width);
                remaining = "";
            }
            lines.add(line);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private String trimToWidth(FontRenderer fontRenderer, String text, int width) {
        String result = text;
        while (!result.isEmpty() && fontRenderer.getStringWidth(result) > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void drawCenteredString(FontRenderer fontRenderer, String text, int x, int y, int color) {
        fontRenderer.drawString(text, x - fontRenderer.getStringWidth(text) / 2, y, color);
    }

    private String formatMode(PipeItemsPatternCraftingLogistics.BlockingMode mode) {
        switch (mode) {
            case BLOCKING:
                return "Blocking";
            case SMART:
                return "Smart";
            case OFF:
            default:
                return "Off";
        }
    }

    private void normalizePage(int size) {
        if (size <= 0) {
            page = 0;
        } else if (page >= size) {
            page = size - 1;
        }
    }

    private int getMaxPage() {
        return Math.max(1, pipe.getHudState().getPatterns().size());
    }
}
