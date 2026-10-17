package com.omepikya.commandcenter.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Central voice subsystem.
 *
 * Handles:
 * - Speech recognition
 * - Speech-to-text normalization
 * - Text-to-speech
 * - Continuous listening
 *
 * Recognition and TTS completion are deliberately kept
 * completely separate.
 */
public class VoiceEngine {

    public interface Listener {

        void onListening();

        void onCommandReceived(
                String command
        );

        void onSpeechError(
                String error
        );

        void onSpeaking();

        /**
         * TTS completion ONLY.
         *
         * This is never called when speech recognition
         * finishes.
         */
        void onVoiceComplete();
    }

    private final SpeechRecognizer speechRecognizer;

    private final TextToSpeechManager tts;

    private final VoiceIntelligence intelligence =
            new VoiceIntelligence();

    private final ConversationSession session =
            new ConversationSession();

    private final Handler mainHandler =
            new Handler(
                    Looper.getMainLooper()
            );

    private Listener listener;

    private boolean continuous = false;

    private boolean shuttingDown = false;

    /*
     * Tracks whether Android speech recognition is
     * currently being used.
     */
    private boolean recognitionActive = false;

    /*
     * Prevents multiple startListening() calls from
     * racing each other.
     */
    private boolean startPending = false;

    /*
     * Used to distinguish a deliberately cancelled
     * recognition session from an actual recognition
     * failure.
     */
    private boolean cancellingRecognition = false;

    public VoiceEngine(
            Context context
    ) {

        if (context == null) {

            throw new IllegalArgumentException(
                    "Context cannot be null"
            );
        }

        Context applicationContext =
                context.getApplicationContext();

        speechRecognizer =
                new SpeechRecognizer(
                        applicationContext
                );

        tts =
                new TextToSpeechManager(
                        applicationContext
                );

        /*
         * ========================================================
         * TTS CALLBACKS
         * ========================================================
         */
        tts.setListener(
                new TextToSpeechManager.Listener() {

                    @Override
                    public void onReady() {
                        // TTS ready.
                    }

                    @Override
                    public void onError(
                            String error
                    ) {

                        Listener current;

                        synchronized (
                                VoiceEngine.this
                        ) {

                            current =
                                    listener;
                        }

                        if (current != null
                                && !shuttingDown) {

                            current.onSpeechError(
                                    error == null
                                            ? "Text-to-speech error."
                                            : error
                            );
                        }
                    }

                    @Override
                    public void onStart() {

                        Listener current;

                        synchronized (
                                VoiceEngine.this
                        ) {

                            current =
                                    listener;
                        }

                        if (current != null
                                && !shuttingDown) {

                            current.onSpeaking();
                        }
                    }

                    @Override
                    public void onComplete() {

                        /*
                         * CRITICAL:
                         *
                         * This is TTS completion ONLY.
                         */
                        Listener current;

                        synchronized (
                                VoiceEngine.this
                        ) {

                            current =
                                    listener;
                        }

                        if (current != null
                                && !shuttingDown) {

                            current.onVoiceComplete();
                        }
                    }
                }
        );

        /*
         * ========================================================
         * SPEECH RECOGNITION CALLBACKS
         * ========================================================
         */
        speechRecognizer.setListener(
                new SpeechRecognizer.Listener() {

                    @Override
                    public void onReady() {

                        if (shuttingDown) {
                            return;
                        }
                    }

                    @Override
                    public void onListening() {

                        if (shuttingDown) {
                            return;
                        }

                        synchronized (
                                VoiceEngine.this
                        ) {

                            recognitionActive =
                                    true;

                            startPending =
                                    false;

                            cancellingRecognition =
                                    false;
                        }

                        Listener current;

                        synchronized (
                                VoiceEngine.this
                        ) {

                            current =
                                    listener;
                        }

                        if (current != null) {

                            current.onListening();
                        }
                    }

                    @Override
                    public void onResult(
                            SpeechResult result
                    ) {

                        if (shuttingDown) {
                            return;
                        }

                        synchronized (
                                VoiceEngine.this
                        ) {

                            recognitionActive =
                                    false;

                            startPending =
                                    false;

                            cancellingRecognition =
                                    false;
                        }

                        if (result == null) {

                            notifySpeechError(
                                    "Speech recognition returned no result."
                            );

                            return;
                        }

                        if (result.isSuccess()) {

                            session.touch();

                            String text =
                                    intelligence
                                            .normalizeTranscript(
                                                    result.getText()
                                            );

                            if (text != null
                                    && !text.trim().isEmpty()) {

                                Listener current;

                                synchronized (
                                        VoiceEngine.this
                                ) {

                                    current =
                                            listener;
                                }

                                if (current != null
                                        && !shuttingDown) {

                                    current.onCommandReceived(
                                            text.trim()
                                    );
                                }

                            } else {

                                notifySpeechError(
                                        "I couldn't understand the speech."
                                );
                            }

                        } else {

                            String error =
                                    result.getErrorMessage();

                            if (error == null
                                    || error.trim().isEmpty()) {

                                error =
                                        "Speech recognition failed.";
                            }

                            notifySpeechError(
                                    error
                            );
                        }

                        /*
                         * IMPORTANT:
                         *
                         * Do NOT call onVoiceComplete().
                         *
                         * TTS has its own completion callback.
                         */
                    }

                    @Override
                    public void onError(
                            String error
                    ) {

                        if (shuttingDown) {
                            return;
                        }

                        synchronized (
                                VoiceEngine.this
                        ) {

                            recognitionActive =
                                    false;

                            startPending =
                                    false;
                        }

                        /*
                         * Ignore errors caused by an intentional
                         * cancellation/restart.
                         */
                        boolean wasCancelling;

                        synchronized (
                                VoiceEngine.this
                        ) {

                            wasCancelling =
                                    cancellingRecognition;

                            cancellingRecognition =
                                    false;
                        }

                        if (wasCancelling) {
                            return;
                        }

                        notifySpeechError(
                                normalizeRecognitionError(
                                        error
                                )
                        );
                    }

                    @Override
                    public void onComplete() {

                        if (shuttingDown) {
                            return;
                        }

                        synchronized (
                                VoiceEngine.this
                        ) {

                            recognitionActive =
                                    false;

                            startPending =
                                    false;
                        }

                        /*
                         * Recognition completion is NOT TTS
                         * completion.
                         *
                         * Therefore:
                         *
                         * listener.onVoiceComplete()
                         *
                         * MUST NOT be called here.
                         */

                        if (continuous
                                && session.isActive()
                                && !shuttingDown) {

                            scheduleRecognitionStart(
                                    400L
                            );
                        }
                    }
                }
        );
    }

