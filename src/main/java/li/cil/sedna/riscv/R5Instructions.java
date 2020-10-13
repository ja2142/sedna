package li.cil.sedna.riscv;

import li.cil.sedna.instruction.InstructionDeclaration;
import li.cil.sedna.instruction.InstructionDeclarationLoader;
import li.cil.sedna.instruction.InstructionDefinition;
import li.cil.sedna.instruction.InstructionDefinitionLoader;
import li.cil.sedna.instruction.decoder.AbstractDecoderTreeNode;
import li.cil.sedna.instruction.decoder.DecoderTree;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

public final class R5Instructions {
    static final Logger LOGGER = LogManager.getLogger();

    static final String RISCV_INSTRUCTIONS_FILE = "/riscv/instructions.txt";
    static final ArrayList<InstructionDeclaration> DECLARATIONS = new ArrayList<>();
    private static final HashMap<InstructionDeclaration, InstructionDefinition> DEFINITIONS = new HashMap<>();
    static final AbstractDecoderTreeNode DECODER;

    static {
        try (final InputStream stream = R5Instructions.class.getResourceAsStream(RISCV_INSTRUCTIONS_FILE)) {
            if (stream == null) {
                throw new IOException("File not found.");
            }
            DECLARATIONS.addAll(InstructionDeclarationLoader.load(stream));
        } catch (final Throwable e) {
            LOGGER.error("Failed loading RISC-V instruction declarations.", e);
        }

        try {
            DEFINITIONS.putAll(InstructionDefinitionLoader.load(R5CPUTemplate.class, DECLARATIONS));
        } catch (final Throwable e) {
            LOGGER.error("Failed loading RISC-V instruction definitions.", e);
        }

        DECODER = DecoderTree.create(DECLARATIONS);
    }

    public static ArrayList<InstructionDeclaration> getDeclarations() {
        return DECLARATIONS;
    }

    @Nullable
    public static InstructionDeclaration findDeclaration(final int instruction) {
        return DECODER.query(instruction);
    }

    @Nullable
    public static InstructionDefinition getDefinition(final InstructionDeclaration declaration) {
        return DEFINITIONS.get(declaration);
    }
}