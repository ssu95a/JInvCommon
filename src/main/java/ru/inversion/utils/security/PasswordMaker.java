package ru.inversion.utils.security;

import ru.inversion.utils.bits.Bits8;
import java.security.SecureRandom;

public class PasswordMaker {

    public enum CaseMode {
        Lower, Upper, Mixed
    }

    private final static char[] UPPER_LAT = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private final static char[] LOWER_LAT = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private final static char[] UPPER_RUS = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ".toCharArray();
    private final static char[] LOWER_RUS = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя".toCharArray();
    private final static char[] DIGITS    = "0123456789".toCharArray();
    private final static char[] SPECIAL   = "!@#$%^&*()_+-=[]{}|;:,.<>?".toCharArray();

    private final static char[] UPPER_LAT_NO_AMBIG = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray(); // без I,O
    private final static char[] LOWER_LAT_NO_AMBIG = "abcdefghjkmnpqrstuvwxyz".toCharArray(); // без i,l,o
    private final static char[] DIGITS_NO_AMBIG    = "23456789".toCharArray();

    private final static SecureRandom random = new SecureRandom();

    final private Bits8 flags;
    private int   length = 30; // default

    /** */
    private PasswordMaker()
    {
        // 0 - rus
        // 1 - special
        // 2 - digit
        // 3 - upper
        // 4 - lower
        flags = new Bits8("01111"); // default
    }

    private void change()
    {
        arrays      = null;
        totalLength = 0;
        groupsCount = 0;
    }

    /** */
    public PasswordMaker useRussian()
    { return useRussian(true);}
    public PasswordMaker useRussian(boolean v)
    { flags.set(0,v); change(); return this;}
    /** */
    public PasswordMaker useDigits()
    { return useDigits(true);}
    public PasswordMaker useDigits(boolean v)
    { flags.set(2,v); change(); return this;}

    /** */
    public PasswordMaker useSpecial()
    { return useSpecial(true);}
    public PasswordMaker useSpecial(boolean v)
    { flags.set(1,v); change(); return this;}

    /** */
    public PasswordMaker excludeAmbiguous(boolean v) { flags.set(5, v); change(); return this; }
    public PasswordMaker excludeAmbiguous() { return excludeAmbiguous(true); }

    /** */
    public PasswordMaker caseMode( CaseMode cm )
    {
        switch( cm )
        {
            case Lower:
                flags.set( 3,false);
                flags.set( 4,true );
            break;
            case Upper:
                flags.set(4,false);
                flags.set(3,true );
            break;
            case Mixed:
                flags.set(3);
                flags.set(4);
            break;
        }
        change();
        return this;
    }

    /** */
    public PasswordMaker length(int l)
    { length = l; change(); return this; }

    private transient char[][] arrays = null;
    private transient int totalLength = 0;
    private transient int groupsCount = 0;

    /** */
    public synchronized char[] make()
    {
        if( length < 5 )
            throw new IllegalArgumentException("Длина пароля должна быть не менее 5 символов");

        if( arrays == null )
        {
            int i = 0;

            arrays = new char[6][];
            totalLength = 0;

            boolean noAmbig = flags.get(5);

            if( flags.get(0) )
            {
                if( flags.get(3) ) arrays[i++] = UPPER_RUS;
                if( flags.get(4) ) arrays[i++] = LOWER_RUS;
            }

            if( flags.get(1) ) arrays[i++] = SPECIAL;

            if( flags.get(2) ) arrays[i++] = noAmbig ? DIGITS_NO_AMBIG : DIGITS;
            if( flags.get(3) ) arrays[i++] = noAmbig ? UPPER_LAT_NO_AMBIG : UPPER_LAT;
            if( flags.get(4) ) arrays[i++] = noAmbig ? LOWER_LAT_NO_AMBIG : LOWER_LAT;

            for( int j = 0; j < i; j++ ) {
                 totalLength += arrays[j].length;
            }

            groupsCount = i;
        }

        if( groupsCount == 0 )
            throw new IllegalStateException("Не выбраны наборы символов для генерации пароля");

        if( groupsCount > length )
            throw new IllegalArgumentException("Длина пароля меньше количества обязательных групп: " + groupsCount );

        char[] password = new char[length];

        for( int j = 0; j < groupsCount; j++ ) {
            // кладем по одному символу каждого типа
            password[j]  = arrays[j][random.nextInt( arrays[j].length) ];
        }

        for( int pos = groupsCount; pos < length; pos++ )
        {
            int index = random.nextInt(totalLength);

            for( int j = 0; j < groupsCount; j++ )
            {
                char[] arr = arrays[j];

                if( arr.length == 0 )
                    throw new IllegalStateException("Empty charset group (bug)");

                if( index < arr.length )
                {
                    password[pos] = arr[index];
                    break;
                }

                index -= arr.length;
            }
        }

        // shuffle
        char tmp;
        int  k;
        for( int n = length - 1; n > 0; n-- )
        {
            k   = random.nextInt(n + 1);
            tmp = password[n];
            password[n] = password[k];
            password[k] = tmp;
        }

        return password;
    }

    /** */
    public static PasswordMaker NEW() {
        return new PasswordMaker();
    }

}
