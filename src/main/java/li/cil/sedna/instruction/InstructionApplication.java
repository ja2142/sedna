package li.cil.sedna.instruction;

import java.util.ArrayList;
import java.util.Collection;

// a specific instruction decoded from some opcode
// e.g. ADDI sp, sp, 16
public class InstructionApplication {
    public static InstructionApplication fromOpcode(InstructionDeclaration declaration, int opcode) {
        Collection<Integer> args = new ArrayList<>();
        declaration.arguments.forEach(
            (k, v) -> args.add(v.get(opcode))
        );
        return new InstructionApplication(declaration.name, args);
    }

    InstructionApplication(String name, Collection<Integer> args) {
        this.name = name;
        this.args = args;
    }

    public final String name;
    public final Collection<Integer> args;
}
