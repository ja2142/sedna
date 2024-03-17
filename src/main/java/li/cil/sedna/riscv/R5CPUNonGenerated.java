package li.cil.sedna.riscv;

import javax.annotation.Nullable;

import li.cil.sedna.api.device.rtc.RealTimeCounter;
import li.cil.sedna.api.memory.MemoryMap;
import li.cil.sedna.instruction.InstructionDeclaration;
import li.cil.sedna.instruction.decoder.DebugDecoderTreeVisitor;
import li.cil.sedna.riscv.exception.R5IllegalInstructionException;
import li.cil.sedna.riscv.exception.R5MemoryAccessException;
import li.cil.sedna.utils.DecisionTreeNode;

public class R5CPUNonGenerated extends R5CPUTemplate {
    DecisionTreeNode<Integer, InstructionDeclaration> decoderDecisionTree;

    public R5CPUNonGenerated(final MemoryMap physicalMemory, @Nullable final RealTimeCounter rtc) {
        super(physicalMemory, rtc);
        DebugDecoderTreeVisitor debugDecoderCreator = new DebugDecoderTreeVisitor();
        R5Instructions.getDecoderTree().accept(debugDecoderCreator);
        decoderDecisionTree = debugDecoderCreator.getDecisionTree();
    }

    @Override
    protected void doDecode() throws R5IllegalInstructionException, R5MemoryAccessException {
        // TODO get instruction from template, actually do stuff
        decoderDecisionTree.decide(0x1234);
        throw new UnsupportedOperationException();
    }
}