    public synchronized void setListener(
            Listener listener
    ) {

        if (shuttingDown) {
            return;
        }

        this.listener =
                listener;
    }

    /**
     * Starts a fresh recognition session.
     *
     * If a previous recognition session is still active,
     * it is cancelled first and Android is given time to
     * release it before another session starts.
     */
    public synchronized void startListening() {

        if (shuttingDown) {
            return;
        }

        if (startPending) {
            return;
        }

        /*
         * Already listening.
         */
        if (recognitionActive) {
            return;
        }

        startPending =
                true;

        cancellingRecognition =
                true;

        try {

            /*
             * Ensure the previous recognition session is
             * completely cancelled before starting another.
             */
            speechRecognizer.cancel();

        } catch (Exception ignored) {
        }

        scheduleRecognitionStart(
                450L
        );
    }

    /**
     * Actually starts recognition after Android has had
     * time to release the previous recognizer session.
     */
    private void scheduleRecognitionStart(
            long delayMs
    ) {

        if (shuttingDown) {
            return;
        }

        mainHandler.postDelayed(
                () -> {

                    synchronized (
                            VoiceEngine.this
                    ) {

                        if (shuttingDown
                                || !startPending) {

                            return;
                        }

                        startPending =
                                false;

                        cancellingRecognition =
                                false;

                        recognitionActive =
                                false;
                    }

                    try {

                        speechRecognizer.startListening();

                    } catch (Exception e) {

                        synchronized (
                                VoiceEngine.this
                        ) {

                            recognitionActive =
                                    false;
                        }

                        notifySpeechError(
                                "Unable to start voice recognition. "
                                        + "Please try again."
                        );
                    }

                },
                Math.max(
                        100L,
                        delayMs
                )
        );
    }

