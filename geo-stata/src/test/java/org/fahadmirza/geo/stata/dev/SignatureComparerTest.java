package org.fahadmirza.geo.stata.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.fahadmirza.geo.stata.dev.CheckRow.Verdict;
import org.junit.jupiter.api.Test;

/** Proves the comparer gives the right verdict for matches and for each kind of mismatch. */
class SignatureComparerTest {

    // Two small classes standing in for "stub" and "real".
    public static class StubSide {
        public StubSide(int x) { }
        public static int same(int a, long b) { return 0; }
        public static int wrongReturn(String s) { return 0; }
        public int wrongKind() { return 0; }
        public static void onlyInStub() { }
        public static void exceptions() { }
    }

    public static class RealSide {
        public static final int ANSWER = 42;
        public RealSide(int x) { }
        public static int same(int a, long b) { return 0; }
        public static String wrongReturn(String s) { return s; }
        public static int wrongKind() { return 0; }
        public static void exceptions() throws IOException { }
    }

    @Test
    void verdictsMatchTheRules() {
        List<CheckRow> rows = SignatureComparer.compare(StubSide.class, RealSide.class,
                getClass().getClassLoader());
        Map<String, CheckRow> byMember = rows.stream()
                .collect(Collectors.toMap(CheckRow::member, Function.identity(), (a, b) -> a));

        assertEquals(Verdict.PASS, find(byMember, "same(").verdict());
        assertEquals(Verdict.FAIL, find(byMember, "wrongReturn(").verdict());
        assertEquals(Verdict.FAIL, find(byMember, "wrongKind(").verdict());
        assertEquals(Verdict.FAIL, find(byMember, "onlyInStub(").verdict());
        assertEquals(Verdict.WARN, find(byMember, "exceptions(").verdict());
        assertEquals(Verdict.PASS, find(byMember, "new StubSide(int)").verdict());
        assertEquals(Verdict.INFO, find(byMember, "ANSWER").verdict());
    }

    @Test
    void stubClassComparedWithItselfPassesEverywhere() throws Exception {
        // The real Data stub on the test classpath, compared with itself: every item must pass.
        Class<?> data = Class.forName("com.stata.sfi.Data");
        List<CheckRow> rows = SignatureComparer.compare(data, data, getClass().getClassLoader());
        assertTrue(rows.size() > 40, "expected the full Data stub, got " + rows.size() + " rows");
        assertTrue(rows.stream().allMatch(r -> r.verdict() == Verdict.PASS),
                "self-comparison produced a non-PASS row: " + rows);
    }

    private static CheckRow find(Map<String, CheckRow> rows, String fragment) {
        return rows.entrySet().stream().filter(e -> e.getKey().contains(fragment))
                .map(Map.Entry::getValue).findFirst()
                .orElseThrow(() -> new AssertionError("no row containing " + fragment + " in " + rows.keySet()));
    }
}
