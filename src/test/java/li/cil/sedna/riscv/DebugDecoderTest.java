package li.cil.sedna.riscv;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import li.cil.sedna.instruction.decoder.DebugDecoderTreeVisitor;
import li.cil.sedna.riscv.exception.R5IllegalInstructionException;

public class DebugDecoderTest {

    DebugDecoderTreeVisitor debugDecoder32;
    DebugDecoderTreeVisitor debugDecoder64;

    public DebugDecoderTest() {
        this.debugDecoder64 = new DebugDecoderTreeVisitor();
        R5Instructions.RV64.getDecoderTree().accept(this.debugDecoder64);
        this.debugDecoder32 = new DebugDecoderTreeVisitor();
        R5Instructions.RV32.getDecoderTree().accept(this.debugDecoder32);
    }

    @ParameterizedTest
    @ValueSource(ints={0, 0x1c, 0x14, 0x6101, 0x402e, 0x504e, 0x704e, 0x8002})
    void testDecoderv64InvalidInstruction(int opcode) {
        assertThrows(R5IllegalInstructionException.class, () -> { debugDecoder64.decode(opcode); });
    }

    @ParameterizedTest
    @ValueSource(ints={0, 0x1c, 0x14, 0x6101, 0x402e, 0x504e, 0x8002, 0x02051513})
    void testDecoderv32InvalidInstruction(int opcode) {
        assertThrows(R5IllegalInstructionException.class, () -> { debugDecoder32.decode(opcode); });
    }
    
    private static Stream<Arguments> makeTestDecoderv64ValidArgs() {
        Integer[] emptyArgs = new Integer[]{};
        return Stream.of(
            // some base I instructions
            Arguments.of(0x510133, "ADD", new Integer[]{2,2,5}),
            Arguments.of(0x407352b3, "SRA", new Integer[]{5,6,7}),
            Arguments.of(0x5e8f0f93, "ADDI", new Integer[]{31, 30, 0x5e8}),
            Arguments.of(0x73, "ECALL", emptyArgs),
            Arguments.of(0x100073, "EBREAK", emptyArgs),
            Arguments.of(0xc3060723, "SB", new Integer[]{12, 16, -0x3d2}),
            Arguments.of(0x1b44d303, "LHU", new Integer[]{6, 9, 0x1b4}),
            Arguments.of(0x8e050e63, "BEQ", new Integer[]{10, 0, -0xf04}),
            Arguments.of(0x707951e3, "BGE", new Integer[]{18, 7, 0xf02}),
            Arguments.of(0x1682e0ef, "JAL", new Integer[]{1, 0x2e168}),
            Arguments.of(0xa35ec437, "LUI", new Integer[]{8, 0xa35ec000}),
            Arguments.of(0x3e29313, "SLLI", new Integer[]{6, 5, 62}),
            Arguments.of(0xf1103473, "CSRRC", new Integer[]{8, 0, 0xf11}),
            // those are the same instructions, with only difference in unused bits
            Arguments.of(0xb533af, "AMOADD.D", new Integer[]{7, 10, 11}),
            Arguments.of(0x2b533af, "AMOADD.D", new Integer[]{7, 10, 11}),
            Arguments.of(0x6b533af, "AMOADD.D", new Integer[]{7, 10, 11}),
            Arguments.of(0x18208043, "FMADD.S", new Integer[]{0, 1, 2, 3, 0}),
            Arguments.of(0xd24, "ADDI", new Integer[]{9, 2, 0x298}), // C.ADDI4SPN
            Arguments.of(0x715d, "ADDI", new Integer[]{2, 2, -0x50}), // C.ADDI16SP
            Arguments.of(0x5944, "LW", new Integer[]{9, 10, 0x34}), // C.LW
            Arguments.of(0x7944, "LD", new Integer[]{9, 10, 0xb0}) // C.LD
        ).map((args) -> Arguments.of(args.get()[0], args.get()[1], Arrays.asList((Integer[])args.get()[2])));
    }

    @ParameterizedTest
    @MethodSource("makeTestDecoderv64ValidArgs")
    void testDecoderv64ValidInstruction(int opcode, String expectedInstructionName, Collection<Integer> expectedArgs) throws R5IllegalInstructionException {
        var decoded = debugDecoder64.decode(opcode);
        assertEquals(decoded.name, expectedInstructionName);
        assertIterableEquals(decoded.args, expectedArgs);
    }
}
