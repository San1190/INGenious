package com.ing.ide.main.testar.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class BddStepTracker {

    private final List<String> originalBddStepsList;
    private final List<String> executedSteps = new ArrayList<>();

    public BddStepTracker(String bddInstructions) {
        this.originalBddStepsList = parseBddInstructionList(bddInstructions);
    }

    private List<String> parseBddInstructionList(String bddInstructions) {
        if (bddInstructions == null || bddInstructions.isBlank()) return Collections.emptyList();
        return Arrays.stream(bddInstructions.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public String validateBddStep(String bddStep) {
        if (bddStep == null || bddStep.trim().isEmpty()) {
            return "ISSUE: Provided BDD step is empty or invalid.";
        }

        if (!isOriginalBddInstruction(bddStep)) {
            return "ISSUE: Provided BDD step does not seem to match with original BDD instructions.";
        }

        // If the step is not the latest and was already executed, the mapping is trying to be done with an old previous step
        if (!isLatestBddStep(bddStep) && hasExecutedBddStep(bddStep)) {
            String message = String.format(
                    "ISSUE: The provided BDD step '%s' is not the current or a new step. " +
                    "This may create a mismatched BDD assertion map. " + 
                    "Please refine the BDD step or just continue with other appropiated BDD steps.",
                    bddStep
            );
            return message;
        }

        return null;
    }

    public void saveExecutedBddStep(String bddStep){
        if (!hasExecutedBddStep(bddStep)) {
            executedSteps.add(bddStep);
        }
    }

    private boolean hasExecutedBddStep(String bddStep) {
        return executedSteps.stream().anyMatch(s -> s.equalsIgnoreCase(bddStep));
    }

    private boolean isLatestBddStep(String bddStep) {
        if (executedSteps.isEmpty()) return false;
        return executedSteps.get(executedSteps.size() - 1).equalsIgnoreCase(bddStep);
    }

    private boolean isOriginalBddInstruction(String bddStep) {
        return originalBddStepsList.stream().anyMatch(s -> s.equalsIgnoreCase(bddStep));
    }

}
