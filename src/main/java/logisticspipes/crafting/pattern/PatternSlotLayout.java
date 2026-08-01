package logisticspipes.crafting.pattern;

/**
 * Shared slot placement for editable pattern inventories.
 * <p>
 * Crafting patterns keep the vanilla-like 3x3 input shape and three horizontal outputs. Processing patterns use a 4x4
 * input matrix and arrange their four outputs as a compact 2x2 block.
 */
public class PatternSlotLayout {

    public static final int SLOT_SIZE = 18;

    private final int inputLeft;
    private final int inputTop;
    private final int outputLeft;
    private final int outputTop;
    private final int inputColumns;
    private final int outputColumns;

    public PatternSlotLayout(AbstractPattern pattern, int inputLeft, int inputTop, int outputLeft, int outputTop) {
        this(
                pattern instanceof ProcessingPattern ? 4 : 3,
                pattern != null && pattern.getResultSlotCount() > DefaultPattern.RESULT_SLOTS ? 2 : 3,
                inputLeft,
                inputTop,
                outputLeft,
                outputTop);
    }

    private PatternSlotLayout(int inputColumns, int outputColumns, int inputLeft, int inputTop, int outputLeft,
            int outputTop) {
        this.inputColumns = Math.max(1, inputColumns);
        this.outputColumns = Math.max(1, outputColumns);
        this.inputLeft = inputLeft;
        this.inputTop = inputTop;
        this.outputLeft = outputLeft;
        this.outputTop = outputTop;
    }

    public int inputX(int inputSlot) {
        return inputLeft + inputSlot % inputColumns * SLOT_SIZE;
    }

    public int inputY(int inputSlot) {
        return inputTop + inputSlot / inputColumns * SLOT_SIZE;
    }

    public int outputX(int outputIndex) {
        return outputLeft + outputIndex % outputColumns * SLOT_SIZE;
    }

    public int outputY(int outputIndex) {
        return outputTop + outputIndex / outputColumns * SLOT_SIZE;
    }
}
