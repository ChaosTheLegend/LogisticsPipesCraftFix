package logisticspipes.crafting;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;

public class PatternTargetInformation implements IAdditionalTargetInformation {

    private final int patternSlot;

    public PatternTargetInformation(int patternSlot) {
        this.patternSlot = patternSlot;
    }

    public int getPatternSlot() {
        return patternSlot;
    }
}
