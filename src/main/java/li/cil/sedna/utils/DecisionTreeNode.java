package li.cil.sedna.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

public class DecisionTreeNode<DecideOnType, ValueType> {
    // value if leaf node, null otherwise
    public ValueType value;
    Predicate<DecideOnType> predicate;
    ArrayList<DecisionTreeNode<DecideOnType, ValueType>> children = new ArrayList<>();

    public DecisionTreeNode() {
        this((e) -> true);
    }
    public DecisionTreeNode(Predicate<DecideOnType> predicate) {
        this(predicate, null);
    }
    public DecisionTreeNode(Predicate<DecideOnType> predicate, ValueType value) {
        this.predicate = predicate;
        this.value = value;
    }

    public Collection<DecisionTreeNode<DecideOnType, ValueType>> getChildren() {
        return children;
    }
    public DecisionTreeNode<DecideOnType, ValueType> addChild(DecisionTreeNode<DecideOnType, ValueType> child) {
        children.add(child);
        return children.get(children.size() - 1);
    }
    public ValueType decide(DecideOnType decideOn) {
        if (!predicate.test(decideOn)) {
            return null;
        }
        if (value != null) {
            return value;
        }
        for (var child : children) {
            var result = child.decide(decideOn);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
