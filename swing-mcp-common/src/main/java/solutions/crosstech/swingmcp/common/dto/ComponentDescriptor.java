package solutions.crosstech.swingmcp.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Describes a single Swing component node in a snapshot tree.
 *
 * <p>{@code tooltip} is set only when the component has a tooltip that differs
 * from its text; for icon-only buttons it is often the only label there is.
 * {@code omittedChildren} is set when the snapshot's {@code maxDepth} stopped
 * above this node's children: it is the number of direct children left out, so
 * a client can snapshot just this node's subtree (by uid) to see them.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComponentDescriptor(
    String uid,
    String componentClass,
    String name,
    String text,
    String tooltip,
    boolean enabled,
    boolean visible,
    boolean focusable,
    int x,
    int y,
    int width,
    int height,
    String selectionState,
    List<ComponentDescriptor> children,
    Integer omittedChildren
) {
    /** Backward-compatible constructor for a component without tooltip or omitted children. */
    public ComponentDescriptor(String uid, String componentClass, String name, String text,
                               boolean enabled, boolean visible, boolean focusable,
                               int x, int y, int width, int height,
                               String selectionState, List<ComponentDescriptor> children) {
        this(uid, componentClass, name, text, null, enabled, visible, focusable,
            x, y, width, height, selectionState, children, null);
    }
}
