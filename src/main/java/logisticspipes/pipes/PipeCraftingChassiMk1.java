package logisticspipes.pipes;

import net.minecraft.item.Item;

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
    public int getChassisTier() {
        return 1;
    }
}
