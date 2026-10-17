package com.omepikya.commandcenter;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;

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
    private static final String PREFS = "tiny_chat";
    private static final String API_BASE = "api_base";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout messages;
    private ScrollView messageScroll;
    private EditText input;
    private Button sendButton;
    private TextView connectionStatus;
    private CheckBox speakReplies;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private boolean busy = false;
    private String sessionId = UUID.randomUUID().toString();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        textToSpeech = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) textToSpeech.setLanguage(Locale.getDefault());
        });
        if (getApiBase().isEmpty()) {
            showApiSettings(true);
        } else {
            setStatus("Endpoint saved; checking when you send a message.");
        }
        addMessage("Tiny chat is separate from device commands. Tiny's replies cannot execute actions on your phone.", false);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundColor(0xFFF8FAFC);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Talk to Tiny");
        title.setTextSize(23);
        title.setTextColor(0xFF18243A);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button settings = new Button(this);
        settings.setText("API");
        settings.setOnClickListener(v -> showApiSettings(false));
        header.addView(settings, new LinearLayout.LayoutParams(-2, -2));
        root.addView(header);

        connectionStatus = new TextView(this);
        connectionStatus.setTextSize(12);
        connectionStatus.setTextColor(0xFF64748B);
        connectionStatus.setPadding(0, dp(4), 0, dp(8));
        root.addView(connectionStatus);

        messageScroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messageScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        root.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        speakReplies = new CheckBox(this);
        speakReplies.setText("Speak Tiny's replies");
        speakReplies.setTextColor(0xFF334155);
        root.addView(speakReplies);

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setHint("Message Tiny…");
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        Button mic = new Button(this);
        mic.setText("🎙");
        mic.setContentDescription("Dictate a message to Tiny");
        mic.setOnClickListener(v -> startDictation());
        composer.addView(mic, new LinearLayout.LayoutParams(-2, -2));

        sendButton = new Button(this);
        sendButton.setText("SEND");
        sendButton.setOnClickListener(v -> sendMessage());
        composer.addView(sendButton, new LinearLayout.LayoutParams(-2, -2));
        root.addView(composer);

        setContentView(root);
    }

    private void showApiSettings(boolean firstRun) {
        EditText endpoint = new EditText(this);
        endpoint.setSingleLine(true);
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setHint("https://your-private-tiny-host.example");
        endpoint.setText(getApiBase());
        new AlertDialog.Builder(this)
                .setTitle("Tiny API endpoint")
                .setMessage("Enter the base URL of your privately hosted Tiny service (not /api/tiny/chat). Use HTTPS for remote servers. Do not connect to an unauthenticated public endpoint.")
                .setView(endpoint)
                .setCancelable(!firstRun)
                .setNegativeButton(firstRun ? "SKIP" : "CANCEL", (dialog, which) -> {
                    if (firstRun && getApiBase().isEmpty()) setStatus("API not configured. Tap API to set your trusted endpoint.");
                })
                .setPositiveButton("SAVE", (dialog, which) -> {
                    String value = endpoint.getText().toString().trim().replaceAll("/+$", "");
                    if (!validBaseUrl(value)) {
                        Toast.makeText(this, "Use a valid HTTPS URL (HTTP is allowed only for localhost).", Toast.LENGTH_LONG).show();
                        if (firstRun) showApiSettings(true);
                        return;
                    }
                    getPreferences(MODE_PRIVATE).edit().putString(API_BASE, value).apply();
                    setStatus("Endpoint saved.");
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

    private void startDictation() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
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
            showApiSettings(false);
            setStatus("API not configured.");
            return;
        }
        if (!validBaseUrl(base)) {
            setStatus("Saved endpoint is invalid. Update it in API settings.");
            return;
        }

        addMessage(message, true);
        input.setText("");
        setBusy(true);
        TextView pending = addMessage("Tiny is thinking…", false);
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("message", message);
                body.put("sessionId", sessionId);
                URL url = new URL(base + "/api/tiny/chat");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                try {
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(25000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
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
                    runOnUiThread(() -> {
                        pending.setText(reply);
                        setStatus("Tiny API connected.");
                        if (speakReplies.isChecked() && ttsReady && textToSpeech != null) textToSpeech.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "tiny-reply");
                    });
                } finally {
                    connection.disconnect();
                }
            } catch (Exception e) {
                String reason = e.getMessage() == null ? "Request failed." : e.getMessage();
                runOnUiThread(() -> {
                    pending.setText("Tiny could not reply: " + reason + "\nCheck the endpoint, deployment health, and server configuration.");
                    setStatus("Tiny API unavailable.");
                });
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    private String readFully(InputStream stream) throws Exception {
        if (stream == null) return "{}";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        }
    }

    private TextView addMessage(String text, boolean user) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(user ? 0xFFFFFFFF : 0xFF1E293B);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackgroundColor(user ? 0xFF344C78 : 0xFFE8EDF5);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        view.setLayoutParams(params);
        messages.addView(view);
        messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
        return view;
    }

    private void setBusy(boolean value) {
        busy = value;
        sendButton.setEnabled(!value);
        input.setEnabled(!value);
        sendButton.setText(value ? "…" : "SEND");
    }

    private void setStatus(String value) {
        if (connectionStatus != null) connectionStatus.setText(value);
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
