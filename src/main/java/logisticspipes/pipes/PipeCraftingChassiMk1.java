package logisticspipes.pipes;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;

public class PipeCraftingChassiMk1 extends PipeCraftingChassi {

    public PipeCraftingChassiMk1(Item item) {
        super(item);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_CRAFTING_CHASSI1_TEXTURE;
    }

    @Override
    public int getChassiSize() {
        return 2;
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "logisticspipes",
            "textures/gui/chassipipe_size2.png");

    @Override
    public ResourceLocation getChassiGUITexture() {
        return PipeCraftingChassiMk1.TEXTURE;
    }
}
