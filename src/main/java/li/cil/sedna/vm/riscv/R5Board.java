package li.cil.sedna.vm.riscv;

import li.cil.sedna.api.vm.MemoryMap;
import li.cil.sedna.api.vm.device.InterruptController;
import li.cil.sedna.api.vm.device.Resettable;
import li.cil.sedna.api.vm.device.Steppable;
import li.cil.sedna.api.vm.device.memory.MemoryAccessException;
import li.cil.sedna.api.vm.device.memory.MemoryMappedDevice;
import li.cil.sedna.api.vm.device.memory.PhysicalMemory;
import li.cil.sedna.api.vm.device.memory.Sizes;
import li.cil.sedna.api.vm.device.rtc.RealTimeCounter;
import li.cil.sedna.api.vm.devicetree.DeviceNames;
import li.cil.sedna.api.vm.devicetree.DeviceTree;
import li.cil.sedna.api.vm.devicetree.DeviceTreePropertyNames;
import li.cil.sedna.vm.SimpleMemoryMap;
import li.cil.sedna.vm.device.memory.Memory;
import li.cil.sedna.vm.devicetree.DeviceTreeRegistry;
import li.cil.sedna.vm.devicetree.FlattenedDeviceTree;
import li.cil.sedna.vm.riscv.device.R5CoreLocalInterrupter;
import li.cil.sedna.vm.riscv.device.R5PlatformLevelInterruptController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class R5Board implements Steppable, Resettable {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final int PHYSICAL_MEMORY_FIRST = 0x80000000;
    private static final int PHYSICAL_MEMORY_LAST = 0xFFFFFFFF;
    private static final int DEVICE_MEMORY_FIRST = 0x10000000;
    private static final int DEVICE_MEMORY_LAST = 0x7FFFFFFF;
    private static final int CLINT_ADDRESS = 0x02000000;
    private static final int PLIC_ADDRESS = 0x0C000000;

    private static final int BIOS_ADDRESS = 0x1000;
    private static final int LOW_MEMORY_SIZE = 0x2000; // Just needs to fit "jump to firmware".

    private static final int FIRMWARE_ADDRESS = PHYSICAL_MEMORY_FIRST;
    private static final int FDT_ADDRESS = FIRMWARE_ADDRESS + 0x02200000;

    private final RealTimeCounter rtc;
    private final MemoryMap memoryMap;
    private final R5CPU cpu;
    private final R5PlatformLevelInterruptController plic;
    private final List<MemoryMappedDevice> devices = new ArrayList<>();
    private final List<Steppable> steppableDevices = new ArrayList<>();

    private String bootargs;

    public R5Board() {
        memoryMap = new SimpleMemoryMap();
        rtc = cpu = new R5CPU(memoryMap);

        final PhysicalMemory flash = Memory.create(LOW_MEMORY_SIZE);
        final R5CoreLocalInterrupter clint = new R5CoreLocalInterrupter(rtc);
        plic = new R5PlatformLevelInterruptController();

        steppableDevices.add(cpu);

        // Wire up interrupts.
        clint.putHart(0, cpu);
        plic.setHart(cpu);

        // Map devices to memory.
        addDevice(CLINT_ADDRESS, clint);
        addDevice(PLIC_ADDRESS, plic);
        memoryMap.addDevice(BIOS_ADDRESS, flash);
    }

    public MemoryMap getMemoryMap() {
        return memoryMap;
    }

    public R5CPU getCpu() {
        return cpu;
    }

    public InterruptController getInterruptController() {
        return plic;
    }

    public void setBootargs(final String value) {
        if (value != null && value.length() > 64) {
            throw new IllegalArgumentException();
        }
        this.bootargs = value;
    }

    public boolean addDevice(final int address, final MemoryMappedDevice device) {
        if (device.getLength() == 0) {
            return false;
        }

        if (devices.contains(device)) {
            // This prevents adding the same device at different addresses. However, that
            // could be circumvented by using a wrapper device, so we save ourselves the
            // additional bookkeeping needed for this here.
            return false;
        }

        if (!memoryMap.addDevice(address, device)) {
            return false;
        }

        devices.add(device);

        if (device instanceof Steppable) {
            steppableDevices.add((Steppable) device);
        }

        return true;
    }

    public boolean addDevice(final MemoryMappedDevice device) {
        if (device.getLength() == 0) {
            return false;
        }

        final int startMin, startMax;
        if (device instanceof PhysicalMemory) {
            startMin = PHYSICAL_MEMORY_FIRST;
            startMax = PHYSICAL_MEMORY_LAST - device.getLength() + 1;
        } else {
            startMin = DEVICE_MEMORY_FIRST;
            startMax = DEVICE_MEMORY_LAST - device.getLength() + 1;
        }

        final OptionalInt address = memoryMap.findFreeRange(startMin, startMax, device.getLength());
        return address.isPresent() && addDevice(address.getAsInt(), device);
    }

    public void removeDevice(final MemoryMappedDevice device) {
        memoryMap.removeDevice(device);
        devices.remove(device);

        if (device instanceof Steppable) {
            steppableDevices.remove(device);
        }
    }

    @Override
    public void step(final int cycles) {
        for (final Steppable device : steppableDevices) {
            device.step(cycles);
        }
    }

    @Override
    public void reset() {
        cpu.reset();

        try {
            for (final MemoryMappedDevice device : devices) {
                if (device instanceof PhysicalMemory) {
                    for (int offset = 0; offset < device.getLength(); offset += 4) {
                        device.store(offset, 0, Sizes.SIZE_32_LOG2);
                    }
                }
            }

            final FlattenedDeviceTree fdt = buildDeviceTree().flatten();
            final byte[] dtb = fdt.toDTB();
            for (int i = 0; i < dtb.length; i++) {
                memoryMap.store(FDT_ADDRESS + i, dtb[i], 0);
            }

            final int lui = 0b0110111;
            final int jalr = 0b1100111;

            final int rd_x5 = 5 << 7;
            final int rd_x11 = 11 << 7;
            final int rs1_x5 = 5 << 15;

            int pc = 0x1000; // R5CPU starts executing at 0x1000.

            // lui a1, FDT_ADDRESS  -> store FDT address in a1 for firmware
            memoryMap.store(pc, lui | rd_x11 + FDT_ADDRESS, 2);
            pc += 4;

            // lui t0, PHYSICAL_MEMORY_FIRST  -> load address of firmware
            memoryMap.store(pc, lui | rd_x5 + PHYSICAL_MEMORY_FIRST, 2);
            pc += 4;

            // jalr zero, t0, 0  -> jump to firmware
            memoryMap.store(pc, jalr | rs1_x5, 2);
        } catch (final MemoryAccessException e) {
            LOGGER.error(e);
        }
    }

    private DeviceTree buildDeviceTree() {
        final DeviceTree root = DeviceTreeRegistry.create(memoryMap);
        root
                .addProp(DeviceTreePropertyNames.NUM_ADDRESS_CELLS, 2)
                .addProp(DeviceTreePropertyNames.NUM_SIZE_CELLS, 2)
                .addProp(DeviceTreePropertyNames.COMPATIBLE, "riscv-sedna")
                .addProp(DeviceTreePropertyNames.MODEL, "riscv-sedna,generic");

        root.putChild(DeviceNames.CPUS, cpus -> cpus
                .addProp(DeviceTreePropertyNames.NUM_ADDRESS_CELLS, 1)
                .addProp(DeviceTreePropertyNames.NUM_SIZE_CELLS, 0)
                .addProp("timebase-frequency", rtc.getFrequency())

                .putChild(DeviceNames.CPU, 0, cpuNode -> cpuNode
                        .addProp(DeviceTreePropertyNames.DEVICE_TYPE, DeviceNames.CPU)
                        .addProp(DeviceTreePropertyNames.REG, 0)
                        .addProp(DeviceTreePropertyNames.STATUS, "okay")
                        .addProp(DeviceTreePropertyNames.COMPATIBLE, "riscv")
                        .addProp("riscv,isa", "rv32imacsu")

                        .addProp("mmu-type", "riscv,sv32")
                        .addProp("clock-frequency", cpu.getFrequency())

                        .putChild(DeviceNames.INTERRUPT_CONTROLLER, ic -> ic
                                .addProp("#interrupt-cells", 1)
                                .addProp(DeviceTreePropertyNames.INTERRUPT_CONTROLLER)
                                .addProp(DeviceTreePropertyNames.COMPATIBLE, "riscv,cpu-intc")
                                .addProp(DeviceTreePropertyNames.PHANDLE, ic.createPHandle(cpu)))));

        root.putChild("soc", soc -> soc
                .addProp(DeviceTreePropertyNames.NUM_ADDRESS_CELLS, 2)
                .addProp(DeviceTreePropertyNames.NUM_SIZE_CELLS, 2)
                .addProp(DeviceTreePropertyNames.COMPATIBLE, "simple-bus")
                .addProp(DeviceTreePropertyNames.RANGES));

        for (final MemoryMappedDevice device : devices) {
            DeviceTreeRegistry.visit(root, memoryMap, device);
        }

        if (bootargs != null) {
            root.putChild("chosen", chosen -> chosen
                    .addProp("bootargs", bootargs));
        }

        return root;
    }
}