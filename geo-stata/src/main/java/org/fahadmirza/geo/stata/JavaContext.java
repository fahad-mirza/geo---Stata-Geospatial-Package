package org.fahadmirza.geo.stata;

import java.util.concurrent.Callable;

/**
 * Runs code with the thread's "context classloader" set to the classloader that loaded geo.jar.
 *
 * <p>Why: libraries such as GeoTools, ImageIO and the units-of-measure library discover their
 * plugins through Java's ServiceLoader, which by default searches the context classloader. When
 * Stata calls geo through javacall, that loader is chosen by Stata and may not see the inside of
 * geo.jar, so plugins would silently go missing. Every javacall entry point that uses a library
 * goes through {@link #call(Callable)}; the previous loader is always restored afterwards.
 */
public final class JavaContext {

    private JavaContext() {
    }

    /** The classloader that loaded geo.jar. */
    public static ClassLoader geoLoader() {
        return JavaContext.class.getClassLoader();
    }

    /** Runs {@code body} with geo's classloader as the context classloader. */
    public static <T> T call(Callable<T> body) throws Exception {
        Thread t = Thread.currentThread();
        ClassLoader previous = t.getContextClassLoader();
        t.setContextClassLoader(geoLoader());
        try {
            return body.call();
        } finally {
            t.setContextClassLoader(previous);
        }
    }
}