    public synchronized void stopListening() {

        if (shuttingDown) {
            return;
        }

        continuous =
                false;

        session.stop();

        startPending =
                false;

        cancellingRecognition =
                true;

        try {

            speechRecognizer.stopListening();

        } catch (Exception ignored) {
        }

        recognitionActive =
                false;
    }

    public synchronized void cancelListening() {

        if (shuttingDown) {
            return;
        }

        continuous =
                false;

        session.stop();

        startPending =
                false;

        cancellingRecognition =
                true;

        try {

            speechRecognizer.cancel();

        } catch (Exception ignored) {
        }

        recognitionActive =
                false;
    }

    /**
     * Speaks text through TTS.
     */
    public synchronized void speak(
            String text
    ) {

        if (shuttingDown) {
            return;
        }

        if (text == null
                || text.trim().isEmpty()) {

            return;
        }

        /*
         * Do NOT start recognition here.
         *
         * MainActivity starts confirmation recognition
         * only after TTS calls onComplete().
         */
        tts.speak(
                text.trim()
        );
    }

    /**
     * True when Android's dedicated on-device speech recognizer is active.
     * False means the compatibility recognizer is in use; Android may still
     * require a network connection on those devices.
     */
    public synchronized boolean isUsingOnDeviceSpeechRecognition() {
        return !shuttingDown
                && speechRecognizer.isUsingOnDeviceRecognizer();
    }

    public static boolean isOnDeviceSpeechRecognitionAvailable(Context context) {
        return SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
    }

    public synchronized boolean isTextToSpeechReady() {

        return !shuttingDown
                && tts.isReady();
    }

    public synchronized void startContinuous() {

        if (shuttingDown) {
            return;
        }

        continuous =
                true;

        session.start();

        startListening();
    }

    public synchronized void stopContinuous() {

        continuous =
                false;

        session.stop();

        if (shuttingDown) {
            return;
        }

        startPending =
                false;

        cancellingRecognition =
                true;

        try {

            speechRecognizer.cancel();

        } catch (Exception ignored) {
        }

        recognitionActive =
                false;
    }

    public synchronized boolean isContinuous() {

        return continuous
                && session.isActive()
                && !shuttingDown;
    }

    public String normalizeTranscript(
            String text
    ) {

        return intelligence.normalizeTranscript(
                text
        );
    }

    public boolean isWakeWord(
            String text
    ) {

        return intelligence.isWakeWord(
                text
        );
    }

    private void notifySpeechError(
            String error
    ) {

        if (shuttingDown) {
            return;
        }

        Listener current;

        synchronized (
                this
        ) {

            current =
                    listener;
        }

        if (current == null) {
            return;
        }

        try {

            current.onSpeechError(
                    error == null
                            || error.trim().isEmpty()
                            ? "Speech recognition failed."
                            : error
            );

        } catch (Exception ignored) {
        }
    }

    /**
     * Converts low-level recognizer errors into messages
     * that are useful to the user.
     */
    private String normalizeRecognitionError(
            String error
    ) {

        if (error == null
                || error.trim().isEmpty()) {

            return "I couldn't hear you. Please try again.";
        }

        String value =
                error.trim();

        String lower =
                value.toLowerCase();

        if (lower.contains("client")
                && lower.contains("already")) {

            return "Voice recognition is still closing the previous session. Please try again.";
        }

        if (lower.contains("busy")) {

            return "The microphone is busy. Please try again.";
        }

        if (lower.contains("timeout")
                || lower.contains("timed out")) {

            return "I didn't hear anything. Please say YES or NO.";
        }

        if (lower.contains("no match")
                || lower.contains("no speech")) {

            return "I didn't catch that. Please say YES or NO.";
        }

        return value;
    }

    /**
     * Completely releases the voice subsystem.
     *
     * Safe to call more than once.
     */
    public synchronized void shutdown() {

        if (shuttingDown) {
            return;
        }

        shuttingDown =
                true;

        continuous =
                false;

        startPending =
                false;

        recognitionActive =
                false;

        cancellingRecognition =
                true;

        session.stop();

        mainHandler.removeCallbacksAndMessages(
                null
        );

        listener =
                null;

        try {

            speechRecognizer.cancel();

        } catch (Exception ignored) {
        }

        try {

            speechRecognizer.destroy();

        } catch (Exception ignored) {
        }

        try {

            tts.shutdown();

        } catch (Exception ignored) {
        }
    }
}