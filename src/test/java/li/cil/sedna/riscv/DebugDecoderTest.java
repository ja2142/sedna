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
            // TODO args
            Arguments.of(0x2000, "FLD", emptyArgs),
            Arguments.of(0x2200, "FLD", emptyArgs)
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
