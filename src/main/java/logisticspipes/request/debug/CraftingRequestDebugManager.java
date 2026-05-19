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

    private static final int MAX_SNAPSHOTS = 24;
    private static final int MAX_EVENTS = 600;
    private static final Deque<RequestSnapshot> SNAPSHOTS = new ArrayDeque<>();
    private static final Deque<DebugEvent> EVENTS = new ArrayDeque<>();
    private static int nextRequestId = 1;
    private static int nextEventId = 1;

    private CraftingRequestDebugManager() {}

    /**
     * Records a request-tree state immediately, before later staged crafting ticks mutate related order state.
     */
    public static void record(String title, RequestTree tree, LinkedLogisticsOrderList orders) {
        int requestId;
        synchronized (SNAPSHOTS) {
            requestId = nextRequestId++;
        }
        RequestSnapshot snapshot = new RequestSnapshot(
                requestId,
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
        recordEvent("REQUEST", "request#" + requestId + " " + title + " " + summarizeOrderList(orders));
    }

    public static void recordEvent(String category, String message) {
        synchronized (EVENTS) {
            DebugEvent event = new DebugEvent(
                    nextEventId++,
                    System.currentTimeMillis(),
                    logisticspipes.proxy.MainProxy.getGlobalTick(),
                    category == null || category.isEmpty() ? "EVENT" : category,
                    message == null ? "" : message);
            EVENTS.addFirst(event);
            while (EVENTS.size() > MAX_EVENTS) {
                EVENTS.removeLast();
            }
        }
    }

    public static void recordPipeEvent(PipeItemsPatternCraftingLogistics pipe, String category, String message) {
        recordEvent(category, describePipe(pipe) + " " + message);
    }

    public static void recordPipeEvent(
            PipeItemsPatternCraftingLogistics pipe,
            String category,
            String message,
            Object... args) {
        recordPipeEvent(pipe, category, safeFormat(message, args));
    }

    /**
     * Builds the text that is shown in the client-side JFrame.
     */
    public static String buildSnapshot() {
        StringBuilder out = new StringBuilder();
        out.append("Crafting Request Debug Snapshot\n");
        out.append("Generated: ").append(formatTime(System.currentTimeMillis())).append("\n\n");
        appendSummary(out);
        appendTimeline(out);
        appendRecordedRequests(out);
        appendActivePatternPipes(out);
        return out.toString();
    }

    private static void appendSummary(StringBuilder out) {
        List<RequestSnapshot> snapshots;
        List<DebugEvent> events;
        synchronized (SNAPSHOTS) {
            snapshots = new ArrayList<>(SNAPSHOTS);
        }
        synchronized (EVENTS) {
            events = new ArrayList<>(EVENTS);
        }
        out.append("== Summary ==\n");
        out.append("Recorded request snapshots: ").append(snapshots.size()).append("/").append(MAX_SNAPSHOTS).append("\n");
        out.append("Timeline events: ").append(events.size()).append("/").append(MAX_EVENTS).append("\n");
        out.append("Active pattern crafting pipes: ").append(countActivePatternPipes()).append("\n");
        if (!events.isEmpty()) {
            DebugEvent newest = events.get(0);
            out.append("Latest event: #")
                    .append(newest.id)
                    .append(" ")
                    .append(newest.category)
                    .append(" ")
                    .append(newest.message)
                    .append("\n");
        }
        out.append("\n");
    }

    private static void appendTimeline(StringBuilder out) {
        List<DebugEvent> events;
        synchronized (EVENTS) {
            events = new ArrayList<>(EVENTS);
        }
        out.append("== Timeline ==\n");
        if (events.isEmpty()) {
            out.append("No crafting flow events have been recorded yet.\n\n");
            return;
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            DebugEvent event = events.get(i);
            out.append("#")
                    .append(event.id)
                    .append(" tick=")
                    .append(event.tick)
                    .append(" ")
                    .append(formatTime(event.time))
                    .append(" [")
                    .append(event.category)
                    .append("] ")
                    .append(event.message)
                    .append("\n");
        }
        out.append("\n");
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
            out.append("-- Request #")
                    .append(snapshot.id)
                    .append(" / snapshot ")
                    .append(i)
                    .append(" (")
                    .append(i == 0 ? "newest" : "history")
                    .append(") --\n");
            out.append("Title: ").append(snapshot.title).append("\n");
            out.append("Captured: ").append(formatTime(snapshot.time)).append("\n\n");
            out.append("Request tree:\n");
            out.append(snapshot.treeText).append("\n");
            out.append("Orders created from tree:\n");
            out.append(snapshot.ordersText).append("\n");
        }
    }

    private static int countActivePatternPipes() {
        if (SimpleServiceLocator.routerManager == null) {
            return 0;
        }
        int count = 0;
        for (IRouter router : SimpleServiceLocator.routerManager.getRouters()) {
            if (router == null || !router.isValidCache()) {
                continue;
            }
            if (router.getCachedPipe() instanceof PipeItemsPatternCraftingLogistics) {
                count++;
            }
        }
        return count;
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

    private static String summarizeOrderList(LinkedLogisticsOrderList orders) {
        if (orders == null) {
            return "orders=<none>";
        }
        return "orders=" + orders.size() + " subtrees=" + orders.getSubOrders().size()
                + " rootSize=" + orders.getTreeRootSize();
    }

    private static String describePipe(PipeItemsPatternCraftingLogistics pipe) {
        if (pipe == null) {
            return "pipe=<none>";
        }
        String router = "<no-router>";
        try {
            router = String.valueOf(pipe.getRouter().getSimpleID());
        } catch (RuntimeException ignored) {}
        return "pipe=(" + pipe.getX() + "," + pipe.getY() + "," + pipe.getZ() + " router=" + router + ")";
    }

    private static String safeFormat(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message == null ? "" : message;
        }
        try {
            return String.format(message, args);
        } catch (RuntimeException e) {
            StringBuilder out = new StringBuilder(message == null ? "" : message);
            out.append(" args=");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(args[i]);
            }
            return out.toString();
        }
    }

    private static String formatTime(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(time));
    }

    private static class RequestSnapshot {

        private final int id;
        private final String title;
        private final long time;
        private final String treeText;
        private final String ordersText;

        private RequestSnapshot(int id, String title, long time, String treeText, String ordersText) {
            this.id = id;
            this.title = title;
            this.time = time;
            this.treeText = treeText;
            this.ordersText = ordersText;
        }
    }

    private static class DebugEvent {

        private final int id;
        private final long time;
        private final int tick;
        private final String category;
        private final String message;

        private DebugEvent(int id, long time, int tick, String category, String message) {
            this.id = id;
            this.time = time;
            this.tick = tick;
            this.category = category;
            this.message = message;
        }
    }
}
