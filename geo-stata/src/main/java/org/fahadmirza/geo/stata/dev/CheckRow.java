package org.fahadmirza.geo.stata.dev;

/**
 * One line of the stub-check report: which member was checked, and the verdict.
 *
 * @param className fully qualified class name, for example com.stata.sfi.Data
 * @param kind      CLASS, CONSTRUCTOR, METHOD, ENUM or CONSTANT
 * @param member    readable signature, for example "double getNum(int, long)"
 * @param verdict   PASS, FAIL, WARN or INFO
 * @param detail    plain-language explanation
 */
public record CheckRow(String className, String kind, String member, Verdict verdict, String detail) {

    /** PASS: identical. FAIL: compiled code would break. WARN: works but differs. INFO: for the record. */
    public enum Verdict { PASS, FAIL, WARN, INFO }

    /**
     * Tab-separated line. Tabs and line breaks are removed, and "$" (Java's nested-class marker, as
     * in Missing$Extended) becomes "." because Stata would read "$Extended" as a global macro.
     */
    public String toTsv() {
        return String.join("\t", clean(shortName()), kind, clean(member), verdict.name(), clean(detail));
    }

    /** Class name without the package, for example "Data" or "Missing$Extended". */
    public String shortName() {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ')
                .replace('$', '.').replace('`', '\'');
    }
}
