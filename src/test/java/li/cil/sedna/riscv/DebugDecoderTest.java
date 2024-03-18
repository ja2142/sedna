package li.cil.sedna.riscv;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import li.cil.sedna.instruction.decoder.DebugDecoderTreeVisitor;
import li.cil.sedna.riscv.exception.R5IllegalInstructionException;

public class DebugDecoderTest {

    DebugDecoderTreeVisitor debugDecoder;

    public DebugDecoderTest() {
        this.debugDecoder = new DebugDecoderTreeVisitor();
        R5Instructions.getDecoderTree().accept(this.debugDecoder);
    }

    @ParameterizedTest
    @ValueSource(ints={0, 0x1c, 0x14, 0x6101, 0x402e, 0x504e, 0x704e, 0x8002})
    void testDecodeInvalidInstruction(int opcode) {
        assertThrows(R5IllegalInstructionException.class, () -> { debugDecoder.decode(opcode); });
    }

    private static Stream<Arguments> makeTestDecodeValidArgs() {
        Collection<Integer> emptyArgs = new ArrayList<>();
        return Stream.of(
            // some base I instructions
            Arguments.of(0x510133, "ADD", new int[]{2,2,5}),
            Arguments.of(0x407352b3, "SRA", new int[]{5,6,7}),
            Arguments.of(0x5e8f0f93, "ADDI", new int[]{31, 30, 0x5e8}),
            Arguments.of(0x73, "ECALL", emptyArgs),
            Arguments.of(0x100073, "EBREAK", emptyArgs),
            Arguments.of(0xc3060723, "SB", new int[]{12, 16, 0xc2e}),
            Arguments.of(0x1b44d303, "LHU", new int[]{6, 9, 0x1b4}),
            Arguments.of(0x8e050e63, "BEQ", new int[]{10, 0, 0x87e}),
            Arguments.of(0x707951e3, "BGE", new int[]{18, 7, 0x781}),
            Arguments.of(0x1682e0ef, "JAL", new int[]{1, 0x170b4}),
            Arguments.of(0xa35ec437, "LUI", new int[]{8, 0xa35ec000})
        );
    }

    @ParameterizedTest
    @MethodSource("makeTestDecodeValidArgs")
    void testDecodeValidInstruction(int opcode, String expectedInstructionName, Collection<Integer> expectedArgs) throws R5IllegalInstructionException {
        var decoded = debugDecoder.decode(opcode);
        assertEquals(decoded.name, expectedInstructionName);
        assertEquals(decoded.args, expectedArgs);
    }
}
