package fr.zelus.jarvis.dialogflow;

import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.FailedPreconditionException;
import com.google.cloud.dialogflow.v2.*;
import com.google.cloud.dialogflow.v2.Context;
import com.google.longrunning.Operation;
import com.google.protobuf.Value;
import fr.inria.atlanmod.commons.log.Log;
import fr.zelus.jarvis.core.EventDefinitionRegistry;
import fr.zelus.jarvis.core.JarvisCore;
import fr.zelus.jarvis.core.session.JarvisSession;
import fr.zelus.jarvis.intent.*;
import org.apache.commons.configuration2.Configuration;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static fr.inria.atlanmod.commons.Preconditions.checkArgument;
import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * A wrapper of the DialogFlow API that provides utility methods to connect to a given DialogFlow project, start
 * sessions, manage registered intents, and detect intents instances from textual inputs.
 * <p>
 * This class is used to easily setup a connection to a given DialogFlow project. Note that in addition to the
 * constructor parameters, the {@code GOOGLE_APPLICATION_CREDENTIALS} environment variable must be set and point to
 * the DialogFlow project's key. See
 * <a href="https://cloud.google.com/dialogflow-enterprise/docs/reference/libraries">DialogFlow documentation</a> for
 * further information.
 */
public class DialogFlowApi {

    /**
     * The {@link Configuration} key to store the unique identifier of the DialogFlow project.
     *
     * @see #DialogFlowApi(JarvisCore, Configuration)
     */
    public static String PROJECT_ID_KEY = "jarvis.dialogflow.projectId";

    /**
     * The {@link Configuration} key to store the code of the language processed by DialogFlow.
     *
     * @see #DialogFlowApi(JarvisCore, Configuration)
     */
    public static String LANGUAGE_CODE_KEY = "jarvis.dialogflow.language";

    /**
     * The default language processed by DialogFlow.
     */
    public static String DEFAULT_LANGUAGE_CODE = "en-US";

    /**
     * The DialogFlow Default Fallback Intent that is returned when the user input does not match any registered Intent.
     *
     * @see #convertDialogFlowIntentToIntentDefinition(Intent)
     */
    private static IntentDefinition DEFAULT_FALLBACK_INTENT = IntentFactory.eINSTANCE.createIntentDefinition();

    /**
     * Initializes the {@link #DEFAULT_FALLBACK_INTENT}'s name.
     */
    static {
        DEFAULT_FALLBACK_INTENT.setName("Default Fallback Intent");
    }

    private JarvisCore jarvisCore;

    /**
     * The {@link Configuration} used to initialize this class.
     * <p>
     * This {@link Configuration} is used to retrieve the underlying DialogFlow project identifier, language, and
     * customize {@link JarvisSession} and {@link fr.zelus.jarvis.core.session.JarvisContext}s.
     */
    private Configuration configuration;

    /**
     * The unique identifier of the DialogFlow project.
     */
    private String projectId;

    /**
     * The language code of the DialogFlow project.
     */
    private String languageCode;

    /**
     * Represents the DialogFlow project name.
     * <p>
     * This attribute is used to compute project-level operations, such as the training of the underlying
     * DialogFlow's agent.
     *
     * @see #trainMLEngine()
     */
    private ProjectName projectName;

    /**
     * Represents the DialogFlow agent name.
     * <p>
     * This attribute is used to compute intent-level operations, such as retrieving the list of registered
     * {@link Intent}s, or deleting specific {@link Intent}s.
     *
     * @see #registerIntentDefinition(IntentDefinition)
     * @see #deleteIntentDefinition(IntentDefinition)
     */
    private ProjectAgentName projectAgentName;

    /**
     * The client instance managing DialogFlow agent-related queries.
     * <p>
     * This client is used to compute project-level operations, such as the training of the underlying DialogFlow's
     * agent.
     *
     * @see #trainMLEngine()
     */
    private AgentsClient agentsClient;

