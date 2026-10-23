package org.fahadmirza.geo.stata.dev;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.fahadmirza.geo.stata.dev.CheckRow.Verdict;

/**
 * Compares one stub class with the real class of the same name, member by member, using
 * reflection only (nothing is called). It has no Stata dependency, so CI tests it directly.
 *
 * <p>What counts as what:
 * <ul>
 *   <li>FAIL: a public stub member is missing from the real class, or its return type or
 *       static/instance kind differs. Code compiled against the stub would break in Stata.</li>
 *   <li>WARN: the member exists but the declared checked exceptions or class modifiers differ.
 *       Compiled code still runs, but error handling may be incomplete.</li>
 *   <li>INFO: facts recorded for later use, such as the values of the real constants.</li>
 * </ul>
 */
public final class SignatureComparer {

    private SignatureComparer() {
    }

    /**
     * Compares {@code stub} with {@code real}. Parameter types are resolved by name in
     * {@code realLoader}, so a stub parameter of type com.stata.sfi.StrLConnector is matched
     * with the real StrLConnector class.
     */
    public static List<CheckRow> compare(Class<?> stub, Class<?> real, ClassLoader realLoader) {
        List<CheckRow> rows = new ArrayList<>();
        String cn = stub.getName();
        rows.add(compareClass(cn, stub, real));

        if (stub.isEnum()) {
            rows.add(compareEnumConstants(cn, stub, real));
        }

        Constructor<?>[] ctors = stub.getConstructors();
        Arrays.sort(ctors, Comparator.comparing(SignatureComparer::describe));
        for (Constructor<?> c : ctors) {
            rows.add(compareConstructor(cn, c, real, realLoader));
        }

        List<Method> methods = Arrays.stream(stub.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .sorted(Comparator.comparing(SignatureComparer::describe))
                .collect(Collectors.toList());
        for (Method m : methods) {
            rows.add(compareMethod(cn, m, real, realLoader));
        }

        rows.addAll(realConstants(cn, real));
        return rows;
    }

    private static CheckRow compareClass(String cn, Class<?> stub, Class<?> real) {
        List<String> problems = new ArrayList<>();
        Verdict verdict = Verdict.PASS;

        String stubSuper = stub.getSuperclass() == null ? "none" : stub.getSuperclass().getName();
        String realSuper = real.getSuperclass() == null ? "none" : real.getSuperclass().getName();
        if (!stubSuper.equals(realSuper)) {
            verdict = Verdict.FAIL;
            problems.add("parent class differs: stub " + stubSuper + ", real " + realSuper);
        }
        Set<String> realIfaces = Arrays.stream(real.getInterfaces()).map(Class::getName)
                .collect(Collectors.toSet());
        for (Class<?> i : stub.getInterfaces()) {
            if (!realIfaces.contains(i.getName())) {
                verdict = Verdict.FAIL;
                problems.add("real class does not implement " + i.getName());
            }
        }
        if (stub.isEnum() != real.isEnum()) {
            verdict = Verdict.FAIL;
            problems.add("enum in one, not in the other");
        }
        if (verdict == Verdict.PASS
                && Modifier.isFinal(stub.getModifiers()) != Modifier.isFinal(real.getModifiers())) {
            verdict = Verdict.WARN;
            problems.add("final in one, not in the other");
        }
        String detail = problems.isEmpty()
                ? "same parent class and interfaces; real class loaded from " + location(real)
                : String.join("; ", problems);
        return new CheckRow(cn, "CLASS", Modifier.toString(real.getModifiers()) + " " + real.getName(),
                verdict, detail);
    }

    private static CheckRow compareEnumConstants(String cn, Class<?> stub, Class<?> real) {
        List<String> stubNames = Arrays.stream(stub.getEnumConstants())
                .map(e -> ((Enum<?>) e).name()).collect(Collectors.toList());
        List<String> realNames = real.isEnum()
                ? Arrays.stream(real.getEnumConstants()).map(e -> ((Enum<?>) e).name())
                        .collect(Collectors.toList())
                : List.of();
        if (stubNames.equals(realNames)) {
            return new CheckRow(cn, "ENUM", stubNames.size() + " constants", Verdict.PASS,
                    "same names in the same order");
        }
        List<String> missing = new ArrayList<>(stubNames);
        missing.removeAll(realNames);
        Verdict v = missing.isEmpty() ? Verdict.WARN : Verdict.FAIL;
        return new CheckRow(cn, "ENUM", stubNames.size() + " constants", v,
                "stub " + stubNames + " vs real " + realNames
                        + (missing.isEmpty() ? " (order or extras differ)" : "; missing in real: " + missing));
    }

    private static CheckRow compareConstructor(String cn, Constructor<?> c, Class<?> real, ClassLoader rl) {
        String sig = describe(c);
        Class<?>[] params;
        try {
            params = resolveAll(c.getParameterTypes(), rl);
        } catch (ClassNotFoundException e) {
            return new CheckRow(cn, "CONSTRUCTOR", sig, Verdict.FAIL,
                    "a parameter type does not exist in the real API: " + e.getMessage());
        }
        Constructor<?> rc;
        try {
            rc = real.getConstructor(params);
        } catch (NoSuchMethodException e) {
            return new CheckRow(cn, "CONSTRUCTOR", sig, Verdict.FAIL,
                    "no public constructor with these parameters in the real class");
        }
        return exceptionsVerdict(cn, "CONSTRUCTOR", sig, c, rc, "constructor matches");
    }

    private static CheckRow compareMethod(String cn, Method m, Class<?> real, ClassLoader rl) {
        String sig = describe(m);
        Class<?>[] params;
        try {
            params = resolveAll(m.getParameterTypes(), rl);
        } catch (ClassNotFoundException e) {
            return new CheckRow(cn, "METHOD", sig, Verdict.FAIL,
                    "a parameter type does not exist in the real API: " + e.getMessage());
        }
        Method rm;
        try {
            rm = real.getMethod(m.getName(), params);
        } catch (NoSuchMethodException e) {
            return new CheckRow(cn, "METHOD", sig, Verdict.FAIL,
                    "missing in the real API (no public method with this name and these parameters)");
        }
        if (!m.getReturnType().getName().equals(rm.getReturnType().getName())) {
            return new CheckRow(cn, "METHOD", sig, Verdict.FAIL,
                    "return type differs: stub " + m.getReturnType().getName()
                            + ", real " + rm.getReturnType().getName());
        }
        boolean stubStatic = Modifier.isStatic(m.getModifiers());
        boolean realStatic = Modifier.isStatic(rm.getModifiers());
        if (stubStatic != realStatic) {
            return new CheckRow(cn, "METHOD", sig, Verdict.FAIL,
                    "static in one, instance method in the other");
        }
        String annotations = annotationNames(rm);
        String ok = annotations.isEmpty() ? "matches" : "matches; real annotations: " + annotations;
        return exceptionsVerdict(cn, "METHOD", sig, m, rm, ok);
    }

    private static CheckRow exceptionsVerdict(String cn, String kind, String sig,
            Executable stub, Executable real, String okDetail) {
        Set<String> s = checkedExceptions(stub);
        Set<String> r = checkedExceptions(real);
        if (s.equals(r)) {
            return new CheckRow(cn, kind, sig, Verdict.PASS, okDetail);
        }
        return new CheckRow(cn, kind, sig, Verdict.WARN,
                "declared checked exceptions differ: stub " + s + ", real " + r);
    }

    /** Public static final fields of the real class, with their values, for the record. */
    private static List<CheckRow> realConstants(String cn, Class<?> real) {
        List<CheckRow> rows = new ArrayList<>();
        Field[] fields = real.getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        for (Field f : fields) {
            int mod = f.getModifiers();
            if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod)
                    && !f.isEnumConstant()
                    && (f.getType().isPrimitive() || f.getType() == String.class)) {
                String value;
                try {
                    value = String.valueOf(f.get(null));
                } catch (IllegalAccessException | RuntimeException e) {
                    value = "(could not read: " + e.getClass().getSimpleName() + ")";
                }
                rows.add(new CheckRow(cn, "CONSTANT", f.getType().getName() + " " + f.getName(),
                        Verdict.INFO, "real value " + value + " (not stubbed; read at run time)"));
            }
        }
        return rows;
    }

    // ----- helpers -----

    static Class<?>[] resolveAll(Class<?>[] types, ClassLoader loader) throws ClassNotFoundException {
        Class<?>[] out = new Class<?>[types.length];
        for (int i = 0; i < types.length; i++) {
            out[i] = types[i].isPrimitive() ? types[i] : Class.forName(types[i].getName(), false, loader);
        }
        return out;
    }

    private static Set<String> checkedExceptions(Executable e) {
        Set<String> out = new TreeSet<>();
        for (Class<?> x : e.getExceptionTypes()) {
            if (!RuntimeException.class.isAssignableFrom(x) && !Error.class.isAssignableFrom(x)) {
                out.add(x.getName());
            }
        }
        return out;
    }

    private static String annotationNames(Method m) {
        return Arrays.stream(m.getAnnotations())
                .map(Annotation::annotationType).map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
    }

    static String describe(Executable e) {
        String params = Arrays.stream(e.getParameterTypes()).map(Class::getTypeName)
                .map(SignatureComparer::shortType).collect(Collectors.joining(", "));
        String throwsPart = e.getExceptionTypes().length == 0 ? ""
                : " throws " + Arrays.stream(e.getExceptionTypes()).map(Class::getSimpleName)
                        .collect(Collectors.joining(", "));
        if (e instanceof Method m) {
            String st = Modifier.isStatic(m.getModifiers()) ? "static " : "";
            return st + shortType(m.getReturnType().getTypeName()) + " " + m.getName()
                    + "(" + params + ")" + throwsPart;
        }
        return "new " + e.getDeclaringClass().getSimpleName() + "(" + params + ")" + throwsPart;
    }

    private static String shortType(String typeName) {
        // "java.lang.String" -> "String", "com.stata.sfi.Missing$Extended" -> "Missing.Extended"
        String t = typeName.replace('$', '.');
        for (String pkg : new String[] {"java.lang.", "java.io.", "com.stata.sfi."}) {
            if (t.startsWith(pkg) && t.indexOf('.', pkg.length()) < 0) {
                return t.substring(pkg.length());
            }
            if (t.startsWith("com.stata.sfi.")) {
                return t.substring("com.stata.sfi.".length());
            }
        }
        return t;
    }

    private static String location(Class<?> c) {
        try {
            var cs = c.getProtectionDomain().getCodeSource();
            return cs == null || cs.getLocation() == null ? "(Java runtime)" : cs.getLocation().toString();
        } catch (SecurityException e) {
            return "(unknown)";
        }
    }
}
