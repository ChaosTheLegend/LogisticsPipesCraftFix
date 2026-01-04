package logisticspipes.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.DummyContainer;

public class GuiDummy extends LogisticsBaseGuiScreen {

    private final ResourceLocation _texture;

    public GuiDummy(EntityPlayer player, ResourceLocation texture, int xSize, int ySize) {
        super(new DummyContainer(player.inventory, null), xSize, ySize, 0, 0);
        _texture = texture;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.renderEngine.bindTexture(_texture);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        // Nothing here
    }
}