    /**
     * The client instance managing DialogFlow intent-related queries.
     * <p>
     * This client is used to compute intent-level operations, such as retrieving the list of registered
     * {@link Intent}s, or deleting specific {@link Intent}s.
     *
     * @see #registerIntentDefinition(IntentDefinition)
     * @see #deleteIntentDefinition(IntentDefinition)
     */
    private IntentsClient intentsClient;

    /**
     * The client instance managing DialogFlow sessions.
     * <p>
     * This instance is used to initiate new sessions (see {@link #createSession(String)}) and send {@link Intent}
     * detection queries to the DialogFlow engine.
     */
    private SessionsClient sessionsClient;


    /**
     * The {@link IntentFactory} used to create {@link RecognizedIntent} instances from DialogFlow computed
     * {@link Intent}s.
     */
    private IntentFactory intentFactory;

    /**
     * Constructs a {@link DialogFlowApi} with the provided {@code configuration}.
     * <p>
     * The provided {@code configuration} must provide values for the following keys:
     * <ul>
     * <li><b>jarvis.dialogflow.projectId</b>: the unique identifier of the DialogFlow project</li>
     * </ul>
     * The value <b>jarvis.dialogflow.language</b> is not mandatory: if no language code is provided in the
     * {@link Configuration} the default one ({@link #DEFAULT_LANGUAGE_CODE} will be used.
     *
     * @param jarvisCore    the {@link JarvisCore} instance managing the {@link DialogFlowApi}
     * @param configuration the {@link Configuration} holding the DialogFlow project ID and language code
     * @throws NullPointerException if the provided {@code jarvisCore}, {@code configuration} or one of the mandatory
     *                              {@code configuration} value is {@code null}.
     * @throws DialogFlowException  if the client failed to start a new session
     */
    public DialogFlowApi(JarvisCore jarvisCore, Configuration configuration) {
        checkNotNull(jarvisCore, "Cannot construct a DialogFlow API instance with a null JarvisCore instance");
        checkNotNull(configuration, "Cannot construct a DialogFlow API instance from a configuration");
        try {
            Log.info("Starting DialogFlow Client");
            this.jarvisCore = jarvisCore;
            this.configuration = configuration;
            this.projectId = configuration.getString(PROJECT_ID_KEY);
            checkNotNull(projectId, "Cannot construct a jarvis instance from a null projectId");
            this.languageCode = configuration.getString(LANGUAGE_CODE_KEY);
            if (isNull(languageCode)) {
                Log.warn("No language code provided, using the default one ({0})", DEFAULT_LANGUAGE_CODE);
                languageCode = DEFAULT_LANGUAGE_CODE;
            }
            this.sessionsClient = SessionsClient.create();
            this.intentsClient = IntentsClient.create();
            this.projectAgentName = ProjectAgentName.of(projectId);
            this.agentsClient = AgentsClient.create();
            this.projectName = ProjectName.of(projectId);
            this.intentFactory = IntentFactory.eINSTANCE;
        } catch (IOException e) {
            throw new DialogFlowException("Cannot construct the DialogFlow API", e);
        }
    }

    /**
     * Returns the DialogFlow project unique identifier.
     *
     * @return the DialogFlow project unique identifier
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * Returns the code of the language processed by DialogFlow.
     *
     * @return the code of the language processed by DialogFlow
     */
    public String getLanguageCode() {
        return languageCode;
    }

    /**
     * Returns the full descriptions of the {@link Intent}s that are registered in the DialogFlow project.
     * <p>
     * The full descriptions of the {@link Intent}s include the {@code training phrases}, that are typically used in
     * testing methods to check that a created {@link Intent} contains all the information provided to the API. To
     * get a partial description of the registered {@link Intent}s see {@link #getRegisteredIntents()}.
     * <p>
     * <b>Note:</b> this method is protected for testing purposes, and should not be called by client code.
     *
     * @return the full descriptions of the {@link Intent}s that are registered in the DialogFlow project
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     */
    protected List<Intent> getRegisteredIntentsFullView() {
        if (isShutdown()) {
            throw new DialogFlowException("Cannot retrieve the registered Intents (full view), the DialogFlow API is " +
                    "shutdown");
        }
        List<Intent> registeredIntents = new ArrayList<>();
        ListIntentsRequest request = ListIntentsRequest.newBuilder().setIntentView(IntentView.INTENT_VIEW_FULL)
                .setParent(projectAgentName.toString()).build();
        for (Intent intent : intentsClient.listIntents(request).iterateAll()) {
            registeredIntents.add(intent);
        }
        return registeredIntents;
    }

