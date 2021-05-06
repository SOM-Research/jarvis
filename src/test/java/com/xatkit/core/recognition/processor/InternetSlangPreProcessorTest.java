package com.xatkit.core.recognition.processor;

import com.xatkit.execution.ExecutionFactory;
import com.xatkit.execution.StateContext;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InternetSlangPreProcessorTest {

    private InternetSlangPreProcessor processor;
    private StateContext context;

    @Before
    public void setUp() {
        this.processor = null;
        this.context = ExecutionFactory.eINSTANCE.createStateContext();
        this.context.setContextId("contextId");
    }

    @Test
    public void testSlangWords() {
        Configuration botConfiguration = new BaseConfiguration();
        // Uncomment next line and set the property value to test external dictionary
        //botConfiguration.setProperty(InternetSlangPreProcessor.SLANG_DICTIONARY_SOURCE, "<Path to json file>");
        processor = new InternetSlangPreProcessor(botConfiguration);
        // A single slang term
        String input = "omg";
        String processedInput = processor.process(input, context);
        assertThat(processedInput).isEqualTo("oh my God");
        // Some slang terms followed by punctuation signs
        input = "wtf, wtf?";
        processedInput = processor.process(input, context);
        assertThat(processedInput).isEqualTo("what the f**k, what the f**k?");
        // Some slang terms together with regular words
        input = "idk if u r happy";
        processedInput = processor.process(input, context);
        assertThat(processedInput).isEqualTo("I don't know if you are happy");

    }


}
