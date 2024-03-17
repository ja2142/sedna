package li.cil.sedna.instruction;

import java.util.ArrayList;
import java.util.Collection;

// a specific instruction decoded from some opcode
// e.g. ADDI sp, sp, 16
public class InstructionApplication {
    static final Collection<Integer> emptyArgs = new ArrayList<>();

    public static InstructionApplication fromOpcode(InstructionDeclaration declaration, int opcode) {
        return new InstructionApplication(declaration.name, emptyArgs);
    }

    InstructionApplication(String name, Collection<Integer> args) {
        this.name = name;
        this.args = args;
    }

    public final String name;
    public final Collection<Integer> args;
}