    /**
     * Returns the partial description of the {@link Intent}s that are registered in the DialogFlow project.
     * <p>
     * The partial descriptions of the {@link Intent}s does not include the {@code training phrases}. To get a full
     * description of the registered {@link Intent}s see {@link #getRegisteredIntentsFullView()}
     * <p>
     * <b>Note:</b> this method is protected for testing purposes, and should not be called by client code.
     *
     * @return the partial descriptions of the {@link Intent}s that are registered in the DialogFlow project
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     */
    protected List<Intent> getRegisteredIntents() {
        if (isShutdown()) {
            throw new DialogFlowException("Cannot retrieve the registered Intents (partial view), the DialogFlow API " +
                    "is shutdown");
        }
        List<Intent> registeredIntents = new ArrayList<>();
        for (Intent intent : intentsClient.listIntents(projectAgentName).iterateAll()) {
            registeredIntents.add(intent);
        }
        return registeredIntents;
    }

    /**
     * Registers the provided {@code intentDefinition} in the DialogFlow project.
     * <p>
     * This method reuses the information contained in the provided {@link IntentDefinition} to create a new
     * DialogFlow {@link Intent} and add it to the current project.
     * <p>
     * <b>Note:</b> this method does not train the underlying DialogFlow Machine Learning Engine, so multiple calls
     * to this method are not generating multiple training calls. Once all the {@link IntentDefinition}s have been
     * registered to the DialogFlow project use {@link #trainMLEngine()} to train the ML Engine.
     *
     * @param intentDefinition the {@link IntentDefinition} to register to the DialogFlow project
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown, or if the {@link Intent} already exists in
     *                             the DialogFlow project
     * @see #trainMLEngine()
     */
    public void registerIntentDefinition(IntentDefinition intentDefinition) {
        if (isShutdown()) {
            throw new DialogFlowException(MessageFormat.format("Cannot register the Intent {0}, the DialogFlow API is" +
                    " shutdown", intentDefinition.getName()));
        }
        checkNotNull(intentDefinition, "Cannot register the IntentDefinition null");
        checkNotNull(intentDefinition.getName(), "Cannot register the IntentDefinition with null as its name");
        Log.info("Registering DialogFlow intent {0}", intentDefinition.getName());
        List<String> trainingSentences = intentDefinition.getTrainingSentences();
        List<Intent.TrainingPhrase> dialogFlowTrainingPhrases = new ArrayList<>();
        for (String trainingSentence : trainingSentences) {
            dialogFlowTrainingPhrases.add(createTrainingPhrase(trainingSentence, intentDefinition.getOutContexts()));
        }

        List<String> inContextNames = createInContextNames(intentDefinition.getInContexts());
        List<Context> outContexts = createOutContexts(intentDefinition.getOutContexts());
        List<Intent.Parameter> parameters = createParameters(intentDefinition.getOutContexts());

        Intent intent = Intent.newBuilder().setDisplayName(adaptIntentDefinitionNameToDialogFlow(intentDefinition
                .getName())).addAllTrainingPhrases(dialogFlowTrainingPhrases).addAllInputContextNames(inContextNames)
                .addAllOutputContexts(outContexts).addAllParameters(parameters).build();
        try {
            Intent response = intentsClient.createIntent(projectAgentName, intent);
            Log.info("Intent {0} successfully registered", response.getDisplayName());
        } catch (FailedPreconditionException e) {
            if (e.getMessage().contains("already exists")) {
                String errorMessage = MessageFormat.format("Cannot register the intent {0}, the intent already " +
                        "exists", intentDefinition.getName());
                Log.error(errorMessage);
                throw new DialogFlowException(errorMessage, e);
            }
        }
    }

