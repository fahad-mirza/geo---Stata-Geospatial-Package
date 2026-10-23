package org.fahadmirza.geo.core;

/**
 * Why a result is missing. The engine records a reason, and the Stata layer (geo-stata) turns it
 * into Stata's extended missing values. This class has no Stata dependency, so engine code and
 * its tests can use it on any platform.
 *
 * <p>Convention fixed in the foundation specification:
 * <ul>
 *   <li>{@link #NO_VALID_PIXELS} is stored as .a: the zone overlaps the raster but every cell is nodata;</li>
 *   <li>{@link #OUTSIDE_RASTER} is stored as .b: the zone or point lies outside the raster;</li>
 *   <li>{@link #INVALID_GEOMETRY} is stored as .c: the geometry is invalid or empty;</li>
 *   <li>{@link #OTHER} is stored as . (system missing): anything else.</li>
 * </ul>
 */
public enum MissingCode {
    NO_VALID_PIXELS("a", "no valid pixels"),
    OUTSIDE_RASTER("b", "outside the raster"),
    INVALID_GEOMETRY("c", "invalid or empty geometry"),
    OTHER("", "missing for another reason");

    private final String stataLetter;
    private final String meaning;

    MissingCode(String stataLetter, String meaning) {
        this.stataLetter = stataLetter;
        this.meaning = meaning;
    }

    /**
     * The letter of the Stata extended missing value ("a" for .a), or "" for system missing.
     * geo-stata maps this to {@code Missing.Extended.valueOf(letter)}, or {@code _sys} when empty.
     */
    public String stataLetter() {
        return stataLetter;
    }

    /** How Stata displays the value: ".a", ".b", ".c" or ".". */
    public String stataDisplay() {
        return "." + stataLetter;
    }

    /** Plain-language meaning, for help files and messages. */
    public String meaning() {
        return meaning;
    }
}
