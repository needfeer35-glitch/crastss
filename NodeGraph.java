
package net.craftsi.mod.node;

import java.util.*;

public class NodeGraph {

    private static final NodeGraph INSTANCE = new NodeGraph();
    public static NodeGraph getInstance() { return INSTANCE; }

    private final Map<String, RecipeNode> nodes = new HashMap<>();

    public Collection<RecipeNode> getAllNodes() {
        return nodes.values();
    }

    public RecipeNode addNode(String id) {
        RecipeNode n = new RecipeNode(id);
        nodes.put(id, n);
        return n;
    }

    public void autoLayout() {
        int i = 0;
        for (RecipeNode n : nodes.values()) {
            n.x = (i % 6) * 140;
            n.y = (i / 6) * 100;
            i++;
        }
    }
}