    protected Intent.TrainingPhrase createTrainingPhrase(String trainingSentence, List<fr.zelus.jarvis.intent
            .Context> outContexts) {
        if (outContexts.isEmpty()) {
            return Intent.TrainingPhrase.newBuilder().addParts(Intent.TrainingPhrase.Part.newBuilder().setText
                    (trainingSentence).build()).build();
        } else {
            // prepare the string
            String preparedTrainingSentence = trainingSentence;
            for (fr.zelus.jarvis.intent.Context context : outContexts) {
                for (ContextParameter parameter : context.getParameters()) {
                    if (preparedTrainingSentence.contains(parameter.getTextFragment())) {
                        preparedTrainingSentence = preparedTrainingSentence.replace(parameter.getTextFragment(), "#"
                                + parameter
                                .getTextFragment() + "#");
                    }
                }
            }
            // process the string
            String[] splitTrainingSentence = preparedTrainingSentence.split("#");
            Intent.TrainingPhrase.Builder trainingPhraseBuilder = Intent.TrainingPhrase.newBuilder();
            for (int i = 0; i < splitTrainingSentence.length; i++) {
                String sentencePart = splitTrainingSentence[i];
                Intent.TrainingPhrase.Part.Builder partBuilder = Intent.TrainingPhrase.Part.newBuilder().setText
                        (sentencePart);
                for (fr.zelus.jarvis.intent.Context context : outContexts) {
                    for (ContextParameter parameter : context.getParameters()) {
                        if (sentencePart.equals(parameter.getTextFragment())) {
                            partBuilder.setEntityType(parameter.getEntityType()).setAlias(parameter.getName());
                        }
                    }
                }
                trainingPhraseBuilder.addParts(partBuilder.build());
            }
            return trainingPhraseBuilder.build();
        }
    }

    protected List<String> createInContextNames(List<fr.zelus.jarvis.intent.Context> contexts) {
        List<String> results = new ArrayList<>();
        for (fr.zelus.jarvis.intent.Context context : contexts) {
            /*
             * Use a dummy session to create the context.
             */
            ContextName contextName = ContextName.of(projectId, SessionName.of(projectId, "setup").getSession(),
                    context.getName());
            results.add(contextName.toString());
            /*
             * Ignore the context parameters, they are not taken into account by DialogFlow for input contexts.
             */
        }
        return results;
    }

    protected List<Context> createOutContexts(List<fr.zelus.jarvis.intent.Context> contexts) {
        List<Context> results = new ArrayList<>();
        for (fr.zelus.jarvis.intent.Context context : contexts) {
            /*
             * Use a dummy session to create the context.
             */
            ContextName contextName = ContextName.of(projectId, SessionName.of(projectId, "setup").getSession(),
                    context.getName());
            Context dialogFlowContext = Context.newBuilder().setName(contextName.toString()).setLifespanCount(context
                    .getLifeSpan()).build();
            results.add(dialogFlowContext);
        }
        return results;
    }

    protected List<Intent.Parameter> createParameters(List<fr.zelus.jarvis.intent.Context> contexts) {
        List<Intent.Parameter> results = new ArrayList<>();
        for (fr.zelus.jarvis.intent.Context context : contexts) {
            for (ContextParameter contextParameter : context.getParameters()) {
                Intent.Parameter parameter = Intent.Parameter.newBuilder().setDisplayName(contextParameter.getName())
                        .setEntityTypeDisplayName(contextParameter.getEntityType()).setValue("$" + contextParameter
                                .getName()).build();
                results.add(parameter);
            }
        }
        return results;
    }

