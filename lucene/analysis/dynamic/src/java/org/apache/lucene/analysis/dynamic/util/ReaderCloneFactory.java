package org.apache.lucene.analysis.dynamic.util;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Duplicates {@link Reader}s in order to feed multiple consumers.
 * <p>
 * This class registers multiple implementations, and tries to resolve which one to use,
 * looking at the actual class of the Reader to clone, and matching with the most bond
 * handled classes for each {@link ReaderCloner} implementation.
 * <p>
 * By default, a few {@link Reader} implementations are handled, including the
 * most used inside Lucene ({@link StringReader}), and a default, fallback implementation
 * that merely reads all the available content, and creates a String out of it.
 * <p>
 * Therefore you should understand the importance of having a proper implementation for
 * any optimizable {@link Reader}. For instance, {@link StringReaderCloner} gains access
 * to the underlying String in order to avoid copies. A generic BufferedReader
 */
public class ReaderCloneFactory {
    /**
     * Interface for a utility class, able to clone the content of a {@link java.io.Reader},
     * possibly in an optimized way (such as gaining access to a package private field,
     * or through reflection using {@link java.lang.reflect.Field#setAccessible(boolean)}).
     */
    public interface ReaderCloner {

        /**
         * Initialize or reinitialize the cloner with the given reader.
         * The implementing class should have a default no arguments constructor.
         * <P><B>Remark:</B> The given Reader is now controlled by this ReaderCloner, it may
         * be closed during a call to this method, or it may be returned
         * at first call to {@link #giveAClone()}.
         *
         * @see #giveAClone()
         */
        void init(Reader originalReader, String expectedPrefix) throws IOException;

        /**
         * Returns a new {@link Reader}.
         * <P><B>Remark:</B> The returned Reader should be closed.
         * The original Reader, if not consumed by the init method,
         * should be returned at first call. Therefore it is important to
         * call this method at least once, or to be prepared to face possible
         * exceptions when closing the original Reader.
         */
        Reader giveAClone();

        String getValueAfterPrefix();
    }

    public interface InstanceCreator {

        ReaderCloner newInstance();
    }

    /**
     * Map storing the mapping between a handled class and a handling class, for {@link ReaderCloner}s
     */
    private static final ConcurrentMap<Class<?>, InstanceCreator> typeMap =
            new ConcurrentHashMap<>();

    /**
     * Add the association between a (handled) class and its handling {@link ReaderCloner}.
     *
     * @param handledClass The base class that is handled by clonerImplClass.
     *                     Using this parameter, you can further restrict the usage of a more generic cloner.
     * @param factory      factory for creating instance of the class
     */
    public static void bindCloner(Class<?> handledClass, InstanceCreator factory) {
        InstanceCreator existing = typeMap.put(handledClass, factory);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate cloned for class : " + handledClass.getName());
        }
    }

    static {
        // General purpose Reader handling
        bindCloner(Reader.class, new ReaderClonerDefaultImpl.Factory());
        ReusableStringReaderCloner.registerCloner();
        bindCloner(CharArrayReader.class, new CharArrayReaderCloner.Factory());
        bindCloner(StringReader.class, new StringReaderCloner.Factory());
    }

    /**
     * Returns the ReaderCloner associated with the exact given class.
     *
     * @param forClass The handled class bond to the ReaderCloner to return.
     * @return The bond ReaderCloner, or null.
     */
    public static ReaderCloner getClonerStrict(Class<?> forClass) {
        InstanceCreator factory = typeMap.get(forClass);
        if (factory != null) {
            try {
                return factory.newInstance();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * (Advanced) Returns an initialized ReaderCloner, associated with the given base class, for the given Reader.
     * If the initialization fails, this function returns null.
     * The function first tries to match the exact class of forReader, and initialize the ReaderCloner.
     * If no ReaderCloner or (tested second) ReaderUnwrapper matches, the resolution continues with the super class,
     * until the baseClass is reached, and tested.
     * If this process is not successful, <code>null</code> is returned.
     *
     * @param forClass  The class to start with, should be the class of forReader (but the latter can be null, hence this parameter)
     * @param forReader The Reader instance to return and initialize a ReaderCloner for. Can be null.
     * @return An initialized ReaderCloner suitable for the givenReader, or null.
     */
    public static ReaderCloner getCloner(Class<?> forClass, final Reader forReader, String expectedPrefix) {
        // Loop through each super class
        while (forClass != null) {
            // Try first a matching cloner
            ReaderCloner cloner = ReaderCloneFactory.getClonerStrict(forClass);
            if (cloner != null) {
                if (forReader != null) {
                    try {
                        cloner.init(forReader, expectedPrefix);
                    } catch (Exception e) {
                        cloner = null;
                    }
                }
                if (cloner != null) {
                    return cloner;
                }
            }
            // Continue resolution with super class...
            Class<?> clazz = forClass.getSuperclass();
            // ... checking ancestry with the given base class
            if (Reader.class.isAssignableFrom(clazz)) {
                //noinspection unchecked
                forClass = clazz;
            } else {
                forClass = null;
            }
        }
        return null;
    }

    public static <S extends Reader> ReaderCloner getCloner(S forReader, String expectedPrefix) {
        if (forReader != null) {
            Class<? extends Reader> clz = forReader.getClass();
            return ReaderCloneFactory.getCloner(clz, forReader, expectedPrefix);
        } else {
            return ReaderCloneFactory.getGenericCloner();
        }
    }

    /**
     * Returns a {@link ReaderCloner} suitable for any {@link java.io.Reader} instance.
     */
    public static <S extends Reader> ReaderCloner getGenericCloner() {
        return ReaderCloneFactory.getCloner(Reader.class, null, null);
    }

    public static Tuple<String, String> stripPrefixIfPresent(String content, String expectedPrefix) {
        if (expectedPrefix == null) {
            return Tuple.of(null, content);
        }

        if (expectedPrefix.charAt(expectedPrefix.length() - 1) != '[') {
            expectedPrefix = expectedPrefix + "[";
        }

        if (!content.startsWith(expectedPrefix)) {
            return Tuple.of(null, content);
        }

        int i = content.indexOf("]");
        if (i <= 0) {
            return Tuple.of(null, content);
        } else {
            return Tuple.of(content.substring(expectedPrefix.length(), i), content.substring(i + 1));
        }
    }

    record Tuple<S, T>(S v1, T v2) {

        static <A, B> Tuple<A, B> of(A v1, B v2) {
            return new Tuple<>(v1, v2);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Tuple<?, ?> tuple = (Tuple<?, ?>) o;

            if (!Objects.equals(v1, tuple.v1)) {
                return false;
            }
            return Objects.equals(v2, tuple.v2);
        }

        @Override
        public int hashCode() {
            int result = v1 != null ? v1.hashCode() : 0;
            result = 31 * result + (v2 != null ? v2.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Tuple{" +
                    "v1=" + v1 +
                    ", v2=" + v2 +
                    '}';
        }
    }
}