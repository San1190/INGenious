package com.ing.ide.main.testar.mcp;

import com.ing.ide.main.testar.mcp.helper.McpMethod;
import com.ing.ide.main.testar.mcp.helper.McpParam;

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

    @McpMethod(description = "Add a unique text assert as final step in the BDD test execution.")
    String addFinalAssert(
            @McpParam(name = "bddStep", description = "The BDD step text associated with this action.") String bddStep,
            @McpParam(name = "assertText", description = "A visible unique text that can be used as text locator assert.") String assertText
    );

    @McpMethod(description = "Stop the test execution when the final assert is completed successfully.")
    void stopTestExecution();

}