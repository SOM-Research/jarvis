package fr.zelus.jarvis.core.session;

import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class JarvisContextTest {

    private JarvisContext context;

    @Test
    public void constructValidContext() {
        context = new JarvisContext();
        assertThat(context.getContextMap()).as("Not null context map").isNotNull();
        assertThat(context.getContextMap()).as("Empty context map").isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void setContextValueNullContext() {
        context = new JarvisContext();
        context.setContextValue(null, "key", "value");
    }

    @Test(expected = NullPointerException.class)
    public void setContextValueNullKey() {
        context = new JarvisContext();
        context.setContextValue("context", null, "value");
    }

    @Test
    public void setContextValueNullValue() {
        context = new JarvisContext();
        context.setContextValue("context", "key", null);
        checkContextMap(context, "context", "key", null);
    }

    @Test
    public void setContextValueValidValue() {
        context = new JarvisContext();
        context.setContextValue("context", "key", "value");
        checkContextMap(context, "context", "key", "value");
    }

    @Test(expected = NullPointerException.class)
    public void getContextVariablesNullContext() {
        context = new JarvisContext();
        context.getContextVariables(null);
    }

    @Test
    public void getContextVariablesNotSetContext() {
        context = new JarvisContext();
        Map<String, Object> contextVariables = context.getContextVariables("context");
        assertThat(contextVariables).as("Empty context variables").isEmpty();
    }

    @Test
    public void getContextVariablesSetContextSetKey() {
        context = new JarvisContext();
        context.setContextValue("context", "key", "value");
        Map<String, Object> contextVariables = context.getContextVariables("context");
        assertThat(contextVariables).as("Not empty context variables").isNotEmpty();
        assertThat(contextVariables).as("Context variables contains the set key").containsKey("key");
        assertThat(contextVariables.get("key")).as("Context variables contains the set value").isEqualTo("value");
    }

    @Test(expected = NullPointerException.class)
    public void getContextValueNullContext() {
        context = new JarvisContext();
        context.getContextValue(null, "key");
    }

    @Test(expected = NullPointerException.class)
    public void getContextValueNullKey() {
        context = new JarvisContext();
        context.getContextValue("context", null);
    }

    @Test
    public void getContextValueNotSetContext() {
        context = new JarvisContext();
        Object value = context.getContextValue("context", "key");
        assertThat(value).as("Null context value").isNull();
    }

    @Test
    public void getContextValueSetContextNotSetKey() {
        context = new JarvisContext();
        context.setContextValue("context", "key", "value");
        Object value = context.getContextValue("context", "test");
        assertThat(value).as("Null context value").isNull();
    }

    @Test
    public void getContextValueSetContextSetKey() {
        context = new JarvisContext();
        context.setContextValue("context", "key", "value");
        Object value = context.getContextValue("context", "key");
        assertThat(value).as("Not null context value").isNotNull();
        assertThat(value).as("Valid value").isEqualTo("value");
    }

    private void checkContextMap(JarvisContext context, String expectedContext, String expectedKey, Object
            expectedValue) {
        Map<String, Map<String, Object>> rawContext = context.getContextMap();
        assertThat(rawContext).as("Context map contains the set context").containsKey(expectedContext);
        Map<String, Object> contextVariables = rawContext.get(expectedContext);
        assertThat(contextVariables).as("Context map contains the set key").containsKey(expectedKey);
        assertThat(contextVariables.get(expectedKey)).as("Context map contains the value").isEqualTo(expectedValue);
    }
}
