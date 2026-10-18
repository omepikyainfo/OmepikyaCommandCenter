package com.omepikya.commandcenter;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.omepikya.commandcenter.automation.AutomationCore;
import com.omepikya.commandcenter.automation.AutomationReceiver;
import com.omepikya.commandcenter.automation.AutomationTask;
import com.omepikya.commandcenter.automation.ProactiveAssistant;
import com.omepikya.commandcenter.core.CommandBrain;
import com.omepikya.commandcenter.core.CommandResult;
import com.omepikya.commandcenter.execution.ExecutionControlCenter;
import com.omepikya.commandcenter.execution.ExecutionEvent;
import com.omepikya.commandcenter.execution.ExecutionResult;
import com.omepikya.commandcenter.execution.ExecutionTrace;
import com.omepikya.commandcenter.planning.CommandPlan;
import com.omepikya.commandcenter.planning.CommandStep;
import com.omepikya.commandcenter.router.ActionRegistry;
import com.omepikya.commandcenter.router.AutomationAction;
import com.omepikya.commandcenter.router.CommunicationAction;
import com.omepikya.commandcenter.router.DeviceAction;
import com.omepikya.commandcenter.router.MediaAction;
import com.omepikya.commandcenter.router.NavigationAction;
import com.omepikya.commandcenter.router.OpenAppAction;
import com.omepikya.commandcenter.router.SystemSettingsAction;
import com.omepikya.commandcenter.router.WebSearchAction;
import com.omepikya.commandcenter.security.SafetyDecision;
import com.omepikya.commandcenter.security.SafetyGuard;
import com.omepikya.commandcenter.ui.command.CommandView;
import com.omepikya.commandcenter.voice.VoiceEngine;

import java.text.DateFormat;
import java.util.List;
import java.util.Locale;

