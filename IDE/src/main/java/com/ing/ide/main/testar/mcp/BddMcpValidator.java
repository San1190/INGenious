package com.ing.ide.main.testar.mcp;

/**
 * Apply the BDD-step validation/tracking before delegating to the technical MCP driver.
 */
public final class BddMcpValidator implements McpInterface {

    private final McpInterface mcpDriver;
    private final BddStepTracker tracker;

    public BddMcpValidator(McpInterface mcpDriver, BddStepTracker tracker) {
        this.mcpDriver = mcpDriver;
        this.tracker = tracker;
    }

    @Override
    public String loadWebURL(String bddStep, String url) {
        String validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        String result = mcpDriver.loadWebURL(bddStep, url);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public String getCurrentURL() {
        return mcpDriver.getCurrentURL();
    }

    @Override
    public String navigateBack() {
        return mcpDriver.navigateBack();
    }

    @Override
    public String getStateInteractiveWidgets() {
        return mcpDriver.getStateInteractiveWidgets();
    }

    @Override
    public String executeClickAction(String bddStep, String cssSelector) {
        String validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        String result = mcpDriver.executeClickAction(bddStep, cssSelector);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public String executeFillAction(String bddStep, String cssSelector, String fillText) {
        String validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        String result = mcpDriver.executeFillAction(bddStep, cssSelector, fillText);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public String executeSelectAction(String bddStep, String cssSelector, String optionValue) {
        String validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        String result = mcpDriver.executeSelectAction(bddStep, cssSelector, optionValue);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public String checkExecutedActions() {
        return mcpDriver.checkExecutedActions();
    }

    @Override
    public String getStateImage() {
        return mcpDriver.getStateImage();
    }

    @Override
    public String getStateVisualText() {
        return mcpDriver.getStateVisualText();
    }

    @Override
    public String addStepAssert(String bddStep, String assertText) {
        String validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        String result = mcpDriver.addStepAssert(bddStep, assertText);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public void stopTestExecution() {
        mcpDriver.stopTestExecution();
    }

    private void recordIfSuccess(String bddStep, String result) {
        if (result != null && !result.startsWith("ISSUE")) {
            tracker.saveExecutedBddStep(bddStep);
        }
    }

}
