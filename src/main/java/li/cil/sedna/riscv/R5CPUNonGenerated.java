package li.cil.sedna.riscv;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import javax.annotation.Nullable;

import li.cil.sedna.api.Sizes;
import li.cil.sedna.api.device.MemoryMappedDevice;
import li.cil.sedna.api.device.rtc.RealTimeCounter;
import li.cil.sedna.api.memory.MemoryAccessException;
import li.cil.sedna.api.memory.MemoryMap;
import li.cil.sedna.instruction.InstructionApplication;
import li.cil.sedna.instruction.InstructionDeclaration;
import li.cil.sedna.instruction.InstructionDefinition.Instruction;
import li.cil.sedna.instruction.InstructionDefinition.InstructionSize;
import li.cil.sedna.instruction.InstructionDefinition.ProgramCounter;
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

    public static Method getInstructionMethod(String instructionName) {
        final Class<? extends Annotation> instructionAnnotation = Instruction.class;
        for (final Method method : R5CPUTemplate.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(instructionAnnotation)) {
                Instruction instruction = (Instruction)method.getAnnotation(instructionAnnotation);
                if(instruction.value().equals(instructionName)) {
                    return method;
                }
            }
        }
        return null;
    }

    public void executeInstruction(int opcode) throws R5IllegalInstructionException  {
        var instructionDeclared = decoderDecisionTree.decide(opcode);
        var instructionMethod = getInstructionMethod(instructionDeclared.name);
        var instructionApplied = InstructionApplication.fromOpcode(instructionDeclared, opcode);
        // this is probably third copy of args? could probably be done in a lot less inefficient way
        var args = new ArrayList<Object>(instructionApplied.args);

        for (var parameterAnnotation : instructionMethod.getParameterAnnotations()) {
            var annotation = parameterAnnotation[0]; // assuming there's exactly one annotation per parameter
            if (annotation.annotationType().equals(InstructionSize.class)) {
                int instructionSize = (opcode & 0b11) == 0b11? 4: 2;
                args.add(instructionSize);
            }
            if (annotation.annotationType().equals(ProgramCounter.class)) {
                args.add(pc);
            }
        }

        boolean updatePC = true;
        try{
            var ret = instructionMethod.invoke(savedThis, args.stream().toArray());
            // TODO there are probably more non-pc-updating instructions
            if(ret != null && ret.getClass().equals(Boolean.class)) {
                if((Boolean)ret && instructionApplied.name.charAt(0) == 'B') {
                    // don't update pc on true branch
                    updatePC = false;
                }
            }
            if (instructionApplied.name.charAt(0) == 'J') {
                // don't update pc on jump
                updatePC = false;
            }
            if (instructionApplied.name.equals("ECALL")
                || instructionApplied.name.equals("EBREAK")
                || instructionApplied.name.equals("MRET")
                || instructionApplied.name.equals("SRET")) {
                // don't update pc on exceptions or returns
                updatePC = false;
            }
        } catch(InvocationTargetException e) {
            if (Error.class.isAssignableFrom(e.getCause().getClass())) {
                throw (Error)e.getCause();
            } else if(RuntimeException.class.isAssignableFrom(e.getCause().getClass())) {
                throw (RuntimeException)e.getCause();
            } else if(e.getCause().getClass().equals(R5IllegalInstructionException.class)) {
                throw new R5IllegalInstructionException();
            } else {
                throw new Error("unhandled exception when executing instruction", e.getCause());
            }
        } catch(IllegalAccessException e) {
            throw new Error("invoke failed with IllegalAccessException", e.getCause());
        }

        if (updatePC) {
            if((opcode & 0b11) == 0b11) {
                this.pc += 4;
            } else {
                this.pc += 2;
            }
        }
    }

    @Override
    protected void doDecode() throws R5IllegalInstructionException, R5MemoryAccessException {
        // taken from interpret()
        final TLBEntry cache = fetchPage(pc);
        final MemoryMappedDevice device = cache.device;
        final int instOffset = (int) (pc + cache.toOffset);
        final int instEnd = instOffset - (int) (pc & R5.PAGE_ADDRESS_MASK) // Page start.
            + ((1 << R5.PAGE_ADDRESS_SHIFT) - 2); // Page size minus 16bit.

        int inst;
        try {
            if (instOffset < instEnd) { // Likely case, instruction fully inside page.
                inst = (int) device.load(instOffset, Sizes.SIZE_32_LOG2);
            } else { // Unlikely case, instruction may leave page if it is 32bit.
                inst = (short) device.load(instOffset, Sizes.SIZE_16_LOG2) & 0xFFFF;
                if ((inst & 0b11) == 0b11) { // 32bit instruction.
                    final TLBEntry highCache = fetchPage(pc + 2);
                    final MemoryMappedDevice highDevice = highCache.device;
                    inst |= highDevice.load((int) (pc + 2 + highCache.toOffset), Sizes.SIZE_16_LOG2) << 16;
                }
            }
        } catch (final MemoryAccessException e) {
            raiseException(R5.EXCEPTION_FAULT_FETCH, pc);
            return;
        }

        executeInstruction(inst);
    }
}
