package ru.inversion.parser.statement;

import ru.inversion.utils.S;
import ru.inversion.utils.U;

/** */
public enum LangEnum {

    UNKNOWN,

    SQL,

    PL_SQL,

    JDBC,

    PL_pgSQL,

    PL; // Обобщенный для PL_SQL и PL_pgSQL

    public static LangEnum of( String s ) {

        if( S.isNullOrEmpty(s) )
            return null;

        String sl = s.toUpperCase();

        switch( sl ) {
            case "PL/SQL":
            case "PL_SQL":
            case "PL-SQL":
            case "PLSQL":
                return PL_SQL;
            case "SQL":
                return SQL;
            case "JDBC":
                return JDBC;
            case "PL/PGSQL":
            case "PL_PGSQL":
            case "PL-PGSQL":
            case "PGPL/SQL":
            case "PLPG/SQL":
                return PL_pgSQL;
            case "PL":
                return PL;
        }
        return UNKNOWN;
    }

    /** */
    public boolean isSpec()
    {
        return this == PL_SQL || this == PL_pgSQL;
    }
}

