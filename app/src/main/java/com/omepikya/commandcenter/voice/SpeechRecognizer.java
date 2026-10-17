package com.omepikya.commandcenter.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;

import java.util.ArrayList;
import java.util.Locale;

public class SpeechRecognizer {

    public interface Listener {

        void onReady();

        void onListening();

        void onResult(SpeechResult result);

        void onError(String error);

        void onComplete();
    }

    private final Context context;
    private final android.speech.SpeechRecognizer recognizer;

    private Listener listener;

    public SpeechRecognizer(Context context) {

        if (context == null) {
            throw new IllegalArgumentException(
                    "Context cannot be null"
            );
        }

        this.context =
                context.getApplicationContext();

        if (!android.speech.SpeechRecognizer
                .isRecognitionAvailable(this.context)) {

            throw new IllegalStateException(
                    "Speech recognition is not available. Install or enable a speech recognition service on your phone."
            );
        }

        recognizer =
                android.speech.SpeechRecognizer
                        .createSpeechRecognizer(
                                this.context
                        );

        recognizer.setRecognitionListener(
                createRecognitionListener()
        );
    }

    public void setListener(
            Listener listener
    ) {

        this.listener = listener;
    }

    public void startListening() {

        Intent intent =
                new Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        /*
         * EXTRA_LANGUAGE expects a BCP-47 language-tag String.
         * Passing a Locale object here can prevent some Android
         * recognition services from applying the requested language.
         */
        String languageTag =
                Locale.getDefault().toLanguageTag();

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                languageTag
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                languageTag
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
        );

        if (listener != null) {
            listener.onReady();
        }

        recognizer.startListening(intent);
    }

    public void stopListening() {
        recognizer.stopListening();
    }

    public void cancel() {
        recognizer.cancel();
    }

    public void destroy() {
        recognizer.destroy();
        listener = null;
    }

    private RecognitionListener createRecognitionListener() {

        return new RecognitionListener() {

            @Override
            public void onReadyForSpeech(
                    Bundle params
            ) {

                if (listener != null) {
                    listener.onListening();
                }
            }

            @Override
            public void onBeginningOfSpeech() {
                // Speech has started.
            }

            @Override
            public void onRmsChanged(
                    float rmsdB
            ) {
                // Audio level callback.
            }

            @Override
            public void onBufferReceived(
                    byte[] buffer
            ) {
                // Raw audio callback.
            }

            @Override
            public void onEndOfSpeech() {
                // Speech has ended.
            }

            @Override
            public void onError(
                    int error
            ) {

                String message =
                        getErrorMessage(error);

                if (listener != null) {
                    listener.onError(message);
                    listener.onComplete();
                }
            }

            @Override
            public void onResults(
                    Bundle results
            ) {

                ArrayList<String> matches =
                        results == null
                                ? null
                                : results.getStringArrayList(
                                android.speech.SpeechRecognizer
                                        .RESULTS_RECOGNITION
                        );

                if (matches != null
                        && !matches.isEmpty()
                        && matches.get(0) != null
                        && !matches.get(0)
                        .trim()
                        .isEmpty()) {

                    if (listener != null) {
                        listener.onResult(
                                SpeechResult.success(
                                        matches.get(0).trim()
                                )
                        );
                    }

                } else {

                    if (listener != null) {
                        listener.onResult(
                                SpeechResult.failure(
                                        "No speech recognized. Try speaking clearly, check the selected recognition language, and verify your internet connection if your speech service requires it."
                                )
                        );
                    }
                }

                if (listener != null) {
                    listener.onComplete();
                }
            }

            @Override
            public void onPartialResults(
                    Bundle partialResults
            ) {
                // Partial results disabled to avoid executing
                // an incomplete command before the user finishes.
            }

            @Override
            public void onEvent(
                    int eventType,
                    Bundle params
            ) {
                // Reserved for future use.
            }
        };
    }

    private String getErrorMessage(int error) {

        switch (error) {

            case android.speech.SpeechRecognizer
                    .ERROR_AUDIO:

                return "Audio recording error. Check that your microphone works and no other app is using it.";

            case android.speech.SpeechRecognizer
                    .ERROR_CLIENT:

                return "Speech recognition client error. Tap VOICE to try again.";

            case android.speech.SpeechRecognizer
                    .ERROR_INSUFFICIENT_PERMISSIONS:

                return "Microphone permission is required. Allow it in Android Settings > Apps > Omepikya Command Center > Permissions.";

            case android.speech.SpeechRecognizer
                    .ERROR_NETWORK:

                return "Speech recognition needs a working network connection or an available offline language pack.";

            case android.speech.SpeechRecognizer
                    .ERROR_NETWORK_TIMEOUT:

                return "Speech recognition timed out. Check your connection and try again.";

            case android.speech.SpeechRecognizer
                    .ERROR_NO_MATCH:

                return "I didn't recognize those words. Try a short command and speak clearly.";

            case android.speech.SpeechRecognizer
                    .ERROR_RECOGNIZER_BUSY:

                return "Speech recognition is busy. Wait a moment and tap VOICE again.";

            case android.speech.SpeechRecognizer
                    .ERROR_SERVER:

                return "The speech recognition service failed. Try again in a moment.";

            case android.speech.SpeechRecognizer
                    .ERROR_SPEECH_TIMEOUT:

                return "I didn't hear any speech. Tap VOICE and start speaking after the listening message.";

            default:

                return "Speech recognition failed (error " + error + "). Check your speech service and try again.";
        }
    }
}