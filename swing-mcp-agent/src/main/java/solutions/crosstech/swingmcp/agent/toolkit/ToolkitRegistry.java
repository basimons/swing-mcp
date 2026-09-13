package solutions.crosstech.swingmcp.agent.toolkit;

import solutions.crosstech.swingmcp.common.spi.UiToolkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discovers the UI toolkits present in the target JVM and routes each command
 * to the right one.
 *
 * <h2>Optional dependency handling</h2>
 * <p>The agent ships as a single jar that is attached to somebody else's
 * application, so it cannot assume JavaFX is on the classpath — and must not
 * fail to start when it is not. {@link JavaFxToolkit} is therefore never named
 * directly here: it is resolved by name, and the very first thing checked is
 * whether {@code javafx.application.Platform} loads at all. In a Swing-only
 * application no {@code javafx.*} class is ever resolved, and in a JavaFX-only
 * application the Swing side simply reports no windows.</p>
 *
 * <h2>Routing</h2>
 * <p>Commands that carry a {@code uid} go to whichever toolkit issued that uid,
 * identified by its prefix. Commands that do not are served by the primary
 * toolkit — the one that currently has a focused window, falling back to the
 * one that has any window at all. Mixed applications, where a Swing frame hosts
 * JavaFX through a {@code JFXPanel}, therefore work without the client having
 * to know which is which.</p>
 */
public class ToolkitRegistry {

    private static final Logger LOG = Logger.getLogger(ToolkitRegistry.class.getName());

    private static final String JAVAFX_PROBE = "javafx.application.Platform";
    private static final String JAVAFX_TOOLKIT =
        "solutions.crosstech.swingmcp.agent.toolkit.JavaFxToolkit";

    private final List<UiToolkit> toolkits = new ArrayList<>();

    public ToolkitRegistry() {
        this(discover());
    }

    ToolkitRegistry(List<UiToolkit> toolkits) {
        this.toolkits.addAll(toolkits);
    }

    private static List<UiToolkit> discover() {
        List<UiToolkit> found = new ArrayList<>();
        found.add(new SwingToolkit());
        UiToolkit fx = loadJavaFx();
        if (fx != null) {
            found.add(fx);
        }
        return found;
    }

    /**
     * Loads the JavaFX toolkit if — and only if — JavaFX is present. Returns
     * {@code null} otherwise, without letting any linkage error escape: a
     * missing optional toolkit is a normal condition, not a failure.
     */
    static UiToolkit loadJavaFx() {
        try {
            Class.forName(JAVAFX_PROBE, false, ToolkitRegistry.class.getClassLoader());
        } catch (Throwable notPresent) {
            LOG.log(Level.FINE, "JavaFX not present in this JVM; Swing only");
            return null;
        }
        try {
            Class<?> impl = Class.forName(JAVAFX_TOOLKIT);
            return (UiToolkit) impl.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "JavaFX is present but its toolkit could not be initialised", t);
            return null;
        }
    }

    /** Every toolkit whose classes loaded, whether or not it currently has windows. */
    public List<UiToolkit> all() {
        return List.copyOf(toolkits);
    }

    /** Toolkits that are live right now. */
    public List<UiToolkit> available() {
        List<UiToolkit> out = new ArrayList<>();
        for (UiToolkit t : toolkits) {
            try {
                if (t.isAvailable()) {
                    out.add(t);
                }
            } catch (Throwable ignored) {
                // A toolkit that cannot answer is simply not available.
            }
        }
        return out;
    }

    /**
     * The toolkit to use for a command with no uid. Prefers a live toolkit;
     * falls back to Swing so that error messages stay familiar when nothing is
     * showing yet.
     */
    public UiToolkit primary() {
        List<UiToolkit> live = available();
        if (live.size() == 1) {
            return live.get(0);
        }
        if (!live.isEmpty()) {
            for (UiToolkit t : live) {
                if (hasFocusedWindow(t)) {
                    return t;
                }
            }
            return live.get(0);
        }
        return toolkits.get(0);
    }

    private boolean hasFocusedWindow(UiToolkit t) {
        try {
            for (Map<String, Object> w : t.listWindows()) {
                if (Boolean.TRUE.equals(w.get("focused"))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // treated as "no focused window"
        }
        return false;
    }

    /** Routes by uid prefix, falling back to {@link #primary()} when there is no uid. */
    public UiToolkit forParams(Map<String, Object> params) {
        Object uid = params == null ? null : params.get("uid");
        if (uid != null) {
            return forUid(String.valueOf(uid));
        }
        Object toolkit = params == null ? null : params.get("toolkit");
        if (toolkit != null) {
            String want = String.valueOf(toolkit);
            for (UiToolkit t : toolkits) {
                if (t.id().equalsIgnoreCase(want)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Unknown toolkit: " + want);
        }
        return primary();
    }

    /** Routes a uid to the toolkit that issued it. */
    public UiToolkit forUid(String uid) {
        for (UiToolkit t : toolkits) {
            if (uid.startsWith(t.uidPrefix())) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unrecognised uid: " + uid
            + ". Expected one of " + prefixes() + ".");
    }

    private List<String> prefixes() {
        List<String> out = new ArrayList<>();
        for (UiToolkit t : toolkits) {
            out.add(t.uidPrefix() + "*");
        }
        return out;
    }

    /**
     * Windows across every live toolkit, each tagged with the toolkit that owns
     * it and a {@code windowId} that stays unambiguous in a mixed application.
     * The per-toolkit {@code index} is preserved so existing single-toolkit
     * clients keep working unchanged.
     */
    public List<Map<String, Object>> listAllWindows() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (UiToolkit t : available()) {
            for (Map<String, Object> w : t.listWindows()) {
                Map<String, Object> copy = new LinkedHashMap<>(w);
                copy.putIfAbsent("toolkit", t.id());
                copy.put("windowId", t.id() + ":" + copy.get("index"));
                out.add(copy);
            }
        }
        return out;
    }
}
