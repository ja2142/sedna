package li.cil.sedna.riscv;

import li.cil.sedna.api.Sizes;
import li.cil.sedna.api.device.MemoryMappedDevice;
import li.cil.sedna.api.memory.MemoryAccessException;
import li.cil.sedna.api.memory.MemoryMap;
import li.cil.sedna.device.memory.Memory;
import li.cil.sedna.elf.*;
import li.cil.sedna.memory.SimpleMemoryMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public final class ISATests {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String[] TEST_FILTERS = {
            "rv32.*",
            "rv64.*"
    };

    private static final long PHYSICAL_MEMORY_START = 0x80000000L;
    private static final int PHYSICAL_MEMORY_LENGTH = 512 * 1024;

    private DynamicTest isaTestFromFile(File file, boolean useDebugCPU) {
        final String filter = getMatchingFilter(file);
        if (filter == null) {
            LOGGER.info("No filter matches file [{}], skipping.", file.getName());
            return null;
        }

        var debugSuffix = useDebugCPU? "_debug" :"";
        var testName = file.getName() + debugSuffix;
        URI testUri = null;
        try {
            testUri = new URI("file", (String)null, file.getAbsoluteFile().getPath() + debugSuffix, (String)null);
        } catch (URISyntaxException e) {
        }
        return DynamicTest.dynamicTest(testName, testUri, () -> {
            LOGGER.info("Running test for file [{}].", file.getName());

            final ELF elf = ELFParser.parse(file);

            final long toHostAddress = getToHostAddress(elf);

            final MemoryMap memoryMap = new SimpleMemoryMap();
            final R5CPU cpu = R5CPU.create(memoryMap, null, useDebugCPU);
            final HostTargetInterface htif = new HostTargetInterface();

            // RAM block below and potentially up to HTIF.
            if (PHYSICAL_MEMORY_START < toHostAddress) {
                final long end = Math.min(PHYSICAL_MEMORY_START + PHYSICAL_MEMORY_LENGTH, toHostAddress);
                memoryMap.addDevice(PHYSICAL_MEMORY_START, Memory.create((int) (end - PHYSICAL_MEMORY_START)));
            }

            // RAM block above and potentially starting from HTIF.
            if (PHYSICAL_MEMORY_START + PHYSICAL_MEMORY_LENGTH > toHostAddress + htif.getLength()) {
                final long start = Math.max(PHYSICAL_MEMORY_START, toHostAddress + htif.getLength());
                memoryMap.addDevice(start, Memory.create((int) (PHYSICAL_MEMORY_START + PHYSICAL_MEMORY_LENGTH - start)));
            }

            loadProgramSegments(elf, memoryMap);

            memoryMap.addDevice(toHostAddress, htif);

            cpu.reset(true, elf.entryPoint);
            if (file.getName().startsWith("rv32")) {
                cpu.setXLEN(R5.XLEN_32);
            }

            assertThrows(TestSuccessful.class, () -> {
                for (int i = 0; i < 1_000_000; i++) {
                    cpu.step(1_000);
                }
            });
        });
    }

    @TestFactory
    public Collection<DynamicTest> testISA() {
        final File[] testFiles = new File("src/test/data/riscv-tests").listFiles();
        assertNotNull(testFiles);
        var testsNonDebug = Arrays.stream(testFiles)
                .filter(File::isFile)
                .map(file -> isaTestFromFile(file, false))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        var testsDebug = Arrays.stream(testFiles)
                .filter(File::isFile)
                .map(file -> isaTestFromFile(file, true))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        testsDebug.addAll(testsNonDebug);
        return testsDebug;
    }

    private long getToHostAddress(final ELF elf) {
        for (final SectionHeader header : elf.sectionHeaderTable) {
            if (".tohost".equals(header.name)) {
                return header.virtualAddress;
            }
        }

        fail(".tohost not found in ELF");
        return 0; // appeasing the compiler: this line will never be executed.
    }

    private void loadProgramSegments(final ELF elf, final MemoryMap memoryMap) throws MemoryAccessException {
        for (final ProgramHeader header : elf.programHeaderTable) {
            if (header.is(ProgramHeaderType.PT_LOAD)) {
                final ByteBuffer data = header.getView();
                final long address = header.physicalAddress;
                final long length = header.sizeInFile;
                for (int i = 0; i < length; i++) {
                    memoryMap.store(address + i, data.get(), Sizes.SIZE_8_LOG2);
                }
            }
        }
    }

    @Nullable
    private static String getMatchingFilter(final File file) {
        for (final String filter : TEST_FILTERS) {
            if (file.getName().matches(filter)) {
                return filter;
            }
        }
        return null;
    }

    private static class HostTargetInterface implements MemoryMappedDevice {
        protected long toHost, fromHost;

        @Override
        public int getLength() {
            return 0x48;
        }

        @Override
        public int getSupportedSizes() {
            return (1 << Sizes.SIZE_32_LOG2);
        }

        @Override
        public long load(final int offset, final int sizeLog2) {
            assert sizeLog2 == Sizes.SIZE_32_LOG2 ||
                   sizeLog2 == Sizes.SIZE_64_LOG2;
            switch (offset) {
                case 0x00: {
                    return toHost;
                }
                case 0x04: {
                    return (int) (toHost >> 32);
                }

                case 0x40: {
                    return fromHost;
                }
                case 0x44: {
                    return (int) (fromHost >> 32);
                }
            }

            return 0;
        }

        @Override
        public void store(final int offset, final long value, final int sizeLog2) {
            assert sizeLog2 == Sizes.SIZE_32_LOG2 ||
                   sizeLog2 == Sizes.SIZE_64_LOG2;
            switch (offset) {
                case 0x00: {
                    if (sizeLog2 == Sizes.SIZE_32_LOG2) {
                        toHost = (toHost & ~0xFFFFFFFFL) | value;
                    } else {
                        toHost = value;
                    }
                    handleCommand();
                    break;
                }
                case 0x04: {
                    toHost = (toHost & 0xFFFFFFFFL) | (value << 32);
                    handleCommand();
                    break;
                }

                case 0x40: {
                    if (sizeLog2 == Sizes.SIZE_32_LOG2) {
                        fromHost = (fromHost & ~0xFFFFFFFFL) | value;
                    } else {
                        fromHost = value;
                    }
                    break;
                }
                case 0x44: {
                    fromHost = (fromHost & 0xFFFFFFFFL) | (value << 32);
                    break;
                }
            }
        }

        protected void handleCommand() {
            if (toHost != 0) {
                final int exitcode = (int) (toHost >>> 1);
                if (exitcode != 0) {
                    fail("Test failed with exit code [" + exitcode + "].");
                } else {
                    throw new TestSuccessful();
                }
            }
        }
    }

    private static final class TestSuccessful extends RuntimeException {
    }
}