    /**
     * Adapts the provided {@code intentDefinitionName} by replacing its {@code _} by spaces.
     * <p>
     * This method is used to deserialize names that have been serialized by
     * {@link fr.zelus.jarvis.core.EventDefinitionRegistry#adaptEventName(String)}.
     *
     * @param intentDefinitionName the {@link IntentDefinition} name to adapt
     * @return the adapted {@code intentDefinitionName}
     */
    private String adaptIntentDefinitionNameToDialogFlow(String intentDefinitionName) {
        return intentDefinitionName.replaceAll("_", " ");
    }

    /**
     * Deletes the {@link Intent} matching the provided {@code intentDefinition} from the DialogFlow project.
     * <p>
     * <b>Note:</b> this method does not train the underlying DialogFlow Machine Learning Engine, so multiple calls
     * to this method are not generating multiple training calls. Once all the {@link IntentDefinition}s have been
     * deleted from the DialogFlow project use {@link #trainMLEngine()} to train the ML Engine.
     *
     * @param intentDefinition the {@link IntentDefinition} to delete from the DialogFlow project
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     * @see #trainMLEngine()
     */
    public void deleteIntentDefinition(IntentDefinition intentDefinition) {
        if (isShutdown()) {
            throw new DialogFlowException(MessageFormat.format("Cannot delete the Intent {0}, the DialogFlow API is " +
                    "shutdown", intentDefinition.getName()));
        }
        checkNotNull(intentDefinition, "Cannot delete the IntentDefinition null");
        checkNotNull(intentDefinition.getName(), "Cannot delete the IntentDefinition with null as its name");
        List<Intent> registeredIntents = getRegisteredIntents();
        for (Intent intent : registeredIntents) {
            if (intent.getDisplayName().equals(intentDefinition.getName())) {
                Log.info("Deleting intent {0}", intentDefinition.getName());
                intentsClient.deleteIntent(intent.getName());
                Log.info("Intent {0} deleted", intentDefinition.getName());
                return;
            }
        }
        Log.warn("Cannot delete the Intent {0}, the intent does not exist", intentDefinition.getName());
    }

    /**
     * Sends a training query to the DialogFlow ML Engine and waits for its completion.
     * <p>
     * This method checks every second whether the underlying ML Engine has finished its training. Note that this
     * method is blocking as long as the ML Engine training is not terminated, and may not terminate if an issue
     * occurred on the DialogFlow side.
     *
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     */
    public void trainMLEngine() {
        if (isShutdown()) {
            throw new DialogFlowException("Cannot train the ML Engine, the DialogFlow API is shutdown");
        }
        Log.info("Starting ML Engine Training (this may take a few minutes)");
        TrainAgentRequest request = TrainAgentRequest.newBuilder()
                .setParent(projectName.toString())
                .build();
        ApiFuture<Operation> future = agentsClient.trainAgentCallable().futureCall(request);
        try {
            Operation operation = future.get();
            while (!operation.getDone()) {
                Thread.sleep(1000);
                /*
                 * Retrieve the new version of the Operation from the API.
                 */
                operation = agentsClient.getOperationsClient().getOperation(operation.getName());
            }
            Log.info("ML Engine Training completed");
        } catch (InterruptedException | ExecutionException e) {
            String errorMessage = "An error occurred during the ML Engine Training";
            Log.error(errorMessage);
            throw new DialogFlowException(errorMessage, e);
        }
    }

    /**
     * Creates a new {@link JarvisSession} for the provided {@code userId}.
     * <p>
     * The created session wraps the internal DialogFlow session that is used on the DialogFlow project to retrieve
     * conversation parts from a given user.
     * <p>
     * The returned {@link JarvisSession} is configured by the global {@link Configuration} provided in
     * {@link #DialogFlowApi(JarvisCore, Configuration)}.
     *
     * @param sessionId the identifier to create a session for
     * @return a new {@link JarvisSession} for the provided {@code userId}
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     */
    public JarvisSession createSession(String sessionId) {
        if (isShutdown()) {
            throw new DialogFlowException("Cannot create a new Session, the DialogFlow API is shutdown");
        }
        SessionName sessionName = SessionName.of(projectId, sessionId);
        Log.info("New session created with path {0}", sessionName.toString());
        return new DialogFlowSession(sessionName, configuration);
    }

