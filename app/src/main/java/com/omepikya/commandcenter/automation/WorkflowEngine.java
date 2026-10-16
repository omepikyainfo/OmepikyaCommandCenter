package com.omepikya.commandcenter.automation;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Phase 7H:
 * persistent named command workflows.
 */
public final class WorkflowEngine {

    private static final String PREFS =
            "omepikya_workflows";

    private final SharedPreferences prefs;

    public WorkflowEngine(
            Context context) {

        if (context == null) {
            throw new IllegalArgumentException(
                    "Context cannot be null");
        }

        prefs =
                context.getApplicationContext()
                        .getSharedPreferences(
                                PREFS,
                                Context.MODE_PRIVATE);
    }

    public synchronized boolean save(
            String name,
            List<String> commands) {

        String key =
                clean(name);

        if (key.isEmpty() ||
                commands == null ||
                commands.isEmpty() ||
                commands.size() > 12) {

            return false;
        }

        JSONArray array =
                new JSONArray();

        for (String command :
                commands) {

            if (command != null &&
                    !command.trim().isEmpty()) {

                array.put(
                        command.trim());
            }
        }

        if (array.length() == 0) {
            return false;
        }

        prefs.edit()
                .putString(
                        key,
                        array.toString())
                .apply();

        return true;
    }

    public synchronized List<String> load(
            String name) {

        String raw =
                prefs.getString(
                        clean(name),
                        null);

        if (raw == null) {
            return Collections.emptyList();
        }

        List<String> result =
                new ArrayList<>();

        try {

            JSONArray array =
                    new JSONArray(raw);

            for (int i = 0;
                    i < array.length();
                    i++) {

                String command =
                        array.optString(
                                i,
                                "").trim();

                if (!command.isEmpty()) {
                    result.add(command);
                }
            }

        } catch (Exception ignored) {
        }

        return result;
    }

    public synchronized boolean delete(
            String name) {

        String key =
                clean(name);

        if (key.isEmpty()) {
            return false;
        }

        boolean existed =
                prefs.contains(key);

        prefs.edit()
                .remove(key)
                .apply();

        return existed;
    }

    private String clean(
            String name) {

        if (name == null) {
            return "";
        }

        return name.trim()
                .toLowerCase(Locale.US)
                .replaceAll(
                        "[^a-z0-9_ -]",
                        "")
                .replaceAll(
                        "\\s+",
                        "_");
    }
}
