package logisticspipes.pipes;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;

public class PipeCraftingChassiMk3 extends PipeCraftingChassi {

    public PipeCraftingChassiMk3(Item item) {
        super(item);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_CRAFTING_CHASSI3_TEXTURE;
    }

    @Override
    public int getChassiSize() {
        return 8;
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "logisticspipes",
            "textures/gui/chassipipe_size8.png");

    @Override
    public ResourceLocation getChassiGUITexture() {
        return PipeCraftingChassiMk3.TEXTURE;
    }
}
