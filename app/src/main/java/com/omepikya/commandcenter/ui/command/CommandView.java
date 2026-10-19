package com.omepikya.commandcenter.ui.command;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.omepikya.commandcenter.execution.ExecutionEvent;
import com.omepikya.commandcenter.execution.ExecutionMonitor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CommandView {

    public interface Listener {

        void onExecuteCommand(String command);
        void onVoiceRequested();
        void onTinyChatRequested();
        void onHistoryRequested();
        void onAutomationsRequested();
        void onHelpRequested();
        void onCancelExecution();
        void onPlanRequested(String command);
        void onExecutionDetailsRequested();
        void onRetryRequested();
        void onConfirmationAccepted();
        void onConfirmationRejected();
        void onProactiveSuggestionAccepted(String command);
        void onProactiveSuggestionDismissed();
        void onProactiveSettingsRequested();
    }

    private final Context context;
    private final Listener listener;

    private EditText commandInput;
    private TextView resultText;
    private TextView consoleText;
    private TextView executionStateText;
    private TextView executionMetaText;
    private TextView brainStatus;
    private TextView routerStatus;
    private TextView bridgeStatus;
    private TextView voiceStatus;
    private TextView automationStatus;
    private Button cancelButton;
    private Button detailsButton;
    private Button retryButton;
    private LinearLayout confirmationRow;
    private TextView progressText;
    private LinearLayout progressBarContainer;
    private TextView interpretationText;
    private LinearLayout proactiveCard;
    private TextView proactiveMessage;
    private TextView proactiveMeta;
    private Button proactiveAcceptButton;

    private LinearLayout root;
    private LinearLayout statusContainer;
    private LinearLayout consoleContainer;
    private ScrollView scrollView;
    private ScrollView consoleScrollView;

    private boolean statusExpanded = true;
    private boolean consoleExpanded = true;

    private final List<String> consoleEntries =
            new ArrayList<>();

    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public CommandView(
            Context context,
            Listener listener) {

        if (context == null) {
            throw new IllegalArgumentException(
                    "Context cannot be null");
        }

        this.context = context;
        this.listener = listener;
        buildView();
    }

    private void buildView() {

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));

        scrollView.addView(
                root,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = textView(
                "Omepikya Command Center",
                27,
                0xFF4F4F4F,
                Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = textView(
                "Your command interface for Android",
                14,
                0xFF777777,
                Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(4);
        subtitleParams.bottomMargin = dp(16);
        root.addView(subtitle, subtitleParams);

        TextView commandLabel = textView(
                "COMMAND",
                12,
                0xFF777777,
                Typeface.BOLD);
        root.addView(commandLabel, matchWrap());

        commandInput = new EditText(context);
        commandInput.setHint("Type a command...");
        commandInput.setSingleLine(true);
        commandInput.setTextSize(18);
        commandInput.setPadding(dp(2), dp(6), dp(2), dp(6));
        root.addView(commandInput, matchWrap());

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);

        Button executeButton = new Button(context);
        executeButton.setText("EXECUTE");
        executeButton.setAllCaps(true);

        Button planButton = new Button(context);
        planButton.setText("PLAN");
        planButton.setAllCaps(true);

        Button voiceButton = new Button(context);
        voiceButton.setText("🎤 VOICE");
        voiceButton.setAllCaps(false);

        buttonRow.addView(executeButton, weightedButtonParams());
        buttonRow.addView(planButton, weightedButtonParams());
        buttonRow.addView(voiceButton, weightedButtonParams());

        LinearLayout.LayoutParams buttonRowParams = matchWrap();
        buttonRowParams.topMargin = dp(6);
        root.addView(buttonRow, buttonRowParams);

        Button tinyChatButton = new Button(context);
        tinyChatButton.setText("CHAT WITH TINY");
        tinyChatButton.setAllCaps(false);
        LinearLayout.LayoutParams tinyChatParams = matchWrap();
        tinyChatParams.topMargin = dp(4);
        root.addView(tinyChatButton, tinyChatParams);
        tinyChatButton.setOnClickListener(v -> {
            if (listener != null) listener.onTinyChatRequested();
        });

        TextView quickLabel = textView(
                "QUICK COMMANDS  →",
                12,
                0xFF777777,
                Typeface.BOLD);
        LinearLayout.LayoutParams quickLabelParams = matchWrap();
        quickLabelParams.topMargin = dp(16);
        root.addView(quickLabel, quickLabelParams);

        HorizontalScrollView quickScroll =
                new HorizontalScrollView(context);
        quickScroll.setHorizontalScrollBarEnabled(false);
        quickScroll.setFillViewport(false);

        LinearLayout quickRow =
                new LinearLayout(context);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);

        addQuickButton(quickRow, "Open Settings", "open settings");
        addQuickButton(quickRow, "Wi-Fi", "turn on wifi");
        addQuickButton(quickRow, "Bluetooth", "turn on bluetooth");
        addQuickButton(quickRow, "YouTube", "open youtube");
        addQuickButton(quickRow, "Time", "what time is it");

        quickScroll.addView(quickRow,
                new HorizontalScrollView.LayoutParams(
                        HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                        HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(quickScroll, matchWrap());

        buildProactiveSuggestionCard();

        LinearLayout statusHeader = headerRow(
                "SYSTEM STATUS",
                "▾");
        statusHeader.setOnClickListener(v -> toggleStatus());
        root.addView(statusHeader, matchWrap());

        statusContainer = new LinearLayout(context);
        statusContainer.setOrientation(LinearLayout.VERTICAL);

        brainStatus = addStatusRow(
                statusContainer,
                "🧠  Command Brain",
                "ONLINE");
        routerStatus = addStatusRow(
                statusContainer,
                "⚙  Action Router",
                "READY");
        bridgeStatus = addStatusRow(
                statusContainer,
                "📱  System Bridge",
                "READY");
        voiceStatus = addStatusRow(
                statusContainer,
                "🎙  Voice Engine",
                "READY");
        automationStatus = addStatusRow(
                statusContainer,
                "⏱  Automation Core",
                "READY");

        root.addView(statusContainer, matchWrap());

        LinearLayout consoleHeader = headerRow(
                "COMMAND CONSOLE",
                "▾");
        consoleHeader.setOnClickListener(v -> toggleConsole());
        root.addView(consoleHeader, matchWrap());

        consoleContainer = new LinearLayout(context);
        consoleContainer.setOrientation(LinearLayout.VERTICAL);
        consoleContainer.setPadding(dp(14), dp(12), dp(14), dp(12));
        consoleContainer.setBackground(
                roundBackground(0xFFF2F3F3, dp(10)));

        executionStateText = textView(
                "READY",
                12,
                0xFF16834B,
                Typeface.BOLD);
        consoleContainer.addView(
                executionStateText,
                matchWrap());

        executionMetaText = textView(
                "Waiting for a command.",
                12,
                0xFF777777,
                Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = matchWrap();
        metaParams.topMargin = dp(3);
        consoleContainer.addView(
                executionMetaText,
                metaParams);

        interpretationText = textView(
                "UNDERSTANDING\nWaiting for command.",
                12,
                0xFF555555,
                Typeface.NORMAL);
        interpretationText.setPadding(0, dp(8), 0, 0);
        consoleContainer.addView(
                interpretationText,
                matchWrap());

        progressText = textView(
                "Progress: idle",
                12,
                0xFF555555,
                Typeface.NORMAL);
        LinearLayout.LayoutParams progressTextParams = matchWrap();
        progressTextParams.topMargin = dp(6);
        consoleContainer.addView(
                progressText,
                progressTextParams);

        progressBarContainer = new LinearLayout(context);
        progressBarContainer.setOrientation(LinearLayout.HORIZONTAL);
        progressBarContainer.setPadding(0, dp(4), 0, dp(4));

        for (int i = 0; i < 10; i++) {
            TextView segment = textView(
                    " ",
                    1,
                    0xFFD5D8DC,
                    Typeface.NORMAL);
            segment.setBackground(
                    roundBackground(0xFFD5D8DC, dp(3)));
            LinearLayout.LayoutParams segmentParams =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(6),
                            1);
            if (i < 9) {
                segmentParams.rightMargin = dp(3);
            }
            progressBarContainer.addView(
                    segment,
                    segmentParams);
        }

        consoleContainer.addView(
                progressBarContainer,
                matchWrap());

        consoleText = textView(
                "No activity yet.",
                14,
                0xFF555555,
                Typeface.NORMAL);
        consoleText.setGravity(Gravity.START);
        consoleText.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams consoleTextParams = matchWrap();
        consoleScrollView = new ScrollView(context);
        consoleScrollView.setFillViewport(false);
        consoleScrollView.setVerticalScrollBarEnabled(true);
        consoleScrollView.setBackgroundColor(0x00000000);
        consoleScrollView.addView(
                consoleText,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams consoleScrollParams = matchWrap();
        consoleScrollParams.topMargin = dp(10);
        consoleScrollParams.height = dp(170);
        consoleContainer.addView(
                consoleScrollView,
                consoleScrollParams);

        resultText = textView(
                "Ready.",
                15,
                0xFF333333,
                Typeface.BOLD);
        resultText.setPadding(0, dp(10), 0, 0);
        consoleContainer.addView(
                resultText,
                matchWrap());

        LinearLayout controlRow = new LinearLayout(context);
        controlRow.setOrientation(LinearLayout.HORIZONTAL);
        controlRow.setGravity(Gravity.CENTER_VERTICAL);

        cancelButton = new Button(context);
        cancelButton.setText("CANCEL");
        cancelButton.setAllCaps(true);
        cancelButton.setEnabled(false);

        detailsButton = new Button(context);
        detailsButton.setText("DETAILS");
        detailsButton.setAllCaps(true);

        retryButton = new Button(context);
        retryButton.setText("RETRY");
        retryButton.setAllCaps(true);
        retryButton.setEnabled(false);

        controlRow.addView(
                cancelButton,
                weightedButtonParams());
        controlRow.addView(
                detailsButton,
                weightedButtonParams());
        controlRow.addView(
                retryButton,
                weightedButtonParams());

        cancelButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCancelExecution();
            }
        });

        detailsButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onExecutionDetailsRequested();
            }
        });

        retryButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRetryRequested();
            }
        });

        confirmationRow = new LinearLayout(context);
        confirmationRow.setOrientation(LinearLayout.HORIZONTAL);
        confirmationRow.setGravity(Gravity.CENTER_VERTICAL);
        confirmationRow.setVisibility(View.GONE);

        Button confirmButton = new Button(context);
        confirmButton.setText("CONFIRM");
        confirmButton.setAllCaps(true);

        Button rejectButton = new Button(context);
        rejectButton.setText("CANCEL");
        rejectButton.setAllCaps(true);

        confirmationRow.addView(
                confirmButton,
                weightedButtonParams());
        confirmationRow.addView(
                rejectButton,
                weightedButtonParams());

        confirmButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConfirmationAccepted();
            }
        });

        rejectButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConfirmationRejected();
            }
        });

        LinearLayout.LayoutParams confirmationParams = matchWrap();
        confirmationParams.topMargin = dp(6);
        consoleContainer.addView(confirmationRow, confirmationParams);

        consoleContainer.addView(
                controlRow,
                matchWrap());

        root.addView(consoleContainer, matchWrap());

        TextView activityLabel = textView(
                "ACTIVITY",
                12,
                0xFF777777,
                Typeface.BOLD);
        LinearLayout.LayoutParams activityLabelParams = matchWrap();
        activityLabelParams.topMargin = dp(16);
        root.addView(activityLabel, activityLabelParams);

        LinearLayout activityRow = new LinearLayout(context);
        activityRow.setOrientation(LinearLayout.HORIZONTAL);

        Button historyButton = new Button(context);
        historyButton.setText("HISTORY");
        historyButton.setAllCaps(true);

        Button automationButton = new Button(context);
        automationButton.setText("AUTOMATIONS");
        automationButton.setAllCaps(true);

        activityRow.addView(historyButton, weightedButtonParams());
        activityRow.addView(automationButton, weightedButtonParams());
        root.addView(activityRow, matchWrap());

        Button helpButton = new Button(context);
        helpButton.setText("HELP & COMMANDS");
        helpButton.setAllCaps(false);
        root.addView(helpButton, matchWrap());

        executeButton.setOnClickListener(v -> executeCommand());

        planButton.setOnClickListener(v -> {
            String command = getCommand();
            if (command.isEmpty()) {
                setResult("Enter a command to preview its plan.");
                return;
            }
            if (listener != null) {
                listener.onPlanRequested(command);
            }
        });

        voiceButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onVoiceRequested();
            }
        });

        historyButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHistoryRequested();
            }
        });

        automationButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAutomationsRequested();
            }
        });

        helpButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHelpRequested();
            }
        });
    }

    private void buildProactiveSuggestionCard() {

        proactiveCard = new LinearLayout(context);
        proactiveCard.setOrientation(LinearLayout.VERTICAL);
        proactiveCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        proactiveCard.setBackground(
                roundBackground(0xFFF2F3F3, dp(10)));
        proactiveCard.setVisibility(View.GONE);

        TextView header = textView(
                "OMEPIKYA SUGGESTION",
                12,
                0xFF777777,
                Typeface.BOLD);
        proactiveCard.addView(header, matchWrap());

        proactiveMessage = textView(
                "",
                15,
                0xFF333333,
                Typeface.BOLD);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(4);
        proactiveCard.addView(proactiveMessage, messageParams);

        proactiveMeta = textView(
                "",
                12,
                0xFF777777,
                Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = matchWrap();
        metaParams.topMargin = dp(3);
        proactiveCard.addView(proactiveMeta, metaParams);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        proactiveAcceptButton = new Button(context);
        proactiveAcceptButton.setText("ACCEPT");
        proactiveAcceptButton.setAllCaps(true);

        Button dismiss = new Button(context);
        dismiss.setText("DISMISS");
        dismiss.setAllCaps(true);

        Button settings = new Button(context);
        settings.setText("SETTINGS");
        settings.setAllCaps(true);

        buttons.addView(proactiveAcceptButton, weightedButtonParams());
        buttons.addView(dismiss, weightedButtonParams());
        buttons.addView(settings, weightedButtonParams());

        proactiveCard.addView(buttons, matchWrap());

        proactiveAcceptButton.setOnClickListener(v -> {
            String command = (String) proactiveAcceptButton.getTag();
            if (listener != null && command != null
                    && !command.trim().isEmpty()) {
                hideProactiveSuggestion();
                listener.onProactiveSuggestionAccepted(command);
            }
        });

        dismiss.setOnClickListener(v -> {
            hideProactiveSuggestion();
            if (listener != null) {
                listener.onProactiveSuggestionDismissed();
            }
        });

        settings.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProactiveSettingsRequested();
            }
        });

        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(10);
        root.addView(proactiveCard, cardParams);
    }

    public void showProactiveSuggestion(
            String message,
            String command,
            double score,
            String type) {

        if (proactiveCard == null) {
            return;
        }

        if (message == null || message.trim().isEmpty()) {
            hideProactiveSuggestion();
            return;
        }

        proactiveMessage.setText(message);

        String confidence = String.format(
                Locale.getDefault(),
                "Source: %s  •  Confidence: %d%%",
                type == null || type.trim().isEmpty() ? "context" : type,
                Math.max(0, Math.min(100, Math.round((float) (score * 100.0)) )));

        proactiveMeta.setText(confidence);
        proactiveAcceptButton.setTag(command);
        proactiveAcceptButton.setEnabled(
                command != null && !command.trim().isEmpty());
        proactiveCard.setVisibility(View.VISIBLE);
    }

    public void hideProactiveSuggestion() {
        if (proactiveCard != null) {
            proactiveCard.setVisibility(View.GONE);
        }
    }

    private LinearLayout headerRow(
            String label,
            String arrow) {

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = textView(
                label,
                12,
                0xFF777777,
                Typeface.BOLD);

        TextView icon = textView(
                arrow,
                16,
                0xFF555555,
                Typeface.BOLD);
        icon.setGravity(Gravity.END);

        row.addView(title,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1));
        row.addView(icon, wrapWrap());

        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(16);
        params.bottomMargin = dp(5);
        row.setPadding(0, dp(4), 0, dp(4));
        return row;
    }

    private void toggleStatus() {
        statusExpanded = !statusExpanded;
        statusContainer.setVisibility(
                statusExpanded ? View.VISIBLE : View.GONE);
    }

    private void toggleConsole() {
        consoleExpanded = !consoleExpanded;
        consoleContainer.setVisibility(
                consoleExpanded ? View.VISIBLE : View.GONE);
    }

    private void addQuickButton(
            LinearLayout parent,
            String label,
            String command) {

        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(6);
        parent.addView(button, params);

        button.setOnClickListener(v -> {
            setCommand(command);
            if (listener != null) {
                listener.onExecuteCommand(command);
            }
        });
    }

    private TextView addStatusRow(
            LinearLayout parent,
            String label,
            String status) {

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(roundBackground(0xFFF7F7F7, dp(8)));

        TextView name = textView(
                label,
                14,
                0xFF444444,
                Typeface.NORMAL);

        TextView value = textView(
                status,
                12,
                0xFF16834B,
                Typeface.BOLD);
        value.setGravity(Gravity.END);

        row.addView(name,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1));
        row.addView(value, wrapWrap());

        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(5);
        parent.addView(row, params);
        return value;
    }

    private TextView textView(
            String text,
            float size,
            int color,
            int style) {

        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable roundBackground(
            int color,
            int radius) {

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void executeCommand() {

        String command = getCommand();
        if (command.isEmpty()) {
            setResult("Please enter a command.");
            return;
        }

        if (listener != null) {
            listener.onExecuteCommand(command);
        }
    }

    public void setCommand(String command) {
        if (command == null) {
            return;
        }
        commandInput.setText(command);
        commandInput.setSelection(commandInput.length());
    }

    public String getCommand() {
        return commandInput.getText().toString().trim();
    }

    public void clearCommandInput() {
        commandInput.setText("");
    }

    public void setConfirmationRequired(boolean required) {
        if (confirmationRow != null) {
            confirmationRow.setVisibility(
                    required ? View.VISIBLE : View.GONE);
        }

        if (required) {
            cancelButton.setEnabled(false);
            retryButton.setEnabled(false);
            setExecutionActive(false);
        }
    }

    public void setResult(String result) {
        if (result == null || result.trim().isEmpty()) {
            result = "Ready.";
        }
        resultText.setText(result);
    }

    public void setExecutionActive(boolean active) {
        cancelButton.setEnabled(active);
        commandInput.setEnabled(!active);
    }

    public void setRetryAvailable(boolean available) {
        if (retryButton != null) {
            retryButton.setEnabled(available);
        }
    }

    public void setInterpretation(
            String intent,
            String action,
            String entity) {

        StringBuilder builder = new StringBuilder();
        builder.append("UNDERSTANDING");

        if (intent != null && !intent.trim().isEmpty()) {
            builder.append("\nIntent: ").append(intent);
        }
        if (action != null && !action.trim().isEmpty()) {
            builder.append("\nAction: ").append(action);
        }
        if (entity != null && !entity.trim().isEmpty()) {
            builder.append("\nEntity: ").append(entity);
        }

        interpretationText.setText(builder.toString());
    }

    public void updateProgress(
            int currentStep,
            int totalSteps,
            long elapsedMs) {

        if (totalSteps <= 0) {
            progressText.setText(
                    "Progress: running  •  "
                            + formatElapsed(elapsedMs));
            setProgressSegments(0);
            return;
        }

        int completed = Math.max(0,
                Math.min(totalSteps, currentStep));
        int percent =
                (int) ((completed * 100.0f) / totalSteps);

        progressText.setText(
                "Progress: "
                        + percent
                        + "%  •  Step "
                        + Math.min(totalSteps, currentStep + 1)
                        + "/"
                        + totalSteps
                        + "  •  "
                        + formatElapsed(elapsedMs));

        setProgressSegments(
                Math.round(
                        percent / 10.0f));
    }

    private void setProgressSegments(int filled) {
        if (progressBarContainer == null) {
            return;
        }

        int count = progressBarContainer.getChildCount();

        for (int i = 0; i < count; i++) {
            TextView segment =
                    (TextView) progressBarContainer.getChildAt(i);

            boolean on = i < filled;

            segment.setBackground(
                    roundBackground(
                            on
                                    ? 0xFF16834B
                                    : 0xFFD5D8DC,
                            dp(3)));
        }
    }

    private String formatElapsed(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        long minutes = seconds / 60L;
        seconds %= 60L;

        return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds);
    }

    public void resetExecutionPresentation() {
        setConfirmationRequired(false);
        setRetryAvailable(false);
        progressText.setText("Progress: idle");
        setProgressSegments(0);
        executionStateText.setText("READY");
        executionMetaText.setText("Waiting for a command.");
        interpretationText.setText(
                "UNDERSTANDING\nWaiting for command.");
    }

    public void setExecutionResultState(
            boolean success,
            boolean cancelled) {

        String state = cancelled
                ? "CANCELLED"
                : (success ? "SUCCESS" : "FAILED");

        executionStateText.setText(state);
        executionStateText.setTextColor(
                cancelled
                        ? 0xFF9A6700
                        : (success
                        ? 0xFF16834B
                        : 0xFFB3261E));
    }

    public void addExecutionEvent(ExecutionEvent event) {
        if (event == null) {
            return;
        }

        String line = timeFormat.format(
                new Date(event.getTimestamp()))
                + "  "
                + event.getType()
                + "  "
                + event.getMessage();

        if ("RECEIVED".equals(event.getType())) {
            consoleEntries.clear();
        }

        consoleEntries.add(line);
        while (consoleEntries.size() > 12) {
            consoleEntries.remove(0);
        }

        StringBuilder builder = new StringBuilder();
        for (String entry : consoleEntries) {
            builder.append(entry).append('\n');
        }
        consoleText.setText(builder.toString().trim());
        if (consoleScrollView != null) {
            consoleScrollView.post(() ->
                    consoleScrollView.fullScroll(View.FOCUS_DOWN));
        }

        String state = mapEventState(event.getType());
        executionStateText.setText(state);
        executionMetaText.setText(
                "Execution: "
                        + safe(event.getExecutionId())
                        + "\n"
                        + safe(event.getMessage()));

        if ("SUCCESS".equals(state)) {
            executionStateText.setTextColor(0xFF16834B);
        } else if ("FAILED".equals(state)
                || "CANCELLED".equals(state)
                || "TIMEOUT".equals(state)) {
            executionStateText.setTextColor(0xFFB3261E);
        } else if ("RECOVERING".equals(state)) {
            executionStateText.setTextColor(0xFF9A6700);
        } else {
            executionStateText.setTextColor(0xFF3F51B5);
        }
    }

    public void updateExecutionMonitor(
            ExecutionMonitor monitor) {

        if (monitor == null) {
            return;
        }

        executionStateText.setText(
                safe(monitor.getState()));

        String step = monitor.getCurrentStep();
        String progress =
                monitor.getTotalSteps() > 0
                        ? "\nStep "
                        + (monitor.getCurrentStepIndex() + 1)
                        + "/"
                        + monitor.getTotalSteps()
                        : "";

        executionMetaText.setText(
                "Execution: "
                        + safe(monitor.getExecutionId())
                        + progress
                        + "\nAttempts: "
                        + monitor.getAttempts()
                        + "  Recovery: "
                        + monitor.getRecoveryAttempts()
                        + (step == null || step.isEmpty()
                        ? ""
                        : "\nCurrent: " + step));

        String monitorState = monitor.getState();
        boolean finished =
                "SUCCESS".equals(monitorState)
                        || "FAILED".equals(monitorState)
                        || "CANCELLED".equals(monitorState)
                        || "TIMEOUT".equals(monitorState);

        if (finished) {
            progressText.setText(
                    "Completed • "
                            + formatElapsed(monitor.getElapsedMs()));
            setProgressSegments(10);
        } else {
            updateProgress(
                    monitor.getCurrentStepIndex(),
                    monitor.getTotalSteps(),
                    monitor.getElapsedMs());
        }

        setRetryAvailable(finished);
    }

    private String mapEventState(String type) {
        if (type == null) return "UNKNOWN";
        if ("SUCCESS".equals(type)
                || "COMPLETED".equals(type)
                || "EXECUTION_COMPLETED".equals(type)) return "SUCCESS";
        if ("FAILED".equals(type)
                || "EXECUTION_FAILED".equals(type)) return "FAILED";
        if ("CANCELLED".equals(type)) return "CANCELLED";
        if ("TIMEOUT".equals(type)) return "TIMEOUT";
        if ("RECOVERY_START".equals(type)
                || "RECOVERY_STARTED".equals(type)) return "RECOVERING";
        if ("RUNNING".equals(type)
                || "EXECUTION_START".equals(type)) return "EXECUTING";
        return type;
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty()
                ? "—"
                : value;
    }

    public void setVoiceSpeaking(boolean speaking) {
        if (speaking) {
            setResult("Speaking...");
        }
    }

    public void setVoiceStatus(boolean ready) {
        setStatus(voiceStatus, ready ? "READY" : "UNAVAILABLE", ready);
    }

    /**
     * Shows the actual recognition mode so users can tell whether
     * voice input is local or may rely on the system's network service.
     */
    public void setVoiceRecognitionMode(
            boolean ready,
            boolean onDeviceAvailable,
            boolean usingOnDevice) {
        if (!ready) {
            setStatus(voiceStatus, "UNAVAILABLE", false);
            return;
        }
        if (usingOnDevice) {
            setStatus(voiceStatus, "ON-DEVICE", true);
        } else if (onDeviceAvailable) {
            setStatus(voiceStatus, "CHECK MODE", false);
        } else {
            setStatus(voiceStatus, "NETWORK FALLBACK", false);
        }
    }

    public void setAutomationStatus(boolean ready) {
        setStatus(automationStatus, ready ? "READY" : "UNAVAILABLE", ready);
    }

    public void setSystemBridgeStatus(boolean ready) {
        setStatus(bridgeStatus, ready ? "READY" : "UNAVAILABLE", ready);
    }

    public void setRouterStatus(boolean ready) {
        setStatus(routerStatus, ready ? "READY" : "UNAVAILABLE", ready);
    }

    public void setBrainStatus(boolean ready) {
        setStatus(brainStatus, ready ? "ONLINE" : "OFFLINE", ready);
    }

    private void setStatus(TextView view, String text, boolean good) {
        if (view != null) {
            view.setText(text);
            view.setTextColor(good ? 0xFF16834B : 0xFFB3261E);
        }
    }

    public View getView() {
        return scrollView;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1);
        params.rightMargin = dp(3);
        return params;
    }

    private int dp(int value) {
        return (int) (
                value * context.getResources()
                        .getDisplayMetrics().density
                        + 0.5f);
    }
}
