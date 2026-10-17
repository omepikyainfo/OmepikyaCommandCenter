package com.omepikya.commandcenter.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
    private final boolean usingOnDeviceRecognizer;

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
                    "Speech recognition is not available"
            );
        }

        /*
         * Prefer Android's dedicated on-device recognizer whenever it is
         * available. This keeps speech processing local on supported devices.
         */
        boolean onDeviceAvailable =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && android.speech.SpeechRecognizer
                                .isOnDeviceRecognitionAvailable(this.context);

        if (onDeviceAvailable) {
            recognizer =
                    android.speech.SpeechRecognizer
                            .createOnDeviceSpeechRecognizer(this.context);
            usingOnDeviceRecognizer = true;
        } else {
            /*
             * Compatibility fallback for older devices. Android may ignore
             * EXTRA_PREFER_OFFLINE, so this path is not guaranteed offline.
             */
            recognizer =
                    android.speech.SpeechRecognizer
                            .createSpeechRecognizer(this.context);
            usingOnDeviceRecognizer = false;
        }

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

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag()
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
        );

        // Ask compatible system recognizers to use downloaded offline speech data.
        intent.putExtra(
                RecognizerIntent.EXTRA_PREFER_OFFLINE,
                true
        );

        if (listener != null) {
            listener.onReady();
        }

        recognizer.startListening(intent);
    }

    /**
     * True only when Android's dedicated on-device recognizer is active.
     * The compatibility fallback may still require network access.
     */
    public boolean isUsingOnDeviceRecognizer() {
        return usingOnDeviceRecognizer;
    }

    public static boolean isOnDeviceRecognitionAvailable(Context context) {
        return context != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && android.speech.SpeechRecognizer
                        .isOnDeviceRecognitionAvailable(
                                context.getApplicationContext()
                        );
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
                        results.getStringArrayList(
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
                                        "No speech recognized"
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
                // Partial results disabled.
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

                return "Audio recording error";

            case android.speech.SpeechRecognizer
                    .ERROR_CLIENT:

                return "Speech recognition client error";

            case android.speech.SpeechRecognizer
                    .ERROR_INSUFFICIENT_PERMISSIONS:

                return "Microphone permission is required";

            case android.speech.SpeechRecognizer
                    .ERROR_NETWORK:

                return usingOnDeviceRecognizer
                        ? "On-device speech recognition failed. Check the downloaded offline language pack."
                        : "Speech recognition needs internet or a supported offline language pack.";

            case android.speech.SpeechRecognizer
                    .ERROR_NETWORK_TIMEOUT:

                return usingOnDeviceRecognizer
                        ? "On-device recognition timed out. Check the offline language pack."
                        : "Speech recognition timed out. Download an offline language pack for offline use.";

            case android.speech.SpeechRecognizer
                    .ERROR_NO_MATCH:

                return "No speech recognized";

            case android.speech.SpeechRecognizer
                    .ERROR_RECOGNIZER_BUSY:

                return "Speech recognizer is busy";

            case android.speech.SpeechRecognizer
                    .ERROR_SERVER:

                return "Speech recognition server error";

            case android.speech.SpeechRecognizer
                    .ERROR_SPEECH_TIMEOUT:

                return "No speech detected";

            default:

                return "Unknown speech recognition error";
        }
    }
}