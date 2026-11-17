package ru.inversion.datacall;

import org.junit.jupiter.api.Test;
import ru.inversion.dataset.IParameters;
import ru.inversion.utils.ParametersValues;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DbmsSqlWorkTest {

    private static DbmsSqlWork work(String sql, IParameters values) {
        return new DbmsSqlWork(null, CallBuilder.NEW().sql(sql).build(), values);
    }

    @Test
    void exactAndMixedCaseRepeatsShareOneSlot() {
        DbmsSqlWork work = work("begin :A := nvl(:A, 0) + :a; end;", new ParametersValues());
        assertEquals(Collections.singletonList("b1"), work.vars);
        assertEquals(3, occurrences(work.preparedSql(), ":b1"));
        work.close();
    }

    @Test
    void dottedRepeatsAndDifferentNamesKeepFirstOccurrenceOrder() {
        ParametersValues values = new ParametersValues();
        values.setParameter("block.item", "first");
        values.setParameter("B", "second");
        DbmsSqlWork work = work("begin :BLOCK.ITEM := :b; :block.item := :B; end;", values);
        assertEquals(Arrays.asList("b1", "b2"), work.vars);
        assertEquals(Arrays.asList("first", "second"), work.inputValues());
        assertEquals(2, occurrences(work.preparedSql(), ":b1"));
        assertEquals(2, occurrences(work.preparedSql(), ":b2"));
        work.close();
    }

    @Test
    void missingAndExplicitNullAreBothSentAsNullAndEachRunReadsFreshValues() {
        ParametersValues values = new ParametersValues();
        DbmsSqlWork work = work("begin :A := :B; end;", values);
        assertEquals(Arrays.asList(null, null), work.inputValues());
        values.setParameter("A", null);
        values.setParameter("B", 42);
        assertEquals(Arrays.asList(null, "42"), work.inputValues());
        values.setParameter("B", 43);
        assertEquals(Arrays.asList(null, "43"), work.inputValues());
        work.close();
    }

    @Test
    void validatesOutputBeforeMutatingAnyParameter() {
        ParametersValues values = new ParametersValues();
        values.setParameter("A", "old-a");
        values.setParameter("B", "old-b");
        DbmsSqlWork work = work("begin :A := :B; end;", values);
        assertThrows(IllegalStateException.class, () -> work.applyOutputs(null));
        assertThrows(IllegalStateException.class, () -> work.applyOutputs(Collections.singletonList("new-a")));
        assertThrows(IllegalStateException.class, () -> work.applyOutputs(Arrays.asList("new-a", "new-b", "extra")));
        assertEquals("old-a", values.get("A"));
        assertEquals("old-b", values.get("B"));
        work.applyOutputs(Arrays.asList("new-a", null));
        assertEquals("new-a", values.get("A"));
        assertNull(values.get("B"));
        work.close();
    }

    @Test
    void generatedBindNameCannotCollideWithSourceName() {
        ParametersValues values = new ParametersValues();
        values.setParameter("b1", "original");
        values.setParameter("OTHER", "other");
        DbmsSqlWork work = work("begin :b1 := :OTHER; end;", values);
        assertEquals(Arrays.asList("original", "other"), work.inputValues());
        work.applyOutputs(Arrays.asList("result", "second"));
        assertEquals("result", values.get("b1"));
        assertEquals("second", values.get("OTHER"));
        work.close();
    }

    @Test
    void launcherWireAndRepeatedExecutionUseFreshValuesAndPreserveDiagnostics() {
        ParametersValues values = new ParametersValues();
        values.setParameter("A", "first");
        FakeLauncher launcher = new FakeLauncher();
        launcher.outs = Collections.singletonList("out-one");
        launcher.resultCode = 0;
        launcher.resultInfo = null;
        DbmsSqlWork work = new DbmsSqlWork(
                CallBuilder.NEW().sql("begin :A := :a; end;").build(), values, launcher);

        work.execute(null);
        assertEquals(1, launcher.executions);
        assertEquals(1, launcher.closes);
        assertEquals(work.preparedSql(), launcher.bound.get("plSql"));
        assertEquals(Collections.singletonList("b1"), launcher.bound.get("vars"));
        assertEquals(Collections.singletonList("first"), launcher.bound.get("vals"));
        assertEquals(Collections.emptyList(), launcher.bound.get("typs"));
        assertEquals(Integer.valueOf(0), work.resultCode());
        assertNull(work.resultInfo());
        assertEquals("out-one", values.get("A"));

        values.setParameter("a", "second");
        launcher.outs = Collections.singletonList("out-two");
        work.execute(null);
        assertEquals(Collections.singletonList("second"), launcher.bound.get("vals"));
        assertEquals(2, launcher.executions);
        assertEquals(2, launcher.closes);
        assertEquals("out-two", values.get("A"));
    }

    @Test
    void serverReturnCodeIsCheckedBeforeReadingOutputs() {
        ParametersValues values = new ParametersValues();
        values.setParameter("A", "old");
        FakeLauncher launcher = new FakeLauncher();
        launcher.resultCode = -1;
        launcher.resultInfo = "Dbms_Sql_Work: the size of a collection with values is smaller than a collection with parameters.";
        launcher.outs = null;
        DbmsSqlWork work = new DbmsSqlWork(
                CallBuilder.NEW().sql("begin :A := 1; end;").build(), values, launcher);

        CallException error = assertThrows(CallException.class, () -> work.execute(null));
        assertTrue(error.getMessage().contains("returned -1"));
        assertTrue(error.getMessage().contains(launcher.resultInfo));
        assertEquals("old", values.get("A"));
        assertEquals(Integer.valueOf(-1), work.resultCode());
        assertEquals(launcher.resultInfo, work.resultInfo());
        assertEquals(1, launcher.closes);

        launcher.resultCode = null;
        launcher.resultInfo = null;
        error = assertThrows(CallException.class, () -> work.execute(null));
        assertTrue(error.getMessage().contains("returned null"));
        assertEquals("old", values.get("A"));
    }

    @Test
    void launcherFailureRetainsCleanupFailure() {
        FakeLauncher launcher = new FakeLauncher();
        launcher.executeFailure = new CallException("execution failure");
        launcher.closeFailure = new IllegalStateException("cleanup failure");
        DbmsSqlWork work = new DbmsSqlWork(
                CallBuilder.NEW().sql("begin :A := 1; end;").build(),
                new ParametersValues(), launcher);

        CallException error = assertThrows(CallException.class, () -> work.execute(null));
        assertSame(launcher.executeFailure, error);
        assertEquals("cleanup failure", error.getSuppressed()[0].getMessage());
        assertEquals(1, launcher.closes);
    }

    private static final class FakeLauncher extends CallWork {
        final Map<String,Object> bound = new HashMap<>();
        List<String> outs;
        Integer resultCode;
        String resultInfo;
        RuntimeException executeFailure;
        RuntimeException closeFailure;
        int executions;
        int closes;

        FakeLauncher() { super(null, null, null, true); }

        @Override
        public <T> CallWork set(Comparable parameter, T value) {
            bound.put(parameter.toString(), value);
            return this;
        }

        @Override
        public CallWork execute() {
            executions++;
            if( executeFailure != null )
                throw executeFailure;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Comparable parameter) {
            switch( parameter.toString() ) {
                case "outs": return (T)outs;
                case "ret": return (T)resultCode;
                case "rsltInf": return (T)resultInfo;
                default: return null;
            }
        }

        @Override
        public void close() {
            closes++;
            if( closeFailure != null )
                throw closeFailure;
        }
    }

    private static int occurrences(String text, String part) {
        int result = 0;
        int from = 0;
        while( (from = text.indexOf(part, from)) >= 0 ) {
            result++;
            from += part.length();
        }
        return result;
    }
}
