package com.xatkit.core.recognition.processor;

import com.google.gson.Gson;
import com.google.common.reflect.TypeToken;

import com.xatkit.execution.StateContext;
import fr.inria.atlanmod.commons.log.Log;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.isNull;

/**
 * Translates internet slang terms by standard-language forms.
 * <p>
 * It uses a dictionary of slang terms to perform the translations.
 * <p>
 * The dictionary can be obtained from the default Xatkit-embedded file
 * {@link InternetSlangPreProcessor#EMBEDDED_SLANG_DICTIONARY_FILE} or from an external file
 * {@link InternetSlangPreProcessor#SLANG_DICTIONARY_FILE}, setting the configuration parameter
 * {@link InternetSlangPreProcessor#SLANG_DICTIONARY_SOURCE} with its corresponding (absolute) path
 *
 * @see #process(String, StateContext)
 */
public class InternetSlangPreProcessor implements InputPreProcessor {

    /**
     * The {@link Configuration} flag to specify whether the slang dictionary should be gotten from the embedded file
     * in the resources directory or from an external location.
     * <p>
     * If you want to use de default embedded file in Xatkit, do not set this configuration property.
     * <p>
     * The property value must be an absolute path.
     */
    public static final String SLANG_DICTIONARY_SOURCE = "xatkit.slang.readExternalSlangDictionary";

    /**
     * The name of the Xatkit-embedded file containing the entries of the slang dictionary.
     */
    protected static final String EMBEDDED_SLANG_DICTIONARY_FILE = "noslang_english_dictionary.json";

    /**
     * The absolute path of the file containing a slang dictionary.
     * <p>
     * When the default dictionary is used, this has {@code null} value.
     */
    protected static String SLANG_DICTIONARY_FILE;

    /**
     * The in-memory {@link Map} containing the slang dictionary obtained from the corresponding file.
     * Keys are the slang terms and values are their corresponding translations to standard language.
     */
    protected static Map<String,String>  slangDictionary;

    /**
     * A list of punctuation characters, used to identify them at the end of a slang term in the input text
     */
    protected static final String[] PUNCTUATION = {"?", "!", ".", ","};

    /**
     * Initializes the {@link InternetSlangPreProcessor}.
     * <p>
     * If the {@link InternetSlangPreProcessor#SLANG_DICTIONARY_SOURCE} configuration option is set,
     * {@link InternetSlangPreProcessor#SLANG_DICTIONARY_FILE} is read. Else,
     * {@link InternetSlangPreProcessor#EMBEDDED_SLANG_DICTIONARY_FILE} is read. Then, the read content is stored in
     * {@link InternetSlangPreProcessor#slangDictionary}
     */
    public InternetSlangPreProcessor(Configuration configuration) {
        String dictionaryAsString = "";
        SLANG_DICTIONARY_FILE = configuration.getString(SLANG_DICTIONARY_SOURCE, null);
        if (isNull(SLANG_DICTIONARY_FILE)) {
            try (InputStream inputStream =
                         InternetSlangPreProcessor.class.getClassLoader().getResourceAsStream(EMBEDDED_SLANG_DICTIONARY_FILE)) {
                if (isNull(inputStream)) {
                    Log.error("Cannot find the file {0}, this processor won't get get the slang dictionary",
                            EMBEDDED_SLANG_DICTIONARY_FILE);
                } else {
                    dictionaryAsString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                Log.error("An error occurred when processing the slang database {0}, this processor may produce "
                        + "unexpected behavior. Check the logs for more information.", EMBEDDED_SLANG_DICTIONARY_FILE);
            }
        } else {
            File slangDictionaryFile = new File(SLANG_DICTIONARY_FILE);
            try (InputStream inputStream = new FileInputStream(slangDictionaryFile)) {
                if (isNull(inputStream)) {
                    Log.error("Cannot find the file {0}, this processor won't get get the slang dictionary",
                            SLANG_DICTIONARY_FILE);
                } else {
                    dictionaryAsString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                Log.error("An error occurred when processing the slang database {0}, this processor may produce "
                        + "unexpected behavior. Check the logs for more information.", SLANG_DICTIONARY_FILE);
            }
        }

        slangDictionary = new Gson().fromJson(dictionaryAsString, new TypeToken<HashMap<String, String>>(){}.getType());
        if (isNull(SLANG_DICTIONARY_FILE)) {
            Log.info("Loaded {0} dictionary entries from {1}", slangDictionary.size(), EMBEDDED_SLANG_DICTIONARY_FILE);
        } else {
            Log.info("Loaded {0} dictionary entries from {1}", slangDictionary.size(), SLANG_DICTIONARY_FILE);
        }
    }

    /**
     * Processes the provided {@code input}, replacing the slang occurrences by their respective standard-English
     * translations.
     * <p>
     * The processor takes care of the punctuation characters after a slang term. If after a slang term there is some
     * punctuation character present in {@link InternetSlangPreProcessor#PUNCTUATION}, the slang term is properly
     * recognized and the punctuation character will remain there. If after a slang term there is some character
     * (different from a space) not present in {@link InternetSlangPreProcessor#PUNCTUATION}, the slang term will not
     * be recognized and thus not replaced by its standard form.
     *
     * @param input   the input to process
     * @param context the {@link StateContext} associated to the {@code input}
     * @return the processed {@code input}
     */
    public String process(String input, StateContext context) {
        String[] inputWords = input.split(" ");
        for (int i = 0; i < inputWords.length; i++) {
            String word = inputWords[i];
            String lastChar = word.substring(word.length()-1);
            if (Arrays.asList(PUNCTUATION).contains(lastChar)) {
                word = word.substring(0, word.length()-1);
            } else {
                lastChar = "";
            }
            String translation = slangDictionary.get(word);
            if (!isNull(translation)) {
                inputWords[i] = translation + lastChar;
            }
        }
        return String.join(" ", inputWords);
    }
}
