package com.omepikya.commandcenter.autonomous;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public final class AutonomousPersistence {

    private static final String PREF =
            "omepikya_autonomous_state";

    private static final String KEY_PLAN =
            "plan";

    private final SharedPreferences preferences;

    public AutonomousPersistence(
            Context context) {

        if (context == null) {

            throw new IllegalArgumentException(
                    "Context cannot be null");
        }

        preferences =
                context.getSharedPreferences(
                        PREF,
                        Context.MODE_PRIVATE);
    }

    public synchronized void save(
            AutonomousPlan plan) {

        if (plan == null ||
                plan.getGoal() == null) {

            return;
        }

        try {

            JSONObject root =
                    new JSONObject();

            AutonomousGoal goal =
                    plan.getGoal();

            root.put(
                    "id",
                    goal.getId());

            root.put(
                    "goal",
                    goal.getText());

            root.put(
                    "status",
                    goal.getStatus());

            root.put(
                    "createdAt",
                    goal.getCreatedAt());

            root.put(
                    "updatedAt",
                    goal.getUpdatedAt());

            root.put(
                    "cursor",
                    plan.getCursor());

            root.put(
                    "cancelled",
                    plan.isCancelled());

            JSONArray steps =
                    new JSONArray();

            for (AutonomousStep step :
                    plan.getSteps()) {

                JSONObject item =
                        new JSONObject();

                item.put(
                        "index",
                        step.getIndex());

                item.put(
                        "command",
                        step.getCommand());

                item.put(
                        "attempts",
                        step.getAttempts());

                item.put(
                        "completed",
                        step.isCompleted());

                item.put(
                        "failed",
                        step.isFailed());

                item.put(
                        "result",
                        step.getResult());

                item.put(
                        "startedAt",
                        step.getStartedAt());

                item.put(
                        "completedAt",
                        step.getCompletedAt());

                steps.put(item);
            }

            root.put(
                    "steps",
                    steps);

            preferences.edit()
                    .putString(
                            KEY_PLAN,
                            root.toString())
                    .apply();

        } catch (Exception ignored) {
            // Persistence must never crash command execution.
        }
    }

    public synchronized AutonomousPlan load() {

        String raw =
                preferences.getString(
                        KEY_PLAN,
                        null);

        if (raw == null ||
                raw.trim().isEmpty()) {

            return null;
        }

        try {

            JSONObject root =
                    new JSONObject(raw);

            String id =
                    root.optString(
                            "id",
                            "");

            String goalText =
                    root.optString(
                            "goal",
                            "");

            AutonomousGoal goal =
                    new AutonomousGoal(
                            id,
                            goalText);

            goal.setStatus(
                    root.optString(
                            "status",
                            AutonomousGoal.CREATED));

            AutonomousPlan plan =
                    new AutonomousPlan(goal);

            JSONArray steps =
                    root.optJSONArray(
                            "steps");

            if (steps != null) {

                for (int i = 0;
                        i < steps.length();
                        i++) {

                    JSONObject item =
                            steps.optJSONObject(i);

                    if (item == null) {
                        continue;
                    }

                    AutonomousStep step =
                            new AutonomousStep(
                                    item.optInt(
                                            "index",
                                            i),
                                    item.optString(
                                            "command",
                                            ""));

                    int attempts =
                            item.optInt(
                                    "attempts",
                                    0);

                    for (int a = 0;
                            a < attempts;
                            a++) {

                        step.incrementAttempts();
                    }

                    step.setResult(
                            item.optString(
                                    "result",
                                    null));

                    boolean completed =
                            item.optBoolean(
                                    "completed",
                                    false);

                    boolean failed =
                            item.optBoolean(
                                    "failed",
                                    false);

                    if (completed) {
                        step.setCompleted(true);
                    } else if (failed) {
                        step.setFailed(true);
                    }

                    plan.addStep(step);
                }
            }

            plan.setCursor(
                    root.optInt(
                            "cursor",
                            0));

            // Restore cancellation explicitly. Without this, a cancelled
            // saved plan would load as active and could execute on resume.
            if (root.optBoolean("cancelled", false)) {
                plan.cancel();
            }

            return plan;

        } catch (Exception ignored) {

            return null;
        }
    }

    public synchronized boolean hasSavedPlan() {

        return preferences.contains(
                KEY_PLAN);
    }

    public synchronized String getGoal() {

        AutonomousPlan plan = load();

        return plan == null ||
                plan.getGoal() == null
                ? ""
                : plan.getGoal().getText();
    }

    public synchronized int getCursor() {

        AutonomousPlan plan = load();

        return plan == null
                ? 0
                : plan.getCursor();
    }

    public synchronized String getStatus() {

        AutonomousPlan plan = load();

        return plan == null ||
                plan.getGoal() == null
                ? "IDLE"
                : plan.getGoal().getStatus();
    }

    public synchronized void clear() {

        preferences.edit()
                .remove(KEY_PLAN)
                .apply();
    }
}