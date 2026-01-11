package logisticspipes.pipes;

import net.minecraft.item.Item;

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
    public int getChassisTier() {
        return 4;
    }
}
