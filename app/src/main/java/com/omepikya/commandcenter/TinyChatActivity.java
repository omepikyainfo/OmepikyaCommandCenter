package com.omepikya.commandcenter;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TinyChatActivity extends AppCompatActivity {
    private static final int SPEECH_REQUEST_CODE = 3101;
    private static final String API_BASE = "api_base";
    private static final String API_TOKEN = "api_token";
    private static final int INK = 0xFF17233B;
    private static final int MUTED = 0xFF718096;
    private static final int ACCENT = 0xFF5B6FF5;
    private static final int CANVAS = 0xFFF5F7FC;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout messages;
    private ScrollView messageScroll;
    private EditText input;
    private Button sendButton;
    private Button micButton;
    private TextView connectionStatus;
    private TextView statusDot;
    private CheckBox speakReplies;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private boolean busy = false;
    private String sessionId = UUID.randomUUID().toString();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFFF5F7FC);
        getWindow().setNavigationBarColor(0xFFF5F7FC);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildUi();
        textToSpeech = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) textToSpeech.setLanguage(Locale.getDefault());
        });
        if (getApiBase().isEmpty()) {
            setStatus("Local mode · cloud chat optional", true);
        } else {
            setStatus("Cloud endpoint saved · local commands remain available", false);
        }
        addWelcome();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(12));
        root.setBackgroundColor(CANVAS);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(4), 0, dp(12));

        TextView logo = new TextView(this);
        logo.setText("t");
        logo.setTextSize(26);
        logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        logo.setTextColor(0xFFFFFFFF);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(ACCENT, dp(16)));
        header.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(11), 0, 0, 0);
        TextView title = label("Tiny", 21, INK, Typeface.BOLD);
        TextView subtitle = label("OMEPIKYA ASSISTANT", 10, MUTED, Typeface.BOLD);
        subtitle.setLetterSpacing(0.08f);
        brand.addView(title);
        brand.addView(subtitle);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        Button newChat = new Button(this);
        newChat.setText("NEW");
        newChat.setTextSize(11);
        newChat.setAllCaps(false);
        newChat.setOnClickListener(v -> confirmNewChat());
        header.addView(newChat, new LinearLayout.LayoutParams(-2, dp(44)));

        Button settings = new Button(this);
        settings.setText("⚙");
        settings.setContentDescription("Tiny API settings");
        settings.setOnClickListener(v -> showApiSettings(false));
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(header);

        LinearLayout statusBar = new LinearLayout(this);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        statusBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        statusBar.setBackground(round(0xFFFFFFFF, dp(13)));
        statusDot = label("●", 10, 0xFFDAA044, Typeface.BOLD);
        statusBar.addView(statusDot);
        connectionStatus = label("API not configured", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams statusTextParams = new LinearLayout.LayoutParams(0, -2, 1);
        statusTextParams.leftMargin = dp(8);
        statusBar.addView(connectionStatus, statusTextParams);
        root.addView(statusBar);

        messageScroll = new ScrollView(this);
        messageScroll.setFillViewport(true);
        messageScroll.setClipToPadding(false);
        messageScroll.setVerticalScrollBarEnabled(false);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, dp(18), 0, dp(18));
        messageScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        root.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composerCard = new LinearLayout(this);
        composerCard.setOrientation(LinearLayout.VERTICAL);
        composerCard.setPadding(dp(10), dp(8), dp(10), dp(6));
        composerCard.setBackground(round(0xFFFFFFFF, dp(20)));
        composerCard.setElevation(dp(3));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setHint("Message Tiny…");
        input.setTextSize(16);
        input.setTextColor(INK);
        input.setHintTextColor(0xFF9AA6B8);
        input.setBackgroundColor(0x00000000);
        input.setPadding(dp(8), dp(10), dp(6), dp(10));
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        micButton = new Button(this);
        micButton.setText("🎙");
        micButton.setTextSize(17);
        micButton.setContentDescription("Dictate a message to Tiny");
        micButton.setOnClickListener(v -> startDictation());
        composer.addView(micButton, new LinearLayout.LayoutParams(dp(46), dp(46)));

        sendButton = new Button(this);
        sendButton.setText("➤");
        sendButton.setTextSize(20);
        sendButton.setTextColor(0xFFFFFFFF);
        sendButton.setBackground(round(ACCENT, dp(15)));
        sendButton.setContentDescription("Send message to Tiny");
        sendButton.setOnClickListener(v -> sendMessage());
        composer.addView(sendButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
        composerCard.addView(composer);

        speakReplies = new CheckBox(this);
        speakReplies.setText("Speak replies");
        speakReplies.setTextSize(12);
        speakReplies.setTextColor(MUTED);
        LinearLayout.LayoutParams speakParams = new LinearLayout.LayoutParams(-2, -2);
        speakParams.leftMargin = dp(4);
        composerCard.addView(speakReplies, speakParams);
        root.addView(composerCard);

        TextView safetyNote = label("Conversation only · device actions stay in Command Center", 10, MUTED, Typeface.NORMAL);
        safetyNote.setGravity(Gravity.CENTER);
        safetyNote.setPadding(0, dp(8), 0, 0);
        root.addView(safetyNote);

        setContentView(root);
    }

    private void addWelcome() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(18), dp(22), dp(18), dp(22));
        card.setBackground(round(0xFFFFFFFF, dp(20)));

        TextView orb = label("✦", 30, 0xFFFFFFFF, Typeface.BOLD);
        orb.setGravity(Gravity.CENTER);
        orb.setBackground(round(ACCENT, dp(24)));
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(dp(58), dp(58));
        orbParams.bottomMargin = dp(12);
        card.addView(orb, orbParams);

        TextView heading = label("What can I help with?", 19, INK, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        card.addView(heading);
        TextView body = label("Try local commands like “what time is it”, “what is today's date”, or “help”. General AI conversation needs an optional configured API until an on-device language model is added.", 13, MUTED, Typeface.NORMAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(7), 0, dp(14));
        card.addView(body);

        LinearLayout suggestions = new LinearLayout(this);
        suggestions.setGravity(Gravity.CENTER);
        addSuggestion(suggestions, "Explain something", "Explain a difficult topic simply");
        addSuggestion(suggestions, "Help me plan", "Help me plan my day");
        card.addView(suggestions);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        messages.addView(card, params);
    }

    private void addSuggestion(LinearLayout parent, String title, String prompt) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setOnClickListener(v -> {
            input.setText(prompt);
            input.setSelection(input.length());
            input.requestFocus();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = dp(3);
        params.rightMargin = dp(3);
        parent.addView(button, params);
    }

    private void confirmNewChat() {
        new AlertDialog.Builder(this)
                .setTitle("Start a new chat?")
                .setMessage("This clears the messages shown here and starts a new conversation session.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("NEW CHAT", (dialog, which) -> {
                    messages.removeAllViews();
                    sessionId = UUID.randomUUID().toString();
                    addWelcome();
                    setStatus("New conversation ready", false);
                })
                .show();
    }

    private void showApiSettings(boolean firstRun) {
        EditText endpoint = new EditText(this);
        endpoint.setSingleLine(true);
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setHint("https://your-private-tiny-host.example");
        endpoint.setText(getApiBase());
        EditText token = new EditText(this);
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setHint("API access token (if required)");
        token.setText(getPreferences(MODE_PRIVATE).getString(API_TOKEN, ""));
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int fieldPadding = dp(20);
        fields.setPadding(fieldPadding, 0, fieldPadding, 0);
        fields.addView(endpoint);
        fields.addView(token);
        new AlertDialog.Builder(this)
                .setTitle("Connect Tiny")
                .setMessage("Enter the base URL of your trusted Tiny API. Do not add /api/tiny/chat. Remote servers must use HTTPS; do not connect to an unauthenticated public endpoint.")
                .setView(fields)
                .setCancelable(true)
                .setNegativeButton("CANCEL", (dialog, which) -> {
                    if (getApiBase().isEmpty()) setStatus("Local mode · cloud chat optional", true);
                })
                .setPositiveButton("SAVE ENDPOINT", (dialog, which) -> {
                    String value = endpoint.getText().toString().trim().replaceAll("/+$", "");
                    if (!validBaseUrl(value)) {
                        Toast.makeText(this, "Enter a valid HTTPS URL (HTTP is allowed only for localhost).", Toast.LENGTH_LONG).show();
                        return;
                    }
                    getPreferences(MODE_PRIVATE).edit()
                            .putString(API_BASE, value)
                            .putString(API_TOKEN, token.getText().toString().trim())
                            .apply();
                    setStatus("Endpoint saved · send a message to test it", false);
                })
                .show();
    }

    private boolean validBaseUrl(String value) {
        try {
            URL url = new URL(value);
            String protocol = url.getProtocol();
            String host = url.getHost();
            boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
            return ("https".equalsIgnoreCase(protocol) || (local && "http".equalsIgnoreCase(protocol)))
                    && !host.isEmpty() && url.getUserInfo() == null
                    && url.getQuery() == null && url.getRef() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String getApiBase() {
        return getPreferences(MODE_PRIVATE).getString(API_BASE, "").trim();
    }

    /** Deterministic local responses; this method never uses the network or executes device actions. */
    @Nullable
    private String answerLocalCommand(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim().toLowerCase(Locale.ROOT);
        if (message.isEmpty()) return null;
        if (message.matches(".*\\b(hi|hello|hey)\\b.*")) {
            return "Hello! Tiny is running in local mode. Try asking for the time, date, or help.";
        }
        if (message.contains("what time") || message.equals("time") || message.contains("current time")) {
            return "The time on this device is "
                    + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault()).format(new java.util.Date()) + ".";
        }
        if ((message.contains("today") && message.contains("date")) || message.equals("date") || message.contains("what date")) {
            return "Today's date on this device is "
                    + java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, Locale.getDefault()).format(new java.util.Date()) + ".";
        }
        if (message.equals("help") || message.contains("what can you do")) {
            return "Local Tiny can currently answer basic greetings, the device time and date, and this help message. Use Command Center for supported device commands. General AI chat needs a trusted API or a future bundled on-device model.";
        }
        return null;
    }

    private void startDictation() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Tiny");
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition is unavailable on this device.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                input.setText(results.get(0));
                input.setSelection(input.length());
            }
        }
    }

    private void sendMessage() {
        if (busy) return;
        String message = input.getText().toString().trim();
        if (message.isEmpty()) {
            input.setError("Enter a message first.");
            return;
        }
        String base = getApiBase();
        if (base.isEmpty()) {
            String localReply = answerLocalCommand(message);
            addMessage(message, true);
            input.setText("");
            if (localReply != null) {
                addMessage(localReply, false);
                setStatus("Local mode · handled on this device", true);
                if (speakReplies.isChecked() && ttsReady && textToSpeech != null) {
                    textToSpeech.speak(localReply, TextToSpeech.QUEUE_FLUSH, null, "tiny-local-reply");
                }
            } else {
                addMessage("I’m in local mode, but an on-device language model is not installed yet. I can answer a few built-in commands; for general conversation, configure a trusted Tiny API with ⚙. Device actions remain in Command Center.", false);
                setStatus("Local mode · on-device AI model not installed", false);
            }
            return;
        }
        if (!validBaseUrl(base)) {
            setStatus("Saved endpoint is invalid · update API settings", false);
            return;
        }

        addMessage(message, true);
        input.setText("");
        setBusy(true);
        TextView pending = addMessage("Tiny is thinking…", false);
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject body = new JSONObject();
                body.put("message", message);
                body.put("sessionId", sessionId);
                URL url = new URL(base + "/api/tiny/chat");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(25000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                String apiToken = getPreferences(MODE_PRIVATE).getString(API_TOKEN, "").trim();
                if (!apiToken.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + apiToken);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String responseBody = readFully(stream);
                JSONObject response = new JSONObject(responseBody);
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException(response.optString("error", "Tiny service returned HTTP " + status));
                }
                String reply = response.optString("reply", "").trim();
                if (reply.isEmpty()) throw new IllegalStateException("Tiny service returned an empty reply.");
                String returnedSession = response.optString("sessionId", "");
                if (!returnedSession.isEmpty()) sessionId = returnedSession;
                String finalReply = reply;
                runOnUiThread(() -> {
                    pending.setText(finalReply);
                    setStatus("Tiny is connected", true);
                    if (speakReplies.isChecked() && ttsReady && textToSpeech != null) {
                        textToSpeech.speak(finalReply, TextToSpeech.QUEUE_FLUSH, null, "tiny-reply");
                    }
                });
            } catch (Exception e) {
                String reason = e.getMessage() == null ? "Request failed." : e.getMessage();
                runOnUiThread(() -> {
                    pending.setText("I couldn't reach Tiny. " + reason + "\n\nCheck the API URL, server health, and server configuration.");
                    setStatus("Tiny API unavailable · tap ⚙ to check settings", false);
                });
            } finally {
                if (connection != null) connection.disconnect();
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    private String readFully(InputStream stream) throws Exception {
        if (stream == null) return "{}";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
                if (result.length() > 65536) throw new IllegalStateException("Tiny response exceeded the size limit.");
            }
            return result.toString();
        }
    }

    private TextView addMessage(String text, boolean user) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.END : Gravity.START);
        row.setOrientation(LinearLayout.VERTICAL);

        TextView bubble = label(text, 15, user ? 0xFFFFFFFF : INK, Typeface.NORMAL);
        bubble.setPadding(dp(14), dp(11), dp(14), dp(11));
        bubble.setBackground(round(user ? ACCENT : 0xFFFFFFFF, dp(18)));
        bubble.setMaxWidth(getResources().getDisplayMetrics().widthPixels - dp(88));
        bubble.setTextIsSelectable(true);
        row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(10);
        messages.addView(row, rowParams);
        messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
        return bubble;
    }

    private TextView label(String text, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private void setBusy(boolean value) {
        busy = value;
        sendButton.setEnabled(!value);
        micButton.setEnabled(!value);
        input.setEnabled(!value);
        sendButton.setAlpha(value ? 0.55f : 1.0f);
    }

    private void setStatus(String value, boolean connected) {
        if (connectionStatus != null) connectionStatus.setText(value);
        if (statusDot != null) statusDot.setTextColor(connected ? 0xFF24A47A : 0xFFDAA044);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
