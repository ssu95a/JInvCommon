package ru.inversion.dataset.impl;

import org.junit.jupiter.api.Test;
import ru.inversion.tc.TaskContext;

import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class XXIDsDaoNativeQueryTest {

    @NamedNativeQueries({
            @NamedNativeQuery(
                    name = "alpha.pg", query = "PG_ALPHA"),
            @NamedNativeQuery(
                    name = "alpha", query = "ORA_ALPHA")
    })
    private static final class OneLogicalQuery {
    }

    @NamedNativeQueries({
            @NamedNativeQuery(
                    name = "byEntryId",
                    query = "SELECT * FROM V_SM_003_MF WHERE idsm_entry = ?")
    })
    private static final class OneNamedQueryInContainer {
    }

    @NamedNativeQueries({
            @NamedNativeQuery(
                    name = "alpha", query = "ORA_ALPHA"),
            @NamedNativeQuery(
                    name = "alpha.pg", query = "PG_ALPHA"),
            @NamedNativeQuery(
                    name = "beta", query = "ORA_BETA"),
            @NamedNativeQuery(
                    name = "beta.pg", query = "PG_BETA")
    })
    private static final class MultipleLogicalQueries {
    }

    @NamedNativeQuery(name = "common", query = "COMMON")
    private static final class CommonQuery {
    }

    @NamedNativeQueries({
            @NamedNativeQuery(name = "row", query = "ROW"),
            @NamedNativeQuery(name = "row.xx", query = "NOT_PG")
    })
    private static final class InvalidPostfixPair {
    }

    @Test
    void namedQueryUsesPostgresVariantAndCommonFallback()
            throws Exception {
        TaskContext oracle = taskContext(1);
        TaskContext postgres = taskContext(2);

        assertEquals(
                "ORA_ALPHA",
                XXIDsDao.getNativeQuery(
                        oracle, MultipleLogicalQueries.class, "alpha")
        );
        assertEquals(
                "PG_ALPHA",
                XXIDsDao.getNativeQuery(
                        postgres, MultipleLogicalQueries.class, "alpha")
        );
        assertEquals(
                "PG_BETA",
                XXIDsDao.getNativeQuery(
                        postgres, MultipleLogicalQueries.class, "beta")
        );
        assertEquals(
                "COMMON",
                XXIDsDao.getNativeQuery(
                        postgres, CommonQuery.class, "common")
        );
        assertNull(XXIDsDao.getNativeQuery(
                postgres, CommonQuery.class, "missing"));
    }

    @Test
    void explicitPostgresNameIsNotSuffixedTwice() throws Exception {
        TaskContext postgres = taskContext(2);

        assertEquals(
                "PG_ALPHA",
                XXIDsDao.getNativeQuery(
                        postgres,
                        MultipleLogicalQueries.class,
                        "alpha.pg"
                )
        );
        assertNull(XXIDsDao.getNativeQuery(
                postgres,
                MultipleLogicalQueries.class,
                "missing.pg"
        ));
    }

    @Test
    void unnamedQueryResolvesOnlyOneLogicalFamily()
            throws Exception {
        TaskContext oracle = taskContext(1);
        TaskContext postgres = taskContext(2);

        assertEquals(
                "ORA_ALPHA",
                XXIDsDao.getNativeQuery(
                        oracle, OneLogicalQuery.class, null)
        );
        assertEquals(
                "PG_ALPHA",
                XXIDsDao.getNativeQuery(
                        postgres, OneLogicalQuery.class, null)
        );
        assertEquals(
                "PG_ALPHA",
                XXIDsDao.getUnnamedNativeQuery(
                        postgres, OneLogicalQuery.class)
        );
        assertEquals(
                "COMMON",
                XXIDsDao.getNativeQuery(
                        postgres, CommonQuery.class, null)
        );
        assertNull(XXIDsDao.getNativeQuery(
                postgres, MultipleLogicalQueries.class, null));
    }

    @Test
    void singleNamedQueryInContainerIsNotImplicitDefault() {
        assertNull(XXIDsDao.getNativeQuery(
                null, OneNamedQueryInContainer.class, null));
        assertEquals(
                "SELECT * FROM V_SM_003_MF WHERE idsm_entry = ?",
                XXIDsDao.getNativeQuery(
                        null, OneNamedQueryInContainer.class, "byEntryId")
        );
    }

    @Test
    void onlyPgPostfixFormsADialectPair() throws Exception {
        assertNull(XXIDsDao.getNativeQuery(
                taskContext(2), InvalidPostfixPair.class, null));
    }

    @Test
    void nullContextKeepsOracleCompatibilityDefault() {
        assertEquals(
                "ORA_ALPHA",
                XXIDsDao.getNativeQuery(
                        null, OneLogicalQuery.class, null)
        );
        assertNull(XXIDsDao.getNativeQuery(
                null, null, "alpha"));
    }

    private static TaskContext taskContext(int databaseCode)
            throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);

        Method allocateInstance = unsafeClass.getMethod(
                "allocateInstance", Class.class);
        TaskContext tc = (TaskContext)allocateInstance.invoke(
                unsafe, TaskContext.class);
        tc.setProperty("ru.inversion.db_prod_name", databaseCode);
        return tc;
    }
}
