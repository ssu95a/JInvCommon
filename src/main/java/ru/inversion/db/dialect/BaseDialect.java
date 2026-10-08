package ru.inversion.db.dialect;

import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.utils.S;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static ru.inversion.utils.parser.ITokenHandler.isAlpha;
import static ru.inversion.utils.parser.ITokenHandler.isAlphaDigit;

/** */
public abstract class BaseDialect implements SqlDialect {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_$ ()&=,]*$");

    static final protected DateTimeFormatter to_dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static final protected DateTimeFormatter to_timestampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public <P,R> String columnName( IEntityProperty<P,R> property )
    {
        return sqlName( property.getColumnInfo().getName() );
    }

    @Override
    public <P,R> String inName( IEntityProperty <P,R> property )
    {
        String n = property.getPropertyName();

        if( S.isNullOrEmpty(n) )
            throw new IllegalArgumentException( "Unsafe bind name: " + n );

        if( isNormalized(n) )
            return n;

        return normalized(n);
    }

    public String inName( String name )
    {
        if( S.isNullOrEmpty(name) )
            throw new IllegalArgumentException( "Unsafe bind name: " + name );

        if( isNormalized(name) )
            return name;

        return normalized(name);
    }

    @Override
    public <P,R> String outName(IEntityProperty<P,R> property) {
        return "OUT__" + inName(property);
    }
    @Override
    public String outName(String name) {
        return "OUT__" + inName(name);
    }

    @Override
    public <P,R> String pkName (IEntityProperty<P,R> pkProperty) {
        return "PK__" + inName(pkProperty);
    }

    /** В кавычки */
    protected String quote(String raw ) {

        if( S.isNullOrEmpty(raw) )
            throw new IllegalArgumentException("'raw' is null");

        String escaped = raw.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    /** */
    protected boolean checkIdentifier(String raw) {
        return raw != null && NAME_PATTERN.matcher(raw).matches(); // && !isMixedCase(raw);
    }

    /** Если строка в смешанном регистре */
    protected static boolean isMixedCase( String s )
    {
        boolean hasLower = false,
                hasUpper = false;

        for( int i = 0; i < s.length(); i++ )
        {
            char c = s.charAt(i);

            if( Character.isLowerCase(c))
                hasLower = true;
            else
                if( Character.isUpperCase(c))
                    hasUpper = true;

            if( hasLower && hasUpper )
                return true;
        }
        return false;
    }

    /** Выражения для преобразования к дате строки */
    @Override
    public String toDateClause( Object dateObj ) {
        return dateObj == null ? null : dateObj.toString();
    }

    /** */
    static boolean isNormalized( String name )
    {
        char ch = name.charAt(0);

        if( !( isAlpha(ch) || ch == '_') )
            return false;

        for(int i = 1; i < name.length(); i++)
        {
            ch = name.charAt(i);

            if( !(isAlphaDigit(ch) || ch == '_') )
                return false;
        }

        return true;
    }

    /** */
    static String normalized( String raw )
    {
        if( S.isNullOrEmpty(raw) )
            throw new IllegalArgumentException("bind name 'raw' is empty");

        final StringBuilder sb = new StringBuilder( raw.length() + 5 );

        char ch = raw.charAt(0);

        if( isAlpha(ch) || ch == '_')
            sb.append(ch);
        else
            sb.append('_');

        for( int i = 1; i < raw.length(); i++ )
        {
            char c = raw.charAt(i);

            if( isAlphaDigit(c) || c == '_')
                sb.append(c);
            else
            {
                sb.append('_');
                //changed = true;
            }
        }

        int h = raw.toLowerCase().hashCode();
        sb.append('_').append( Integer.toString( h & 0x7fffffff, 36 ) );

        return sb.toString();
    }

    /** */
    protected <P> Collection<IEntityProperty <P,?>> sortColumns( IEntityMetaData<P> md, Collection<String> columnOrder )
    {
        final Map<String, IEntityProperty<P, ?>> columnsMap = md.getColumnsMap();
        final Collection<IEntityProperty<P, ?>> columnsList = columnsMap.values();

        if( columnsList.isEmpty() || columnOrder.isEmpty() )
            return columnsList;

        final List< IEntityProperty<P,?> > clo = new ArrayList<>( columnsList.size() );

        for( String name : columnOrder )
        {
            if( S.isNullOrEmpty(name) )
                continue;

            IEntityProperty<P, ?> prp = columnsMap.get( columnName(name) );
            if( prp != null )
                clo.add(prp );
        }

        if( !clo.isEmpty() ) {
            columnsList.stream().filter(p -> !clo.contains(p)).forEach(clo::add);
            return clo;
        }
        else
            return columnsList;
    }
}