    /**
     * Shuts down the DialogFlow clients and invalidates the session.
     * <p>
     * <b>Note:</b> calling this method invalidates the DialogFlow connection, and thus this class cannot be used to
     * access DialogFlow API anymore.
     */
    public void shutdown() {
        if (isShutdown()) {
            throw new DialogFlowException("Cannot perform shutdown, DialogFlow API is already shutdown");
        }
        this.sessionsClient.shutdownNow();
        this.intentsClient.shutdownNow();
        this.agentsClient.shutdownNow();
    }

    /**
     * Returns whether the DialogFlow client is shutdown.
     *
     * @return {@code true} if the DialogFlow client is shutdown, {@code false} otherwise
     */
    public boolean isShutdown() {
        return this.sessionsClient.isShutdown() || this.intentsClient.isShutdown() || this.agentsClient.isShutdown();
    }

    /**
     * Returns the {@link RecognizedIntent} extracted from the provided {@code text}
     * <p>
     * The returned {@link RecognizedIntent} is constructed from the raw {@link Intent} returned by the DialogFlow
     * API, using the mapping defined in {@link #convertDialogFlowIntentToRecognizedIntent(QueryResult)}.
     * {@link RecognizedIntent}s are used
     * to wrap the Intents returned by the Intent Recognition APIs and decouple the application from the concrete API
     * used.
     * <p>
     * This method uses the provided {@code session} to extract contextual {@link Intent}s, such as follow-up
     * or context-based {@link Intent}s.
     *
     * @param text    a {@link String} representing the textual input to process and extract the {@link Intent} from
     * @param session the {@link JarvisSession} wrapping the underlying DialogFlow session
     * @return a {@link RecognizedIntent} extracted from the provided input {@code text}
     * @throws NullPointerException     if the provided {@code text} or {@code session} is {@code null}
     * @throws IllegalArgumentException if the provided {@code text} is empty
     * @throws DialogFlowException      if the {@link DialogFlowApi} is shutdown or if an exception is thrown by the
     *                                  underlying DialogFlow engine
     */
    public RecognizedIntent getIntent(String text, JarvisSession session) {
        if (isShutdown()) {
            throw new DialogFlowException("Cannot extract an Intent from the provided input, the DialogFlow API is " +
                    "shutdown");
        }
        checkNotNull(text, "Cannot retrieve the intent from null");
        checkNotNull(session, "Cannot retrieve the intent using null as a session");
        checkArgument(!text.isEmpty(), "Cannot retrieve the intent from empty string");
        checkArgument(session instanceof DialogFlowSession, "Cannot handle the message, expected session type to be " +
                "%s, found %s", DialogFlowSession.class.getSimpleName(), session.getClass().getSimpleName());
        TextInput.Builder textInput = TextInput.newBuilder().setText(text).setLanguageCode(languageCode);
        QueryInput queryInput = QueryInput.newBuilder().setText(textInput).build();
        DetectIntentResponse response;
        try {
            response = sessionsClient.detectIntent(((DialogFlowSession) session).getSessionName(), queryInput);
        } catch (Exception e) {
            throw new DialogFlowException(e);
        }
        QueryResult queryResult = response.getQueryResult();
        Log.info("====================\n" +
                "Query Text: {0} \n" +
                "Detected Intent: {1} (confidence: {2})\n" +
                "Fulfillment Text: {3}", queryResult.getQueryText(), queryResult.getIntent()
                .getDisplayName(), queryResult.getIntentDetectionConfidence(), queryResult.getFulfillmentText());
        return convertDialogFlowIntentToRecognizedIntent(queryResult);
    }

