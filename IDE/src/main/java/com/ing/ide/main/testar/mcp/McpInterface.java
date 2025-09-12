package com.ing.ide.main.testar.mcp;

import com.ing.ide.main.testar.mcp.helper.McpMethod;
import com.ing.ide.main.testar.mcp.helper.McpParam;
import com.ing.ide.main.testar.mcp.helper.McpToolBuilder;
import com.ing.ide.main.testar.mcp.helper.McpToolExecutor;

/**
 * Contract for all MCP tools exposed to the LLM agent.
 * <p>
 * Methods annotated with {@link McpMethod} becomes a callable "tool" in the Chat Completions API.
 * The {@link McpToolBuilder} reflects this interface to produce the tool definitions (name, description, JSON schema).
 * The {@link McpToolExecutor} dispatches tool calls to the implementing driver (e.g., {@link PlaywrightMcpDriver}).
 * <p>
 * Rules:
 * - Annotate each method with {@link McpMethod} to be visible to the agent.
 * - Annotate each parameter with {@link McpParam} to define its JSON name, description, and whether it is required.
 * - Write clear, action-oriented descriptions; the agent relies on them for intent and argument selection.
 * - Return values should be concise, serializable strings.
 */
public interface McpInterface {

    @McpMethod(description = "Load the web URL to be tested.")
    String loadWebURL(@McpParam(name = "url", description = "The web url to be tested.") String url);

    @McpMethod(description = "Get the current web url that is being tested.")
    String getCurrentURL();

    @McpMethod(description = "Navigate back to the previous web page.")
    String navigateBack();

    @McpMethod(description = "Get a list of current interactive GUI web elements with CSS selector, visible text, tag type, and accessibility attributes.")
    String getState();

    @McpMethod(description = "Use a CSS selector to click an element.")
    String executeClickAction(
            @McpParam(name = "bddStep", description = "The BDD step text associated with this action.") String bddStep,
            @McpParam(name = "cssSelector", description = "The CSS selector of the clickable element.") String cssSelector
    );

    @McpMethod(description = "Use a CSS selector to fill text into an input element.")
    String executeFillAction(
            @McpParam(name = "bddStep", description = "The BDD step text associated with this action.") String bddStep,
            @McpParam(name = "cssSelector", description = "The CSS selector of the input element to be filled.") String cssSelector,
            @McpParam(name = "fillText", description = "The text to be entered into the input element.") String fillText
    );

    @McpMethod(description = "Use a CSS selector to select an option value into a select element.")
    String executeSelectAction(
            @McpParam(name = "bddStep", description = "The BDD step text associated with this action.") String bddStep,
            @McpParam(name = "cssSelector", description = "The CSS selector of the select element to be changed.") String cssSelector,
            @McpParam(name = "optionValue", description = "The option value to be selected into the select element.") String optionValue
    );

    @McpMethod(description = "Check the information of the executed actions during the BDD test process.")
    String checkExecutedActions();

    @McpMethod(description = "Get an image of the current GUI state.")
    String getStateImage();

    @McpMethod(description = "Add a unique text that can be used to assert a BDD step.")
    String addStepAssert(
            @McpParam(name = "bddStep", description = "The BDD step text associated with this action.") String bddStep,
            @McpParam(name = "assertText", description = "A visible unique text that can be used as text locator assert.") String assertText
    );

    @McpMethod(description = "Stop the test execution when the final assert is completed successfully.")
    void stopTestExecution();

}