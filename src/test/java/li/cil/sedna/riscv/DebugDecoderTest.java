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
    @ValueSource(ints={0, })
    void testDecodeInvalidInstruction(int opcode) {
        assertThrows(R5IllegalInstructionException.class, () -> { debugDecoder.decode(opcode); });
    }

    private static Stream<Arguments> makeTestDecodeValidArgs() {
        Collection<Integer> emptyArgs = new ArrayList<>();
        return Stream.of(
            Arguments.of(0x2000, "FLD", emptyArgs),
            Arguments.of(0x2200, "FLD", emptyArgs)
        );
    }

    @ParameterizedTest
    @MethodSource("makeTestDecodeValidArgs")
    void testDecodeValidInstruction(int opcode, String expectedInstructionName, Collection<Integer> args) {
        var decoded = debugDecoder.decode(opcode);
        assertEquals(decoded.name, expectedInstructionName);
        // TODO args
    }
}
