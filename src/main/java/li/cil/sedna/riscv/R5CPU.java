package li.cil.sedna.riscv;

import li.cil.sedna.api.device.InterruptController;
import li.cil.sedna.api.device.Resettable;
import li.cil.sedna.api.device.Steppable;
import li.cil.sedna.api.device.rtc.RealTimeCounter;
import li.cil.sedna.api.memory.MemoryMap;
import li.cil.sedna.gdbstub.CPUDebugInterface;

import javax.annotation.Nullable;

public interface R5CPU extends Steppable, Resettable, RealTimeCounter, InterruptController {
    static R5CPU create(final MemoryMap physicalMemory, @Nullable final RealTimeCounter rtc) {
        return R5CPUGenerator.create(physicalMemory, rtc);
    }

    static R5CPU create(final MemoryMap physicalMemory) {
        return create(physicalMemory, null);
    }

    // make a non-generated version of R5CPU, which makes debugging easier (and probably makes everything way slower)
    static R5CPU create(final MemoryMap physicalMemory, @Nullable final RealTimeCounter rtc, boolean debugCpu) {
        if(debugCpu){
            return new R5CPUNonGenerated(physicalMemory, rtc);
        } else {
            return create(physicalMemory, rtc);
        }
    }

    long getISA();

    void setXLEN(int value);

    void reset(boolean hard, long pc);

    void invalidateCaches();

    void setFrequency(int value);

    CPUDebugInterface getDebugInterface();
}
