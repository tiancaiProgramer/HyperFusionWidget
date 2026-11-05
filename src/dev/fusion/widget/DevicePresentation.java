package dev.fusion.widget;

import java.util.*;

/** Homepage choices, hidden devices and the three independent user orders. */
public final class DevicePresentation {
    public static final String HIDDEN = "__fusion_hidden_devices__";
    public static final String HOME = "home", ALL = "all", CONCEALED = "hidden";
    public final List<String> pinned = new ArrayList<>();
    public final List<String> allOrder = new ArrayList<>();
    public final List<String> hiddenOrder = new ArrayList<>();
    public final Set<String> hidden = new LinkedHashSet<>();

    public static DevicePresentation migrate(List<String> discovered, Set<String> oldExcluded, int oldLimit) {
        DevicePresentation result = new DevicePresentation();
        result.discover(discovered);
        int limit = oldLimit == 0 ? Integer.MAX_VALUE : Math.max(0, oldLimit);
        for (String id : clean(discovered)) {
            if (!oldExcluded.contains(id) && result.pinned.size() < limit) result.pinned.add(id);
        }
        // Old unchecked items meant "not shown"; they are not explicitly hidden devices.
        return result;
    }
    public boolean discover(List<String> discovered) {
        boolean changed = false;
        for (String id : clean(discovered)) if (!allOrder.contains(id)) { allOrder.add(id); changed = true; }
        if (!allOrder.contains(HIDDEN)) { allOrder.add(HIDDEN); changed = true; }
        return changed;
    }
    public List<String> home(List<String> discovered) {
        List<String> result = new ArrayList<>();
        for (String id : clean(pinned)) if (!hidden.contains(id)) result.add(id);
        return result;
    }
    public List<String> all(List<String> discovered) {
        List<String> available = clean(discovered); available.add(HIDDEN);
        List<String> result = ordered(available, allOrder);
        result.removeIf(hidden::contains); return result;
    }
    public List<String> concealed(List<String> discovered) {
        List<String> result = ordered(clean(discovered), hiddenOrder);
        result.removeIf(id -> !hidden.contains(id)); return result;
    }
    public void pin(String id, boolean value) {
        if (id == null || id.isEmpty() || hidden.contains(id)) return;
        if (value) { if (!pinned.contains(id)) pinned.add(id); } else pinned.remove(id);
    }
    public void hide(String id, boolean value) {
        if (id == null || id.isEmpty() || HIDDEN.equals(id)) return;
        if (value) {
            hidden.add(id); pinned.remove(id);
            if (!hiddenOrder.contains(id)) hiddenOrder.add(id);
        } else hidden.remove(id);
    }
    public boolean move(String scope, String source, String target) {
        List<String> order = HOME.equals(scope) ? pinned : ALL.equals(scope) ? allOrder : CONCEALED.equals(scope) ? hiddenOrder : null;
        if (order == null) return false;
        int from = order.indexOf(source), to = order.indexOf(target);
        if (from < 0 || to < 0 || from == to) return false;
        order.remove(from); order.add(to, source); return true;
    }
    private static List<String> ordered(List<String> available, List<String> saved) {
        Set<String> remaining = new LinkedHashSet<>(available);
        List<String> result = new ArrayList<>();
        for (String id : saved) if (remaining.remove(id)) result.add(id);
        result.addAll(remaining); return result;
    }
    private static List<String> clean(List<String> ids) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String id : ids) if (id != null && !id.isEmpty()) result.add(id);
        return new ArrayList<>(result);
    }
}
