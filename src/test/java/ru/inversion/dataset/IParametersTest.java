package ru.inversion.dataset;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class IParametersTest {

    @Test
    public void getParameterOrDefaultDistinguishesMissingFromExplicitNull() {
        Map<String,Object> named = new HashMap<>();
        named.put("presentNull", null);
        named.put("value", 17);

        IParameters parameters = ParametersByName.of(named);

        assertNull(parameters.getParameterOrDefault("presentNull", 99));
        assertEquals(17, parameters.getParameterOrDefault("value", 99));
        assertEquals(99, parameters.getParameterOrDefault("missing", 99));
    }

    @Test
    public void indexedAdapterReturnsNullForMissingIndex() {
        ParametersByIndex parameters =
                ParametersByIndex.of(Arrays.<Object>asList("A", null));

        assertEquals("A", parameters.getParameter(0));
        assertNull(parameters.getParameter(1));
        assertNull(parameters.getParameter(-1));
        assertNull(parameters.getParameter(2));

        assertFalse(parameters.hasParameter(-1));
        assertFalse(parameters.hasParameter(2));
        assertFalse(parameters.isEmpty());
    }

    @Test
    public void adaptersReportEmptyState() {
        assertTrue(ParametersByName.of(
                Collections.<String,Object>emptyMap()).isEmpty());

        assertTrue(ParametersByIndex.of(
                Collections.emptyList()).isEmpty());
    }
}
