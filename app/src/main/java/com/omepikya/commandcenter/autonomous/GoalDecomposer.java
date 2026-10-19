package com.omepikya.commandcenter.autonomous;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GoalDecomposer {

    private int maxSteps = 12;

    public List<String> decompose(
            String goal) {

        List<String> result =
                new ArrayList<>();

        if (goal == null ||
                goal.trim().isEmpty()) {

            return result;
        }

        String remaining =
                goal.trim();

        while (!remaining.isEmpty() &&
                result.size() < maxSteps) {

            Separator separator =
                    findSeparator(remaining);

            if (separator == null) {

                result.add(remaining);
                break;
            }

            String left =
                    remaining.substring(
                            0,
                            separator.index)
                            .trim();

            String right =
                    remaining.substring(
                            separator.end)
                            .trim();

            if (left.isEmpty() ||
                    right.isEmpty()) {

                result.add(remaining);
                break;
            }

            result.add(left);
            remaining = right;
        }

        // Never return a partial plan: reaching the step limit while work remains
        // would let the executor report success after silently dropping user intent.
        if (!remaining.isEmpty() && result.size() >= maxSteps) {
            return new ArrayList<>();
        }

        return result;
    }

    private Separator findSeparator(
            String value) {

        String lower =
                value.toLowerCase(
                        Locale.US);

        String[] separators = {
                ";",
                " and then ",
                " after that ",
                " followed by ",
                " then ",
                " also "
        };

        int bestIndex = -1;
        int bestEnd = -1;

        for (String separator :
                separators) {

            int index =
                    lower.indexOf(separator);

            if (index >= 0 &&
                    (bestIndex < 0 ||
                            index < bestIndex)) {

                bestIndex = index;
                bestEnd =
                        index +
                                separator.length();
            }
        }

        if (bestIndex < 0) {
            return null;
        }

        return new Separator(
                bestIndex,
                bestEnd);
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(
            int maxSteps) {

        this.maxSteps =
                Math.max(
                        1,
                        Math.min(
                                20,
                                maxSteps));
    }

    private static final class Separator {

        final int index;
        final int end;

        Separator(
                int index,
                int end) {

            this.index = index;
            this.end = end;
        }
    }
}