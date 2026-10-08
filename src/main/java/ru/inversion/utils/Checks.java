package ru.inversion.utils;

import java.util.Objects;
import java.util.regex.Pattern;

public final class Checks {

    private Checks() {
    }

    public static final class Require {

        private Require() {
        }

        public static <T> T object(T v, String field) {
            if (v == null)
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'" + field + "' is null");
            return v;
        }

        /** */
        public static void objects( Object ... objects )
        {
            if( objects == null || objects.length == 0 )
                return;

            if( objects.length == 1 )
            {
                if( objects[0] == null )
                    throw new NullPointerException();

                return;
            }

            for( int i = 0; i < objects.length; i = i + 2 )
            {
                 if( objects[i] == null )
                 {
                     if( i == objects.length - 1 || objects[i+1] == null )
                         throw new NullPointerException();

                    throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'" + objects[i+1].toString() + "' is null");
                 }
            }
        }

        public static String text(String v, String field) {
            if (v == null || v.trim().isEmpty())
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'" +field + "' is blank");
            return v;
        }

        public static char[] chars(char[] v, String field) {
            if (v == null || v.length == 0)
                throw new IllegalArgumentException(Tags.PRODUCT_LABEL + "'" +field + "' is empty");
            return v;
        }

        public static byte[] bytes(byte[] v, String field) {
            if (v == null || v.length == 0)
                throw new IllegalArgumentException(Tags.PRODUCT_LABEL + "'" +field + "' is empty");
            return v;
        }
    }

    public static final class Length {
        private Length() {
        }

        public static String min(String v, int min, String field) {
            if (v != null && v.length() < min)
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'" +field + "' length is less than " + min);
            return v;
        }

        public static String max(String v, int max, String field) {
            if (v != null && v.length() > max)
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'" +field + "' length is greater than " + max);
            return v;
        }

        public static char[] min(char[] v, int min, String field) {
            if (v != null && v.length < min)
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'" +field + "' length is less than " + min);
            return v;
        }

        public static char[] max(char[] v, int max, String field) {
            if (v != null && v.length > max)
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'" +field + "' length is greater than " + max);
            return v;
        }
    }

    public static final class Match {
        private Match() {
        }

        public static String regex(String v, Pattern pattern, String field) {
            if (v != null && pattern != null && !pattern.matcher(v).matches())
                throw new IllegalArgumentException(Tags.PRODUCT_LABEL + "'" +field + "' has invalid format");
            return v;
        }
    }

    public static final class Numeric {

        private Numeric() {
        }

        /** */
        public static int positive( int v, String field ) {
            if( v <= 0)
                throw new IllegalStateException( field + " must be positive" );
            return v;
        }

        /** */
        public static int positiveOrZero( int v, String field ) {
            if( v < 0)
                throw new IllegalStateException( field + " must be positive or zero" );
            return v;
        }

        /** */
        public static long positive( long v, String field ) {
            if( v <= 0L)
                throw new IllegalStateException( field + " must be positive" );
            return v;
        }

        /** */
        public static long positiveOrZero( long v, String field ) {
            if( v < 0L )
                throw new IllegalStateException( field + " must be positive or zero" );
            return v;
        }

        /** */
        public static Number positive( Number v, String field ) {
            if( v == null || v.longValue() <= 0L )
                throw new IllegalStateException( field + " must be positive" );
            return v;
        }

        /** */
        public static Number positiveOrZero( Number v, String field ) {
            if( v == null || v.longValue() < 0L )
                throw new IllegalStateException( field + " must be positive or zero" );
            return v;
        }

    }

}