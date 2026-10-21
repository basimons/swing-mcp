package solutions.crosstech.swingmcp.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Names under which a component class is reported and found, shared by the
 * Swing and JavaFX toolkits.
 *
 * <p>{@link Class#getSimpleName()} is empty for anonymous classes, which are
 * common in UI code ({@code new JTabbedPane() { ... }}). Reporting and matching
 * on the simple name alone left such components with an empty class in
 * snapshots and made them impossible to find by class.</p>
 */
public final class ClassNames {

    /**
     * Base classes every component extends. Matching a query against these
     * would match every component, so the superclass walk stops here. Names,
     * not class literals, so a Swing-only JVM never loads a JavaFX class.
     */
    private static final Set<String> GENERIC_BASES = Set.of(
        "java.lang.Object", "java.awt.Component", "java.awt.Container", "javax.swing.JComponent",
        "javafx.scene.Node", "javafx.scene.Parent", "javafx.scene.layout.Region",
        "javafx.scene.control.Control");

    private ClassNames() {
    }

    /**
     * The class name to show for a component: its simple name, or for an
     * anonymous class the first named superclass, e.g. {@code "JTabbedPane (anonymous)"}.
     */
    public static String displayName(Class<?> type) {
        if (!type.isAnonymousClass()) {
            return type.getSimpleName();
        }
        Class<?> named = type;
        while (named.isAnonymousClass()) {
            named = named.getSuperclass();
        }
        return named.getSimpleName() + " (anonymous)";
    }

    /**
     * Whether {@code query} names the class, compared case-insensitively against
     * the simple names of the class and its superclasses up to the toolkit base
     * classes, so an anonymous {@code JTabbedPane} subclass is found by
     * {@code TabbedPane}. An anonymous class also answers to its name without the
     * package (e.g. {@code MainFrame$1}); a query containing a dot is also compared
     * against the fully qualified name.
     *
     * @param substring {@code true} for a substring match, {@code false} for an exact one
     */
    public static boolean matches(Class<?> type, String query, boolean substring) {
        String q = query.toLowerCase(Locale.ROOT);
        List<String> names = namesOf(type);
        if (q.indexOf('.') >= 0) {
            names.add(type.getName());
        }
        for (String name : names) {
            String n = name.toLowerCase(Locale.ROOT);
            if (substring ? n.contains(q) : n.equals(q)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> namesOf(Class<?> type) {
        List<String> names = new ArrayList<>();
        if (type.isAnonymousClass()) {
            // Only anonymous classes: for named nested classes the outer class name
            // is noise (BasicTabbedPaneUI$TabContainer is no TabbedPane).
            String binary = type.getName();
            names.add(binary.substring(binary.lastIndexOf('.') + 1));
        }
        for (Class<?> c = type; c != null && !GENERIC_BASES.contains(c.getName()); c = c.getSuperclass()) {
            if (!c.getSimpleName().isEmpty()) {
                names.add(c.getSimpleName());
            }
        }
        return names;
    }
}