public class MainActivity
        extends AppCompatActivity
        implements CommandView.Listener,
        VoiceEngine.Listener {

    private static final int MICROPHONE_REQUEST_CODE =
            2001;

    private static final int CONTACTS_REQUEST_CODE =
            2002;

    private CommandBrain commandBrain;

    private CommandView commandView;

    private VoiceEngine voiceEngine;

    private AutomationCore automationCore;

    private ProactiveAssistant proactiveAssistant;

    private String pendingCommunicationCommand;

    private ExecutionControlCenter executionControlCenter;

    private final SafetyGuard safetyGuard =
            new SafetyGuard();

    private String lastSubmittedCommand = "";

    private String lastSpokenMessage = "";

    private boolean textToSpeechActive = false;

    /*
     * TRUE when the current command originated from
     * the microphone.
     *
     * This lets the confirmation system automatically
     * start listening after the confirmation prompt.
     */
    private boolean lastCommandCameFromVoice = false;

    /*
     * TRUE only while we are waiting for the user to
     * answer a voice confirmation prompt.
     */
    private boolean waitingForVoiceConfirmation = false;

    private final Handler mainHandler =
            new Handler(
                    Looper.getMainLooper()
            );

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );

        setTitle("OMEPIKYA");

        setupCommandCenter();

        if (savedInstanceState != null) {

            String savedCommand =
                    savedInstanceState.getString(
                            "command_input",
                            ""
                    );

            if (commandView != null
                    && savedCommand != null) {

                commandView.setCommand(
                        savedCommand
                );
            }

            lastSubmittedCommand =
                    savedInstanceState.getString(
                            "last_submitted_command",
                            ""
                    );

            pendingCommunicationCommand =
                    savedInstanceState.getString(
                            "pending_communication_command",
                            null
                    );

            lastCommandCameFromVoice =
                    savedInstanceState.getBoolean(
                            "last_command_came_from_voice",
                            false
                    );

            waitingForVoiceConfirmation =
                    savedInstanceState.getBoolean(
                            "waiting_for_voice_confirmation",
                            false
                    );
        }

        handleAutomationIntent(
                getIntent()
        );
    }

    @Override
    protected void onNewIntent(
            Intent intent
    ) {

        super.onNewIntent(
                intent
        );

        setIntent(
                intent
        );

        handleAutomationIntent(
                intent
        );
    }

    private void setupCommandCenter() {

        ActionRegistry actionRegistry =
                new ActionRegistry();

        automationCore =
                new AutomationCore(
                        this
                );

        /*
         * Phase 1
         */
        actionRegistry.register(
                new OpenAppAction(
                        this
                )
        );

        actionRegistry.register(
                new SystemSettingsAction(
                        this
                )
        );

        /*
         * Phase 2
         */
        actionRegistry.register(
                new CommunicationAction(
                        this
                )
        );

        actionRegistry.register(
                new NavigationAction(
                        this
                )
        );

        actionRegistry.register(
                new MediaAction(
                        this
                )
        );

        actionRegistry.register(
                new WebSearchAction(
                        this
                )
        );

        actionRegistry.register(
                new DeviceAction(
                        this
                )
        );

        actionRegistry.register(
                new AutomationAction(
                        this
                )
        );

        /*
         * Phase 4B
         */
        commandBrain =
                new CommandBrain(
                        this,
                        actionRegistry
                );

        /*
         * Phase 5:
         *
         * Pass CommandBrain's existing ContextEngine.
         *
         * CommandBrain and ProactiveAssistant therefore
         * operate using one shared context instance.
         */
        proactiveAssistant =
                new ProactiveAssistant(
                        this,
                        commandBrain.getContextEngine(),
                        commandBrain.getMemoryManager()
                );

        executionControlCenter =
                new ExecutionControlCenter(
                        commandBrain.getExecutionCoordinator()
                );

        executionControlCenter.setListener(
                new ExecutionControlCenter.Listener() {

                    @Override
                    public void onExecutionEvent(
                            ExecutionEvent event
                    ) {

                        runOnUiThread(() -> {

                            commandView.addExecutionEvent(
                                    event
                            );

                            commandView.updateExecutionMonitor(
                                    executionControlCenter.getMonitor()
                            );
                        });
                    }

                    @Override
                    public void onExecutionFinished(
                            ExecutionResult result,
                            ExecutionTrace trace
                    ) {

                        runOnUiThread(() -> {

                            commandView.setExecutionActive(
                                    false
                            );

                            commandView.updateExecutionMonitor(
                                    executionControlCenter.getMonitor()
                            );

                            if (result != null) {

                                commandView.setExecutionResultState(
                                        result.isSuccess(),
                                        result.getStatus()
                                                == com.omepikya.commandcenter.execution.ExecutionStatus.CANCELLED
                                );
                            }
                        });
                    }
                }
        );

        commandView =
                new CommandView(
                        this,
                        this
                );

        LinearLayout root =
                new LinearLayout(
                        this
                );

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                )
        );

        root.addView(
                commandView.getView(),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f
                )
        );

        setContentView(
                root
        );

        proactiveAssistant.setListener(
                suggestion -> {

                    if (suggestion == null) {
                        return;
                    }

                    commandView.showProactiveSuggestion(
                            suggestion.getMessage(),
                            suggestion.getCommand(),
                            suggestion.getScore(),
                            suggestion.getType() == null
                                    ? "context"
                                    : suggestion.getType().name()
                    );
                }
        );

        try {

            voiceEngine =
                    new VoiceEngine(
                            this
                    );

            voiceEngine.setListener(
                    this
            );

        } catch (Exception e) {

            voiceEngine = null;

            commandView.setResult(
                    "Voice recognition is unavailable on this device."
            );
        }

        commandView.setBrainStatus(
                commandBrain != null
        );

        commandView.setRouterStatus(
                actionRegistry != null
        );

        commandView.setSystemBridgeStatus(
                true
        );

        commandView.setVoiceStatus(
                voiceEngine != null
        );

        commandView.setVoiceRecognitionMode(
                voiceEngine != null,
                voiceEngine != null
                        && voiceEngine.isUsingOnDeviceSpeechRecognition()
        );

        commandView.setAutomationStatus(
                automationCore != null
        );
    }

    @Override
    protected void onResume() {

        super.onResume();

        /*
         * Restore scheduled alarms.
         */
        if (automationCore != null) {

            try {

                automationCore.restoreAlarms();

            } catch (Exception ignored) {
            }
        }

        /*
         * Tell the shared context which screen
         * the user is currently using.
         */
        if (proactiveAssistant != null) {

            proactiveAssistant.setCurrentScreen(
                    "Command Center"
            );

            proactiveAssistant.check();
        }
    }

    private void openHelp() {

        Intent intent =
                new Intent(
                        this,
                        HelpActivity.class
                );

        startActivity(
                intent
        );
    }

    private void handleAutomationIntent(
            Intent intent
    ) {

        if (intent == null) {
            return;
        }

        String command =
                intent.getStringExtra(
                        AutomationReceiver.EXTRA_COMMAND
                );

        if (command == null
                || command.trim().isEmpty()) {

            return;
        }

        commandView.setCommand(
                command
        );

        boolean autoExecute =
                intent.getBooleanExtra(
                        AutomationReceiver.EXTRA_AUTO_EXECUTE,
                        false
                );

        if (autoExecute) {

            /*
             * Scheduled commands are not voice commands.
             */
            lastCommandCameFromVoice = false;
            waitingForVoiceConfirmation = false;

            commandView.setResult(
                    "Scheduled command is executing..."
            );

            onExecuteCommand(
                    command
            );

        } else {

            commandView.setResult(
                    "Scheduled command ready."
            );
        }
    }

    @Override
    public void onTinyChatRequested() {
        startActivity(new Intent(this, TinyChatActivity.class));
    }

    @Override
    public void onExecuteCommand(
            String command
    ) {

        if (command == null
                || command.trim().isEmpty()) {

            return;
        }

        /*
         * This callback normally comes from the text command
         * field / EXECUTE button.
         *
         * A microphone command uses onCommandReceived()
         * which explicitly marks the command as voice-originated.
         */
        if (!lastCommandCameFromVoice) {

            waitingForVoiceConfirmation = false;
        }

        SafetyDecision safetyDecision =
                safetyGuard.check(
                        command
                );

        if (!safetyDecision.isAllowed()) {

            commandView.setResult(
                    "Command blocked: "
                            + safetyDecision.getReason()
            );

            return;
        }

        if (safetyDecision.requiresConfirmation()) {

            showSafetyConfirmation(
                    command,
                    safetyDecision
            );

            return;
        }

        submitCommandAfterChecks(
                command
        );
    }

    private void submitCommandAfterChecks(
            String command
    ) {

        if (command == null
                || command.trim().isEmpty()) {

            return;
        }

        if (needsContactPermission(
                command
        )) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED) {

                pendingCommunicationCommand =
                        command;

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.READ_CONTACTS
                        },
                        CONTACTS_REQUEST_CODE
                );

                commandView.setResult(
                        "Contacts permission is required to find people by name."
                );

                return;
            }
        }

        executeCommand(
                command
        );
    }

    private void executeCommand(
            String command
    ) {

        if (command == null
                || command.trim().isEmpty()) {

            return;
        }

        final String originalCommand =
                command.trim();

        lastSubmittedCommand =
                originalCommand;

        commandView.setExecutionActive(
                true
        );

        commandView.resetExecutionPresentation();

        commandView.setResult(
                "Processing command..."
        );

        new Thread(() -> {

            String resolvedCommand =
                    originalCommand;

            CommandResult result = null;

            try {

                /*
                 * Confirmation replies must go directly to
                 * CommandBrain.
                 *
                 * Do not allow proactive/context follow-up
                 * resolution to rewrite YES/NO.
                 */
                boolean confirmationReply =
                        commandBrain.isAwaitingConfirmation()
                                && isConfirmationReply(
                                originalCommand
                        );

                if (!confirmationReply
                        && proactiveAssistant != null) {

                    resolvedCommand =
                            proactiveAssistant.resolveFollowUp(
                                    originalCommand
                            );
                }

                if (resolvedCommand == null
                        || resolvedCommand.trim().isEmpty()) {

                    resolvedCommand =
                            originalCommand;
                }

                final String commandForBrain =
                        resolvedCommand;

                result =
                        commandBrain.process(
                                commandForBrain
                        );

                final CommandResult finalResult =
                        result;

                final String finalCommand =
                        commandForBrain;

                runOnUiThread(() -> {

                    if (finalResult == null) {

                        commandView.setResult(
                                "No result returned."
                        );

                        return;
                    }

                    String message =
                            finalResult.getMessage();

                    if (message == null
                            || message.trim().isEmpty()) {

                        message =
                                finalResult.isSuccess()
                                        ? "Command completed."
                                        : "Command failed.";
                    }

                    boolean awaitingConfirmation =
                            commandBrain.isAwaitingConfirmation();

                    /*
                     * Do not record a command as a completed
                     * proactive interaction while the Brain is
                     * still waiting for confirmation.
                     */
                    if (proactiveAssistant != null
                            && !awaitingConfirmation) {

                        proactiveAssistant.recordCommand(
                                finalCommand,
                                commandBrain.getContextEngine()
                                        .getLastIntent(),
                                commandBrain.getContextEngine()
                                        .getLastAction(),
                                commandBrain.getContextEngine()
                                        .getLastEntity(),
                                finalResult.isSuccess(),
                                message
                        );
                    }

                    commandView.setResult(
                            message
                    );

                    commandView.clearCommandInput();

                    commandView.setConfirmationRequired(
                            awaitingConfirmation
                    );

                    lastSpokenMessage =
                            message;

                    try {

                        commandView.setInterpretation(
                                commandBrain.getContextEngine()
                                        .getLastIntent(),
                                commandBrain.getContextEngine()
                                        .getLastAction(),
                                commandBrain.getContextEngine()
                                        .getLastEntity()
                        );

                    } catch (Exception ignored) {
                    }

                    /*
                     * If the Brain is waiting for confirmation
                     * and the original command came from voice,
                     * remember that we must automatically listen
                     * after TTS finishes.
                     */
                    if (awaitingConfirmation
                            && lastCommandCameFromVoice) {

                        waitingForVoiceConfirmation =
                                true;

                    } else {

                        waitingForVoiceConfirmation =
                                false;
                    }

                    /*
                     * Speak the result.
                     */
                    if (voiceEngine != null
                            && voiceEngine.isTextToSpeechReady()) {

                        textToSpeechActive =
                                true;

                        voiceEngine.speak(
                                message
                        );

                    } else {

                        textToSpeechActive =
                                false;

                        /*
                         * If TTS isn't available, do not leave
                         * a voice confirmation waiting forever.
                         */
                        if (awaitingConfirmation
                                && lastCommandCameFromVoice) {

                            waitingForVoiceConfirmation =
                                    false;

                            startConfirmationListening();
                        }
                    }

                    mainHandler.postDelayed(
                            () -> {

                                if (proactiveAssistant != null
                                        && executionControlCenter != null
                                        && !executionControlCenter.isActive()) {

                                    proactiveAssistant.check();
                                }

                            },
                            1500L
                    );
                });

            } catch (Exception e) {

                final String error =
                        e.getMessage() == null
                                || e.getMessage()
                                .trim()
                                .isEmpty()
                                ? "Command execution failed."
                                : e.getMessage().trim();

                if (proactiveAssistant != null) {

                    proactiveAssistant.recordCommand(
                            resolvedCommand,
                            null,
                            null,
                            null,
                            false,
                            error
                    );
                }

                runOnUiThread(() ->
                        commandView.setResult(
                                error
                        )
                );

            } finally {

                final ExecutionResult latest =
                        commandBrain.getLastExecutionResult();

                final ExecutionTrace trace =
                        commandBrain.getLastExecutionTrace();

                boolean awaitingConfirmation =
                        commandBrain.isAwaitingConfirmation();

                if (!awaitingConfirmation
                        && executionControlCenter != null) {

                    executionControlCenter.complete(
                            latest,
                            trace
                    );

                } else if (!awaitingConfirmation) {

                    runOnUiThread(() ->
                            commandView.setExecutionActive(
                                    false
                            )
                    );
                }
            }

        }).start();
    }

    private boolean isConfirmationReply(
            String command
    ) {

        if (command == null) {
            return false;
        }

        String value =
                command.trim()
                        .toLowerCase(
                                Locale.getDefault()
                        );

        return value.equals("yes")
                || value.equals("yeah")
                || value.equals("yep")
                || value.equals("go ahead")
                || value.equals("do it")
                || value.equals("okay")
                || value.equals("ok")
                || value.equals("no")
                || value.equals("nope")
                || value.equals("don't")
                || value.equals("cancel")
                || value.equals("stop")
                || value.equals("never mind")
                || value.equals("nevermind");
    }

    private boolean needsContactPermission(
            String command
    ) {

        if (command == null) {

            return false;
        }

        String text =
                command.trim()
                        .toLowerCase();

        return text.startsWith("call ")
                || text.startsWith("phone ")
                || text.startsWith("dial ")
                || text.startsWith("ring ")
                || (
                text.startsWith("give ")
                        && text.contains(" a call")
        )
                || text.startsWith("message ")
                || text.startsWith("text ")
                || text.startsWith("sms ")
                || text.startsWith("send a message to ")
                || text.startsWith("send message to ")
                || text.startsWith("send an sms to ")
                || text.startsWith("send sms to ")
                || text.startsWith("send whatsapp message to ")
                || text.startsWith("send a whatsapp message to ")
                || text.startsWith("whatsapp ");
    }

    @Override
    public void onCancelExecution() {

        if (executionControlCenter == null
                || !executionControlCenter.isActive()) {

            commandView.setResult(
                    "No active execution to cancel."
            );

            return;
        }

        executionControlCenter.cancel();

        commandView.setResult(
                "Cancellation requested..."
        );
    }

    @Override
    public void onPlanRequested(
            String command
    ) {

        if (command == null
                || command.trim().isEmpty()) {

            return;
        }

        new Thread(() -> {

            CommandPlan plan = null;

            try {

                plan =
                        commandBrain
                                .getIntelligentPlanner()
                                .plan(command);

            } catch (Exception ignored) {
            }

            final CommandPlan finalPlan =
                    plan;

            runOnUiThread(() ->
                    showPlanPreview(
                            finalPlan,
                            command
                    )
            );

        }).start();
    }

    private void showPlanPreview(
            CommandPlan plan,
            String command
    ) {

        if (plan == null
                || plan.getSteps().isEmpty()) {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "Plan Preview"
                    )
                    .setMessage(
                            "No multi-step plan was generated. "
                                    + "The command can still be executed normally."
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

            return;
        }

        StringBuilder text =
                new StringBuilder();

        int index = 1;

        for (CommandStep step :
                plan.getSteps()) {

            if (step == null) {
                continue;
            }

            text.append(index++)
                    .append(". ")
                    .append(step.getCommand())
                    .append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "Plan Preview"
                )
                .setMessage(
                        text.toString().trim()
                )
                .setNegativeButton(
                        "CLOSE",
                        null
                )
                .setPositiveButton(
                        "EXECUTE",
                        (dialog, which) ->
                                onExecuteCommand(
                                        command
                                )
                )
                .show();
    }

    @Override
    public void onExecutionDetailsRequested() {

        showExecutionDetails();
    }

    @Override
    public void onRetryRequested() {

        if (lastSubmittedCommand == null
                || lastSubmittedCommand
                .trim()
                .isEmpty()) {

            commandView.setResult(
                    "There is no command to retry."
            );

            return;
        }

        /*
         * Retry from the UI is a normal manual action.
         */
        lastCommandCameFromVoice =
                false;

        waitingForVoiceConfirmation =
                false;

        onExecuteCommand(
                lastSubmittedCommand
        );
    }

    @Override
    public void onConfirmationAccepted() {

        /*
         * The existing on-screen CONFIRM button.
         *
         * Keep the voice-origin flag intact because the
         * pending command may have originally come from voice.
         */
        commandView.setConfirmationRequired(
                false
        );

        commandView.setResult(
                "Confirmation accepted. Executing..."
        );

        waitingForVoiceConfirmation =
                false;

        executeCommand(
                "yes"
        );
    }

    @Override
    public void onConfirmationRejected() {

        /*
         * The existing on-screen CANCEL button.
         */
        commandView.setConfirmationRequired(
                false
        );

        commandView.setResult(
                "Confirmation rejected. Command cancelled."
        );

        waitingForVoiceConfirmation =
                false;

        executeCommand(
                "no"
        );
    }

    @Override
    public void onProactiveSuggestionAccepted(
            String command
    ) {

        if (proactiveAssistant != null) {

            proactiveAssistant.dismissSuggestion();
        }

        if (command == null
                || command.trim().isEmpty()) {

            commandView.setResult(
                    "This suggestion has no executable command."
            );

            return;
        }

        /*
         * Proactive suggestions are not microphone commands.
         */
        lastCommandCameFromVoice =
                false;

        waitingForVoiceConfirmation =
                false;

        commandView.setCommand(
                command
        );

        onExecuteCommand(
                command
        );
    }

    @Override
    public void onProactiveSuggestionDismissed() {

        if (proactiveAssistant != null) {

            proactiveAssistant.dismissSuggestion();
        }
    }

    @Override
    public void onProactiveSettingsRequested() {

        Intent intent =
                new Intent(
                        this,
                        ProactiveSettingsActivity.class
                );

        startActivity(
                intent
        );
    }

    private void showSafetyConfirmation(
            String command,
            SafetyDecision decision
    ) {

        String risk =
                decision.getRisk() == null
                        ? "ELEVATED"
                        : decision.getRisk().name();

        new AlertDialog.Builder(this)
                .setTitle(
                        "Confirmation Required"
                )
                .setMessage(
                        "Risk: "
                                + risk
                                + "\n\n"
                                + decision.getReason()
                                + "\n\nCommand:\n"
                                + command
                )
                .setNegativeButton(
                        "CANCEL",
                        (dialog, which) ->
                                commandView.setResult(
                                        "Command cancelled before execution."
                                )
                )
                .setPositiveButton(
                        "CONFIRM",
                        (dialog, which) ->
                                submitCommandAfterChecks(
                                        command
                                )
                )
                .show();
    }

    private void showExecutionDetails() {

        ExecutionTrace trace =
                commandBrain == null
                        ? null
                        : commandBrain.getLastExecutionTrace();

        ExecutionResult result =
                commandBrain == null
                        ? null
                        : commandBrain.getLastExecutionResult();

        if (trace == null) {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "Execution Details"
                    )
                    .setMessage(
                            "No execution trace is available yet."
                    )
                    .setPositiveButton(
                            "CLOSE",
                            null
                    )
                    .show();

            return;
        }

        StringBuilder details =
                new StringBuilder();

        details.append(
                "Execution ID\n"
        )
                .append(
                        trace.getExecutionId()
                )
                .append("\n\n");

        details.append(
                "Command\n"
        )
                .append(
                        trace.getCommand()
                )
                .append("\n\n");

        details.append(
                "State\n"
        )
                .append(
                        trace.getState()
                )
                .append("\n\n");

        details.append(
                "Attempts\n"
        )
                .append(
                        trace.getAttemptCount()
                )
                .append("\n\n");

        details.append(
                "Duration\n"
        )
                .append(
                        trace.getDurationMs()
                )
                .append(
                        " ms\n\n"
                );

        if (result != null) {

            details.append(
                    "Result\n"
            )
                    .append(
                            result.getMessage()
                    )
                    .append("\n\n");
        }

        details.append(
                "Execution Trace\n"
        );

        List<String> events =
                trace.getEvents();

        if (events == null
                || events.isEmpty()) {

            details.append(
                    "No trace events."
            );

        } else {

            for (String event :
                    events) {

                details.append(
                        event
                )
                        .append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "Execution Details"
                )
                .setMessage(
                        details.toString()
                )
                .setPositiveButton(
                        "CLOSE",
                        null
                )
                .setNeutralButton(
                        "RETRY",
                        (dialog, which) ->
                                onRetryRequested()
                )
                .show();
    }

    @Override
    public void onHistoryRequested() {

        showExecutionHistory();
    }

    @Override
    public void onAutomationsRequested() {

        showAutomations();
    }

    @Override
    public void onHelpRequested() {

        openHelp();
    }

    private void showExecutionHistory() {

        if (commandBrain == null
                || commandBrain.getExecutionHistory()
                == null) {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "Command History"
                    )
                    .setMessage(
                            "No command history is available yet."
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

            return;
        }

        List<ExecutionResult> results =
                commandBrain
                        .getExecutionHistory()
                        .getRecent(20);

        if (results == null
                || results.isEmpty()) {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "Command History"
                    )
                    .setMessage(
                            "No commands have been executed yet."
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

            return;
        }

        StringBuilder text =
                new StringBuilder();

        DateFormat timeFormat =
                DateFormat.getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT,
                        Locale.getDefault()
                );

        for (
                int i = results.size() - 1;
                i >= 0;
                i--
        ) {

            ExecutionResult result =
                    results.get(i);

            if (result == null) {
                continue;
            }

            text.append(
                    result.isSuccess()
                            ? "✓ "
                            : "✕ "
            )
                    .append(
                            result.getCommand()
                    )
                    .append("\n")
                    .append(
                            timeFormat.format(
                                    result.getTimestamp()
                            )
                    )
                    .append(" — ")
                    .append(
                            result.getMessage() == null
                                    ? "No result"
                                    : result.getMessage()
                    )
                    .append("\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "Recent Commands"
                )
                .setMessage(
                        text.toString().trim()
                )
                .setPositiveButton(
                        "CLOSE",
                        null
                )
                .show();
    }

    private void showAutomations() {

        if (automationCore == null) {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "Automations"
                    )
                    .setMessage(
                            "Automation Core is unavailable."
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

            return;
        }

        List<AutomationTask> tasks =
                automationCore.getTasks();

        if (tasks == null
                || tasks.isEmpty()) {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "Automations"
                    )
                    .setMessage(
                            "No scheduled automations found."
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

            return;
        }

        StringBuilder text =
                new StringBuilder();

        DateFormat timeFormat =
                DateFormat.getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT,
                        Locale.getDefault()
                );

        for (AutomationTask task :
                tasks) {

            if (task == null) {
                continue;
            }

            text.append(
                    task.isEnabled()
                            ? "● "
                            : "○ "
            )
                    .append(
                            task.getName()
                    )
                    .append("\n")
                    .append(
                            "Command: "
                    )
                    .append(
                            task.getCommand()
                    )
                    .append("\n")
                    .append(
                            "Status: "
                    )
                    .append(
                            task.getStatus()
                    )
                    .append("\n")
                    .append(
                            "Runs at: "
                    )
                    .append(
                            timeFormat.format(
                                    task.getTriggerTime()
                            )
                    )
                    .append("\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "Scheduled Automations"
                )
                .setMessage(
                        text.toString().trim()
                )
                .setPositiveButton(
                        "CLOSE",
                        null
                )
                .show();
    }

    @Override
    public void onVoiceRequested() {

        if (voiceEngine == null) {

            commandView.setResult(
                    "Voice recognition is unavailable."
            );

            return;
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    MICROPHONE_REQUEST_CODE
            );

            return;
        }

        /*
         * This is an explicit microphone request from
         * the user, so reset the previous confirmation
         * state before starting a fresh recognition session.
         */
        waitingForVoiceConfirmation =
                false;

        startVoiceRecognition();
    }

    private void startVoiceRecognition() {

        if (voiceEngine == null) {
            return;
        }

        try {

            commandView.setResult(
                    "Listening..."
            );

            voiceEngine.startListening();

        } catch (Exception e) {

            commandView.setResult(
                    "Voice recognition is unavailable."
            );
        }
    }

    /**
     * Automatically starts microphone recognition for
     * a pending YES/NO confirmation.
     *
     * This is the missing link that caused the problem
     * shown in the screenshot.
     */
    private void startConfirmationListening() {

        if (voiceEngine == null) {
            return;
        }

        if (commandBrain == null
                || !commandBrain.isAwaitingConfirmation()) {

            return;
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    MICROPHONE_REQUEST_CODE
            );

            return;
        }

        /*
         * SpeechRecognizer operations are dispatched
         * on Android's main thread.
         */
        mainHandler.post(() -> {

            if (isFinishing()) {
                return;
            }

            if (commandBrain == null
                    || !commandBrain.isAwaitingConfirmation()) {

                return;
            }

            try {

                commandView.setResult(
                        "Listening for YES or NO..."
                );

                voiceEngine.startListening();

            } catch (Exception e) {

                commandView.setResult(
                        "I couldn't start confirmation listening. "
                                + "Please tap VOICE and say yes or no."
                );
            }
        });
    }

    @Override
    public void onListening() {

        /*
         * A new recognition session has started.
         * Therefore the TTS completion state is no
         * longer active.
         */
        textToSpeechActive =
                false;

        commandView.setResult(
                waitingForVoiceConfirmation
                        ? "Listening for YES or NO..."
                        : "Listening..."
        );
    }

    @Override
    public void onCommandReceived(
            String command
    ) {

        if (command == null
                || command.trim().isEmpty()) {

            return;
        }

        /*
         * CRITICAL:
         *
         * Mark this command as voice-originated BEFORE
         * passing it into the normal command pipeline.
         *
         * If the Brain requests confirmation, the TTS
         * completion callback will then automatically
         * start another recognition session.
         */
        lastCommandCameFromVoice =
                true;

        commandView.setCommand(
                command
        );

        onExecuteCommand(
                command
        );
    }

    @Override
    public void onSpeechError(
            String error
    ) {

        textToSpeechActive =
                false;

        /*
         * If the Brain is waiting for confirmation,
         * give a confirmation-specific recovery message.
         */
        if (commandBrain != null
                && commandBrain.isAwaitingConfirmation()) {

            waitingForVoiceConfirmation =
                    true;

            commandView.setResult(
                    "I didn't catch that. "
                            + "Please say YES or NO, or tap VOICE."
            );

            return;
        }

        if (error == null
                || error.trim().isEmpty()) {

            commandView.setResult(
                    "Voice recognition failed."
            );

            return;
        }

        commandView.setResult(
                error
        );
    }

    @Override
    public void onSpeaking() {

        textToSpeechActive =
                true;

        commandView.setVoiceSpeaking(
                true
        );
    }

    @Override
    public void onVoiceComplete() {

        /*
         * VoiceEngine uses this callback for both:
         *
         * 1. Speech-recognition completion
         * 2. Text-to-speech completion
         *
         * Only TTS completion should trigger the
         * automatic confirmation listener.
         */
        if (!textToSpeechActive) {

            return;
        }

        textToSpeechActive =
                false;

        if (lastSpokenMessage != null
                && !lastSpokenMessage.trim().isEmpty()) {

            commandView.setResult(
                    lastSpokenMessage
            );
        }

        /*
         * The confirmation prompt has finished speaking.
         *
         * Now automatically listen for YES/NO.
         */
        if (waitingForVoiceConfirmation
                && commandBrain != null
                && commandBrain.isAwaitingConfirmation()) {

            waitingForVoiceConfirmation =
                    false;

            startConfirmationListening();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode
                == MICROPHONE_REQUEST_CODE) {

            if (grantResults.length > 0
                    && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {

                /*
                 * If the permission was requested while
                 * waiting for YES/NO, resume confirmation
                 * listening. Otherwise start normal voice
                 * recognition.
                 */
                if (commandBrain != null
                        && commandBrain.isAwaitingConfirmation()) {

                    startConfirmationListening();

                } else {

                    startVoiceRecognition();
                }

            } else {

                Toast.makeText(
                        this,
                        "Microphone permission is required for voice commands.",
                        Toast.LENGTH_LONG
                ).show();

                commandView.setResult(
                        "Microphone permission denied."
                );

                waitingForVoiceConfirmation =
                        false;
            }

            return;
        }

        if (requestCode
                == CONTACTS_REQUEST_CODE) {

            if (grantResults.length > 0
                    && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {

                if (pendingCommunicationCommand
                        != null
                        && !pendingCommunicationCommand
                        .trim()
                        .isEmpty()) {

                    String command =
                            pendingCommunicationCommand;

                    pendingCommunicationCommand =
                            null;

                    commandView.setCommand(
                            command
                    );

                    executeCommand(
                            command
                    );
                }

            } else {

                pendingCommunicationCommand =
                        null;

                Toast.makeText(
                        this,
                        "Contacts permission is required to find people by name.",
                        Toast.LENGTH_LONG
                ).show();

                commandView.setResult(
                        "Contacts permission denied."
                );
            }
        }
    }

    @Override
    protected void onSaveInstanceState(
            @NonNull Bundle outState
    ) {

        super.onSaveInstanceState(
                outState
        );

        if (commandView != null) {

            outState.putString(
                    "command_input",
                    commandView.getCommand()
            );
        }

        outState.putString(
                "last_submitted_command",
                lastSubmittedCommand
        );

        outState.putString(
                "pending_communication_command",
                pendingCommunicationCommand
        );

        outState.putBoolean(
                "last_command_came_from_voice",
                lastCommandCameFromVoice
        );

        outState.putBoolean(
                "waiting_for_voice_confirmation",
                waitingForVoiceConfirmation
        );
    }

    @Override
    protected void onDestroy() {

        /*
         * Stop any pending confirmation-listening callback.
         */
        waitingForVoiceConfirmation =
                false;

        textToSpeechActive =
                false;

        mainHandler.removeCallbacksAndMessages(
                null
        );

        if (executionControlCenter != null) {

            executionControlCenter.destroy();

            executionControlCenter =
                    null;
        }

        if (voiceEngine != null) {

            voiceEngine.shutdown();

            voiceEngine =
                    null;
        }

        super.onDestroy();
    }
}