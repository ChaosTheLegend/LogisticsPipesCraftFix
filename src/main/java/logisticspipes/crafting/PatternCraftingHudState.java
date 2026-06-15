package logisticspipes.crafting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.utils.item.ItemIdentifierStack;

public class PatternCraftingHudState {

    private PipeItemsPatternCraftingLogistics.BlockingMode blockingMode;
    private List<PatternInfo> patterns;

    public PatternCraftingHudState() {
        this(PipeItemsPatternCraftingLogistics.BlockingMode.OFF, new ArrayList<>());
    }

    public PatternCraftingHudState(PipeItemsPatternCraftingLogistics.BlockingMode blockingMode) {
        this(blockingMode, new ArrayList<>());
    }

    public PatternCraftingHudState(PipeItemsPatternCraftingLogistics.BlockingMode blockingMode,
            List<PatternInfo> patterns) {
        this.blockingMode = blockingMode == null ? PipeItemsPatternCraftingLogistics.BlockingMode.OFF : blockingMode;
        this.patterns = patterns == null ? new ArrayList<>() : patterns;
    }

    public static PatternCraftingHudState empty() {
        return new PatternCraftingHudState();
    }

    public PipeItemsPatternCraftingLogistics.BlockingMode getBlockingMode() {
        return blockingMode;
    }

    public List<PatternInfo> getPatterns() {
        return patterns;
    }

    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeEnum(blockingMode);
        data.writeList(patterns, (stream, pattern) -> pattern.writeData(stream));
    }

    public static PatternCraftingHudState readData(LPDataInputStream data) throws IOException {
        PipeItemsPatternCraftingLogistics.BlockingMode blockingMode = data
                .readEnum(PipeItemsPatternCraftingLogistics.BlockingMode.class);
        return new PatternCraftingHudState(
                blockingMode,
                data.readList(PatternInfo::readData));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PatternCraftingHudState)) {
            return false;
        }
        PatternCraftingHudState that = (PatternCraftingHudState) o;
        return blockingMode == that.blockingMode && patterns.equals(that.patterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockingMode, patterns);
    }

    public static class PatternInfo {

        private int slot;
        private List<IngredientInfo> ingredients;
        private List<OutputInfo> outputs;
        private String status;
        private boolean active;

        public PatternInfo(int slot) {
            this(slot, new ArrayList<>(), new ArrayList<>(), "", false);
        }

        private PatternInfo(int slot, List<IngredientInfo> ingredients, List<OutputInfo> outputs,
                String status, boolean active) {
            this.slot = slot;
            this.ingredients = ingredients == null ? new ArrayList<>() : ingredients;
            this.outputs = outputs == null ? new ArrayList<>() : outputs;
            this.status = status == null ? "" : status;
            this.active = active;
        }

        public int getSlot() {
            return slot;
        }

        public List<IngredientInfo> getIngredients() {
            return ingredients;
        }

        public List<OutputInfo> getOutputs() {
            return outputs;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status == null ? "" : status;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        private void writeData(LPDataOutputStream data) throws IOException {
            data.writeInt(slot);
            data.writeList(ingredients, (stream, ingredient) -> ingredient.writeData(stream));
            data.writeList(outputs, (stream, output) -> output.writeData(stream));
            data.writeUTF(status);
            data.writeBoolean(active);
        }

        private static PatternInfo readData(LPDataInputStream data) throws IOException {
            return new PatternInfo(
                    data.readInt(),
                    data.readList(IngredientInfo::readData),
                    data.readList(OutputInfo::readData),
                    data.readUTF(),
                    data.readBoolean());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PatternInfo)) {
                return false;
            }
            PatternInfo that = (PatternInfo) o;
            return slot == that.slot
                    && active == that.active
                    && ingredients.equals(that.ingredients)
                    && outputs.equals(that.outputs)
                    && status.equals(that.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(slot, ingredients, outputs, status, active);
        }
    }

    public static class OutputInfo {

        private ItemIdentifierStack stack;
        private int requestedAmount;

        public OutputInfo(ItemIdentifierStack stack, int requestedAmount) {
            this.stack = stack;
            this.requestedAmount = Math.max(0, requestedAmount);
        }

        public ItemIdentifierStack getStack() {
            return stack;
        }

        public int getRequestedAmount() {
            return requestedAmount;
        }

        private void writeData(LPDataOutputStream data) throws IOException {
            data.writeItemIdentifierStack(stack);
            data.writeInt(requestedAmount);
        }

        private static OutputInfo readData(LPDataInputStream data) throws IOException {
            return new OutputInfo(data.readItemIdentifierStack(), data.readInt());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof OutputInfo)) {
                return false;
            }
            OutputInfo that = (OutputInfo) o;
            return requestedAmount == that.requestedAmount && stack.equals(that.stack);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stack, requestedAmount);
        }
    }

    public static class IngredientInfo {

        private ItemIdentifierStack stack;
        private int bufferedAmount;

        public IngredientInfo(ItemIdentifierStack stack, int bufferedAmount) {
            this.stack = stack;
            this.bufferedAmount = Math.max(0, bufferedAmount);
        }

        public ItemIdentifierStack getStack() {
            return stack;
        }

        public int getBufferedAmount() {
            return bufferedAmount;
        }

        private void writeData(LPDataOutputStream data) throws IOException {
            data.writeItemIdentifierStack(stack);
            data.writeInt(bufferedAmount);
        }

        private static IngredientInfo readData(LPDataInputStream data) throws IOException {
            return new IngredientInfo(data.readItemIdentifierStack(), data.readInt());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IngredientInfo)) {
                return false;
            }
            IngredientInfo that = (IngredientInfo) o;
            return bufferedAmount == that.bufferedAmount && stack.equals(that.stack);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stack, bufferedAmount);
        }
    }
}
