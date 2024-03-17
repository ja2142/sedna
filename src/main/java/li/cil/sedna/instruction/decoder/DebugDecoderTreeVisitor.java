package li.cil.sedna.instruction.decoder;

import li.cil.sedna.instruction.InstructionDeclaration;
import li.cil.sedna.instruction.decoder.tree.DecoderTreeBranchNode;
import li.cil.sedna.instruction.decoder.tree.DecoderTreeSwitchNode;
import li.cil.sedna.utils.DecisionTreeNode;

public final class DebugDecoderTreeVisitor implements DecoderTreeVisitor {
    DecisionTreeNode<Integer, InstructionDeclaration> decoderDecisionTreeRoot = new DecisionTreeNode<Integer, InstructionDeclaration>();

    public DebugDecoderTreeVisitor() {
    }

    public DecisionTreeNode<Integer, InstructionDeclaration> getDecisionTree() {
        return decoderDecisionTreeRoot;
    }

    public InstructionDeclaration decode(int opcode) {
        return decoderDecisionTreeRoot.decide(opcode);
    }

    @Override
    public DecoderTreeSwitchVisitor visitSwitch(final DecoderTreeSwitchNode node) {
        return new SwitchVisitor(decoderDecisionTreeRoot);
    }

    @Override
    public DecoderTreeBranchVisitor visitBranch(final DecoderTreeBranchNode node) {
        return new BranchVisitor(decoderDecisionTreeRoot);
    }

    @Override
    public DecoderTreeLeafVisitor visitInstruction() {
        return new LeafVisitor(decoderDecisionTreeRoot);
    }

    @Override
    public void visitEnd() {
    }

    private final class InnerNodeVisitor implements DecoderTreeVisitor {
        DecisionTreeNode<Integer, InstructionDeclaration> switchNode;

        public InnerNodeVisitor(DecisionTreeNode<Integer, InstructionDeclaration> switchNode) {
            this.switchNode = switchNode;
        }

        @Override
        public DecoderTreeSwitchVisitor visitSwitch(final DecoderTreeSwitchNode node) {
            return new SwitchVisitor(switchNode);
        }

        @Override
        public DecoderTreeBranchVisitor visitBranch(final DecoderTreeBranchNode node) {
            return new BranchVisitor(switchNode);
        }

        @Override
        public DecoderTreeLeafVisitor visitInstruction() {
            return new LeafVisitor(switchNode);
        }

        @Override
        public void visitEnd() {
        }
    }

    private final class SwitchVisitor implements DecoderTreeSwitchVisitor {
        private int mask;
        DecisionTreeNode<Integer, InstructionDeclaration> switchNode;

        public SwitchVisitor(DecisionTreeNode<Integer, InstructionDeclaration> switchNode) {
            this.switchNode = switchNode;
        }

        @Override
        public void visit(final int mask, final int[] patterns, final DecoderTreeNodeArguments arguments) {
            this.mask = mask;
        }

        @Override
        public DecoderTreeVisitor visitSwitchCase(final int index, final int pattern) {
            return new InnerNodeVisitor(switchNode.addChild(new DecisionTreeNode<Integer, InstructionDeclaration>((opcode) -> (opcode & mask) == pattern)));
        }

        @Override
        public void visitEnd() {
        }
    }

    private final class BranchVisitor implements DecoderTreeBranchVisitor {
        DecisionTreeNode<Integer, InstructionDeclaration> branchNode;

        public BranchVisitor(DecisionTreeNode<Integer, InstructionDeclaration> branchNode) {
            this.branchNode = branchNode;
        }

        @Override
        public void visit(final int count, final DecoderTreeNodeArguments arguments) {
        }

        @Override
        public DecoderTreeVisitor visitBranchCase(final int index, final int mask, final int pattern) {
            return new InnerNodeVisitor(branchNode);
        }

        @Override
        public void visitEnd() {
        }
    }

    private final class LeafVisitor implements DecoderTreeLeafVisitor {
        DecisionTreeNode<Integer, InstructionDeclaration> switchNode;

        public LeafVisitor(DecisionTreeNode<Integer, InstructionDeclaration> switchNode) {
            this.switchNode = switchNode;
        }

        @Override
        public void visitInstruction(final InstructionDeclaration declaration) {
            switchNode.value = declaration;
        }

        @Override
        public void visitEnd() {
        }
    }
}
