package logisticspipes.crafting;

import com.github.bsideup.jabel.Desugar;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;

@Desugar
public record PatternTargetInformation(int patternSlot) implements IAdditionalTargetInformation {

}
