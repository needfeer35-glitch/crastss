package net.craftsi.mod.node;

import java.util.ArrayList;
import java.util.List;

public class NodeGraph {
    private static final NodeGraph INSTANCE = new NodeGraph();
    private final List<RecipeNode> nodes = new ArrayList<>();
    private final List<String[]> connections = new ArrayList<>();

    public static NodeGraph getInstance() { return INSTANCE; }

    public void addNode(RecipeNode node) { nodes.add(node); }
    public List<RecipeNode> getNodes() { return nodes; }
    public List<String[]> getConnections() { return connections; }
    public void addConnection(String fromId, String toId) {
        connections.add(new String[]{fromId, toId});
    }
    public void removeNode(String id) {
        nodes.removeIf(n -> n.id.equals(id));
        connections.removeIf(c -> c[0].equals(id) || c[1].equals(id));
    }
    public RecipeNode findById(String id) {
        return nodes.stream().filter(n -> n.id.equals(id)).findFirst().orElse(null);
    }
}