    private IntentDefinition convertDialogFlowIntentToIntentDefinition(Intent intent) {
        if (nonNull(intent)) {
            IntentDefinition result = jarvisCore.getEventDefinitionRegistry().getIntentDefinition(intent
                    .getDisplayName());
            if (isNull(result)) {
                Log.warn("Cannot retrieve the {0} with the provided name {1}, returning the Default Fallback Intent",
                        IntentDefinition.class.getSimpleName(), intent.getDisplayName());
                result = DEFAULT_FALLBACK_INTENT;
            }
            return result;
        } else {
            Log.warn("Cannot convert null to IntentDefinition");
            return null;
        }
    }

    private ContextParameter getContextParameter(String contextName, String parameterName) {
        EventDefinitionRegistry eventDefinitionRegistry = jarvisCore.getEventDefinitionRegistry();
        for (IntentDefinition intentDefinition : eventDefinitionRegistry.getAllIntentDefinitions()) {
            for (fr.zelus.jarvis.intent.Context context : intentDefinition.getOutContexts()) {
                /*
                 * Use toLowerCase() because context are stored in lower case by DialogFlow
                 */
                if (context.getName().toLowerCase().equals(contextName)) {
                    for (ContextParameter parameter : context.getParameters()) {
                        if (parameter.getName().equals(parameterName)) {
                            return parameter;
                        }
                    }
                }
            }
        }
        /*
         * DialogFlow merges all the parameters in the current contexts, so we can have context containing parameter
         * keys that are not defined in the module model. We should ignore these parameter accesses, it is not
         * straightforward to access a merged key, we should use its defining context.
         */
        Log.warn("Unable to find the context parameter {0}.{1}", contextName, parameterName);
        return null;
    }

    private RecognizedIntent convertDialogFlowIntentToRecognizedIntent(QueryResult result) {
        Intent intent = result.getIntent();
        if (nonNull(intent)) {
            RecognizedIntent recognizedIntent = intentFactory.createRecognizedIntent();
            /*
             * Retrieve the IntentDefinition corresponding to this Intent.
             */
            IntentDefinition intentDefinition = convertDialogFlowIntentToIntentDefinition(intent);
            if (isNull(intentDefinition)) {
                String errorMessage = MessageFormat.format("Cannot retrieve the IntentDefinition associated to the " +
                        "provided DialogFlow Intent {0}", intent.getDisplayName());
                Log.error(errorMessage);
                return null;
            }
            recognizedIntent.setDefinition(intentDefinition);
            /*
             * Set the output context values.
             */
            for (Context context : result.getOutputContextsList()) {
                String contextName = ContextName.parse(context.getName()).getContext();
                Map<String, Value> parameterValues = context.getParameters().getFieldsMap();
                for (String key : parameterValues.keySet()) {
                    /*
                     * Ignore original: this variable contains the raw parsed value, we don't need this.
                     */
                    if (!key.contains(".original")) {
                        String parameterValue = parameterValues.get(key).getStringValue();
                        ContextParameter contextParameter = getContextParameter(contextName, key);
                        if (nonNull(contextParameter)) {
                            ContextParameterValue contextParameterValue = intentFactory.createContextParameterValue();
                            contextParameterValue.setValue(parameterValue);
                            contextParameterValue.setContextParameter(contextParameter);
                            recognizedIntent.getOutContextValues().add(contextParameterValue);
                        }
                    }
                }
            }
            return recognizedIntent;
        } else {
            Log.warn("Cannot convert null to a RecognizedIntent");
            return null;
        }
    }

    /**
     * Closes the DialogFlow session if it is not shutdown yet.
     */
    @Override
    protected void finalize() {
        if (!sessionsClient.isShutdown()) {
            Log.warn("DialogFlow session was not closed properly, calling automatic shutdown");
            this.sessionsClient.shutdownNow();
        }
        if (!intentsClient.isShutdown()) {
            Log.warn("DialogFlow Intent client was not closed properly, calling automatic shutdown");
            this.intentsClient.shutdownNow();
        }
        if (!agentsClient.isShutdown()) {
            Log.warn("DialogFlow Agent client was not closed properly, calling automatic shutdown");
            this.agentsClient.shutdownNow();
        }
    }
}
