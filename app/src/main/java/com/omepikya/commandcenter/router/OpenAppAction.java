package com.omepikya.commandcenter.router;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.omepikya.commandcenter.bridge.SystemBridge;
import com.omepikya.commandcenter.core.CommandContext;
import com.omepikya.commandcenter.core.CommandResult;
import com.omepikya.commandcenter.core.CommandType;

import java.util.Locale;

public class OpenAppAction implements Action {

    private final Context appContext;
    private final SystemBridge systemBridge;

    public OpenAppAction(Context context) {

        if (context == null) {
            throw new IllegalArgumentException(
                    "Context cannot be null"
            );
        }

        appContext = context.getApplicationContext();
        systemBridge = new SystemBridge(context);
    }

    @Override
    public String getName() {
        return "Open App";
    }

    @Override
    public boolean canHandle(CommandContext context) {
        return context != null
                && context.getCommandType() == CommandType.OPEN_APP;
    }

    @Override
    public CommandResult execute(CommandContext context) {

        if (context == null) {
            return CommandResult.failure("Command context is null");
        }

        String appName = context.getParameter("app");

        if (appName == null || appName.trim().isEmpty()) {
            appName = extractAppName(context.getRawCommand());
        }

        if (appName == null || appName.trim().isEmpty()) {
            return CommandResult.failure("No application was specified");
        }

        appName = appName.trim();

        try {
            String packageName = systemBridge.findAppPackage(appName);

            if (packageName != null && systemBridge.openApp(packageName)) {
                return CommandResult.success("Opening " + appName);
            }

            /*
             * A browser fallback makes the common "open YouTube" command
             * useful on devices where the YouTube app is absent or hidden
             * by Android package-visibility restrictions.
             */
            if (isYouTube(appName) && openYouTubeInBrowser()) {
                return CommandResult.success(
                        "YouTube app not found; opening YouTube in your browser"
                );
            }

            if (packageName == null) {
                return CommandResult.failure(
                        "I couldn't find an installed app named " + appName
                );
            }

            return CommandResult.failure(
                    "I found " + appName
                            + ", but Android could not launch it"
            );

        } catch (Exception e) {
            return CommandResult.failure(
                    "Could not open " + appName
                            + " because Android rejected the launch request"
            );
        }
    }

    private boolean isYouTube(String appName) {
        if (appName == null) {
            return false;
        }

        String normalized = appName.trim().toLowerCase(Locale.US);
        return normalized.equals("youtube")
                || normalized.equals("you tube")
                || normalized.equals("yt")
                || normalized.equals("youtube app");
    }

    private boolean openYouTubeInBrowser() {
        Intent intent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/")
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            appContext.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractAppName(String command) {
        if (command == null) {
            return "";
        }

        String normalized = command.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        String lower = normalized.toLowerCase(Locale.US);
        for (String prefix : new String[]{"open ", "launch ", "start "}) {
            if (lower.startsWith(prefix)) {
                return normalized.substring(prefix.length()).trim();
            }
        }

        return "";
    }
}
