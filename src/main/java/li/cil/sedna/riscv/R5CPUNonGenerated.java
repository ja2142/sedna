package li.cil.sedna.riscv;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import javax.annotation.Nullable;
import it.unimi.dsi.fastutil.longs.LongSet;
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
import li.cil.sedna.instruction.decoder.tree.AbstractDecoderTreeNode;
import li.cil.sedna.riscv.exception.R5IllegalInstructionException;
import li.cil.sedna.riscv.exception.R5MemoryAccessException;
import li.cil.sedna.utils.DecisionTreeNode;

public class R5CPUNonGenerated extends R5CPUTemplate {
    DebugDecoderTreeVisitor decoder64;
    DebugDecoderTreeVisitor decoder32;

    public R5CPUNonGenerated(final MemoryMap physicalMemory, @Nullable final RealTimeCounter rtc) {
        super(physicalMemory, rtc);

        decoder64 = specToDecoder(R5Instructions.RV64.getDecoderTree());
        decoder32 = specToDecoder(R5Instructions.RV32.getDecoderTree());
    }

    private static DebugDecoderTreeVisitor specToDecoder(AbstractDecoderTreeNode decoderTree) {
        DebugDecoderTreeVisitor debugDecoderCreator = new DebugDecoderTreeVisitor();
        decoderTree.accept(debugDecoderCreator);
        return debugDecoderCreator;
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

    @Override
    protected void interpret(final boolean singleStep, final boolean ignoreBreakpoints) {
        try {
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

            interpretInstruction(device, inst, pc, instOffset, singleStep ? 0 : instEnd, ignoreBreakpoints ? null : cache.breakpoints);

        } catch (final R5MemoryAccessException e) {
            raiseException(e.getType(), e.getAddress());
        }
    }

    private DebugDecoderTreeVisitor getDecoder() {
        if (xlen == R5.XLEN_32) {
            return decoder32;
        } else {
            return decoder64;
        }
    }

    protected void interpretInstruction(final MemoryMappedDevice device, int inst, long pc, int instOffset, final int instEnd, final LongSet breakpoints) {
        try{
            int instructionSize = (inst & 0b11) == 0b11? 4: 2;
            
            var instruction = getDecoder().decode(inst);
            var instructionMethod = getInstructionMethod(instruction.name);

            boolean updatePC = true;
            var ret = invokeInstruction(instruction, instructionMethod, pc, instructionSize);
            if(ret && instruction.name.charAt(0) == 'B') {
                // don't update pc on true branch
                updatePC = false;
            }
            if (instruction.name.charAt(0) == 'J') {
                // don't update pc on jump
                updatePC = false;
            }
            if (instruction.name.equals("ECALL")
                || instruction.name.equals("EBREAK")
                || instruction.name.equals("MRET")
                || instruction.name.equals("SRET")) {
                // don't update pc on exceptions or returns
                updatePC = false;
            }

            if (updatePC) {
                this.pc += instructionSize;
            }
        // } catch (final MemoryAccessException e) {
        //     this.pc = pc;
        //     raiseException(R5.EXCEPTION_FAULT_FETCH, pc);
        } catch (final R5IllegalInstructionException e) {
            this.pc = pc;
            raiseException(R5.EXCEPTION_ILLEGAL_INSTRUCTION, inst);
        } catch (final R5MemoryAccessException e) {
            this.pc = pc;
            raiseException(e.getType(), e.getAddress());
        }
    }

    boolean invokeInstruction(InstructionApplication instructionApplied, Method instructionMethod, long pc, int instructionSize)
        throws R5IllegalInstructionException, R5MemoryAccessException {
        if (instructionApplied.name.equals("NOP")) {
            return false;
        }
        if (instructionApplied.name.equals("ILLEGAL")) {
            throw new R5IllegalInstructionException();
        }

        // this is probably third copy of args? could probably be done in a lot less inefficient way
        var args = new ArrayList<Object>(instructionApplied.args);

        for (var parameterAnnotation : instructionMethod.getParameterAnnotations()) {
            var annotation = parameterAnnotation[0]; // assuming there's exactly one annotation per parameter
            if (annotation.annotationType().equals(InstructionSize.class)) {
                args.add(instructionSize);
            }
            if (annotation.annotationType().equals(ProgramCounter.class)) {
                args.add(pc);
            }
        }

        try{
            var ret = instructionMethod.invoke(this, args.stream().toArray());
            if(ret != null && ret.getClass().equals(Boolean.class)) {
                return (Boolean)ret;
            }
            return false;
        } catch(InvocationTargetException | IllegalAccessException e) {
            if (Error.class.isAssignableFrom(e.getCause().getClass())) {
                throw (Error)e.getCause();
            } else if(RuntimeException.class.isAssignableFrom(e.getCause().getClass())) {
                throw (RuntimeException)e.getCause();
            } else if(e.getCause().getClass().equals(R5IllegalInstructionException.class)) {
                throw (R5IllegalInstructionException)e.getCause();
            } else if(e.getCause().getClass().equals(R5MemoryAccessException.class)) {
                throw (R5MemoryAccessException)e.getCause();
            } else {
                throw new Error("unhandled exception when executing instruction", e.getCause());
            }
        }
    }

}
