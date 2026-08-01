package logisticspipes.crafting;

import com.github.bsideup.jabel.Desugar;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;

@Desugar
public record PatternTargetInformation(int patternSlot, int inputSlot) implements IAdditionalTargetInformation {

    public static final int NO_INPUT_SLOT = -1;

    public PatternTargetInformation(int patternSlot) {
        this(patternSlot, NO_INPUT_SLOT);
    }
}
