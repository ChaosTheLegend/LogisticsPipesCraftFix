package logisticspipes.request.debug;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;

import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestTree;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.LinkedLogisticsOrderList;

public final class CraftingRequestDebugManager {

    private static final int MAX_SNAPSHOTS = 8;
    private static final Deque<RequestSnapshot> SNAPSHOTS = new ArrayDeque<>();

    private CraftingRequestDebugManager() {}

    /**
     * Records a request-tree state immediately, before later staged crafting ticks mutate related order state.
     */
    public static void record(String title, RequestTree tree, LinkedLogisticsOrderList orders) {
        RequestSnapshot snapshot = new RequestSnapshot(
                title,
                System.currentTimeMillis(),
                tree == null ? "<no request tree>" : tree.toString(),
                formatOrderList(orders));
        synchronized (SNAPSHOTS) {
            SNAPSHOTS.addFirst(snapshot);
            while (SNAPSHOTS.size() > MAX_SNAPSHOTS) {
                SNAPSHOTS.removeLast();
            }
        }
    }

    /**
     * Builds the text that is shown in the client-side JFrame.
     */
    public static String buildSnapshot() {
        StringBuilder out = new StringBuilder();
        out.append("Crafting Request Debug Snapshot\n");
        out.append("Generated: ").append(formatTime(System.currentTimeMillis())).append("\n\n");
        appendRecordedRequests(out);
        appendActivePatternPipes(out);
        return out.toString();
    }

    private static void appendRecordedRequests(StringBuilder out) {
        List<RequestSnapshot> snapshots;
        synchronized (SNAPSHOTS) {
            snapshots = new ArrayList<>(SNAPSHOTS);
        }
        out.append("== Recorded Request Trees ==\n");
        if (snapshots.isEmpty()) {
            out.append("No request tree has been recorded yet.\n\n");
            return;
        }
        for (int i = 0; i < snapshots.size(); i++) {
            RequestSnapshot snapshot = snapshots.get(i);
            out.append("-- Snapshot ").append(i).append(" (").append(i == 0 ? "newest" : "history").append(") --\n");
            out.append("Title: ").append(snapshot.title).append("\n");
            out.append("Captured: ").append(formatTime(snapshot.time)).append("\n\n");
            out.append("Request tree:\n");
            out.append(snapshot.treeText).append("\n");
            out.append("Orders created from tree:\n");
            out.append(snapshot.ordersText).append("\n");
        }
    }

    private static void appendActivePatternPipes(StringBuilder out) {
        out.append("== Active Pattern Crafting Pipes ==\n");
        if (SimpleServiceLocator.routerManager == null) {
            out.append("Router manager is not available.\n");
            return;
        }
        int count = 0;
        for (IRouter router : SimpleServiceLocator.routerManager.getRouters()) {
            if (router == null || !router.isValidCache()) {
                continue;
            }
            CoreRoutedPipe pipe = router.getCachedPipe();
            if (!(pipe instanceof PipeItemsPatternCraftingLogistics)) {
                continue;
            }
            count++;
            try {
                ((PipeItemsPatternCraftingLogistics) pipe).getPatternModule().appendDebugState(out);
            } catch (RuntimeException e) {
                out.append("Pattern crafting pipe at router ")
                        .append(router.getSimpleID())
                        .append(" could not be dumped: ")
                        .append(e.getClass().getName())
                        .append(": ")
                        .append(e.getMessage())
                        .append("\n");
            }
            out.append("\n");
        }
        if (count == 0) {
            out.append("No pattern crafting pipes are currently registered.\n");
        }
    }

    private static String formatOrderList(LinkedLogisticsOrderList orders) {
        if (orders == null) {
            return "<no order list>\n";
        }
        StringBuilder out = new StringBuilder();
        appendOrderList(out, orders, "", "root");
        return out.toString();
    }

    private static void appendOrderList(StringBuilder out, LinkedLogisticsOrderList orders, String prefix, String label) {
        out.append(prefix)
                .append(label)
                .append(" orders=")
                .append(orders.size())
                .append(" subtrees=")
                .append(orders.getSubOrders().size())
                .append(" rootSize=")
                .append(orders.getTreeRootSize())
                .append("\n");
        for (IOrderInfoProvider order : orders) {
            appendOrder(out, order, prefix + "  ");
        }
        for (int i = 0; i < orders.getSubOrders().size(); i++) {
            appendOrderList(out, orders.getSubOrders().get(i), prefix + "  ", "subtree " + i);
        }
    }

    private static void appendOrder(StringBuilder out, IOrderInfoProvider order, String prefix) {
        if (order == null) {
            out.append(prefix).append("- <null order>\n");
            return;
        }
        out.append(prefix)
                .append("- ")
                .append(order.getType())
                .append(" ")
                .append(order.getAsDisplayItem())
                .append(" -> router ")
                .append(order.getRouterId());
        if (order.isInProgress()) {
            out.append(" in-progress");
        }
        if (order.isFinished()) {
            out.append(" finished");
        }
        out.append("\n");
    }

    private static String formatTime(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(time));
    }

    private static class RequestSnapshot {

        private final String title;
        private final long time;
        private final String treeText;
        private final String ordersText;

        private RequestSnapshot(String title, long time, String treeText, String ordersText) {
            this.title = title;
            this.time = time;
            this.treeText = treeText;
            this.ordersText = ordersText;
        }
    }
}
