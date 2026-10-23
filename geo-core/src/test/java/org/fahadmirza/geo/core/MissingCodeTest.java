package org.fahadmirza.geo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MissingCodeTest {

    @Test
    void conventionMatchesFoundationSpecification() {
        assertEquals(".a", MissingCode.NO_VALID_PIXELS.stataDisplay());
        assertEquals(".b", MissingCode.OUTSIDE_RASTER.stataDisplay());
        assertEquals(".c", MissingCode.INVALID_GEOMETRY.stataDisplay());
        assertEquals(".", MissingCode.OTHER.stataDisplay());
    }

    @Test
    void lettersAreDistinct() {
        long distinct = java.util.Arrays.stream(MissingCode.values())
                .map(MissingCode::stataLetter).distinct().count();
        assertEquals(MissingCode.values().length, distinct);
    }
}
