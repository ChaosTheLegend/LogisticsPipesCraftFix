package logisticspipes.pipes;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;

public class PipeCraftingChassiMk4 extends PipeCraftingChassi {

    public PipeCraftingChassiMk4(Item item) {
        super(item);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_CRAFTING_CHASSI4_TEXTURE;
    }

    @Override
    public int getChassiSize() {
        return 12;
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "logisticspipes",
            "textures/gui/chassipipe_size8.png");

    @Override
    public ResourceLocation getChassiGUITexture() {
        return PipeCraftingChassiMk4.TEXTURE;
    }
}
