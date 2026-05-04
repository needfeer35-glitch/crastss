package net.craftsi.mod.node;

import java.util.ArrayList;
import java.util.List;

public class NodeGraph {
    private static final NodeGraph INSTANCE = new NodeGraph();
    private final List<RecipeNode> nodes = new ArrayList<>();

    public static NodeGraph getInstance() {
        return INSTANCE;
    }

    public void addNode(RecipeNode node) {
        nodes.add(node);
    }

    public List<RecipeNode> getNodes() {
        return nodes;
    }

    public void removeNode(String id) {
        nodes.removeIf(n -> n.id.equals(id));
    }
}
