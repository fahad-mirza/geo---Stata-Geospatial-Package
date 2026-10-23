package org.fahadmirza.geo.stata.dev;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Enforces the stub rule: no compile-time constants in sfi-stub. Java copies a constant's value
 * into the code that uses it, so a wrong value in a stub would ship inside geo.jar unnoticed.
 */
class StubRulesTest {
    private static final String[] STUB_CLASSES = {
        "com.stata.sfi.Data", "com.stata.sfi.Frame", "com.stata.sfi.Missing",
        "com.stata.sfi.Missing$Extended", "com.stata.sfi.SFIToolkit", "com.stata.sfi.StrLConnector",
        "com.stata.sfi.SFIException", "com.stata.sfi.FrameException",
    };

    @Test
    void stubsDeclareNoCompileTimeConstants() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String name : STUB_CLASSES) {
            for (Field f : Class.forName(name).getDeclaredFields()) {
                int m = f.getModifiers();
                boolean constant = Modifier.isStatic(m) && Modifier.isFinal(m) && Modifier.isPublic(m)
                        && (f.getType().isPrimitive() || f.getType() == String.class);
                if (constant) {
                    offenders.add(name + "." + f.getName());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "constants found in sfi-stub: " + offenders);
    }
}
