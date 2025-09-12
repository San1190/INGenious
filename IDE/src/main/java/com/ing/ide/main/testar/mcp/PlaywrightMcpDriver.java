package com.ing.ide.main.testar.mcp;

import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.playwright.actions.PlaywrightClick;
import com.ing.ide.main.testar.playwright.actions.PlaywrightFill;
import com.ing.ide.main.testar.playwright.actions.PlaywrightSelect;
import com.ing.ide.main.testar.playwright.system.PlaywrightSUT;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightTags;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PlaywrightMcpDriver implements McpInterface {

    private static final Logger logger = LogManager.getLogger();

    private final TESTARDataWriter dataWriter;

    private PlaywrightSUT system;
    private Page page;
    private PlaywrightState state;

    private final List<String> executedAction = new ArrayList<>();

    // Define selectors for clickable elements
    private final static String[] clickableSelectors = {
            "a",                       // Links
            "button",                  // Buttons
            "input[type='button']",    // Input button
            "input[type='submit']",    // Input submit button
            "input[type='reset']",     // Input reset button
            "input[type='checkbox']",  // Checkbox inputs
            "input[type='radio']",     // Radio button inputs
            "[onclick]",               // Elements with onclick attributes (custom clickable elements)
            "[role='button']"          // Elements with a role attribute as buttons (often used in modern UIs)
    };

    // Define selectors for fillable elements
    private final static String[] fillableSelectors = {
            "input[type='text']",      // Text input fields
            "textarea",                // Text areas
            "input[class='input']",    // Input fields
            "input[type='email']",     // Email input fields
            "input[type='password']"   // Password input fields
    };

    // Define selectors for selectable elements
    private final static String[] selectableSelectors = {
            "select"                   // Dropdowns
    };

    public PlaywrightMcpDriver(Project project) {
        // Initialize the data writer for saving OR objects and steps
        this.dataWriter = new TESTARDataWriter(project);
    }

    @Override
    public String loadWebURL(String bddStep, String url){
        try {
            this.system = new PlaywrightSUT(url);
            this.page = this.system.getPage();
        } catch (Exception e) {
            logger.log(Level.ERROR, "Failed to run PlaywrightSUT with URL: " + url);
            logger.log(Level.ERROR, e.getMessage());
            return "ISSUE loading the Web URL: " + e.getMessage();
        }

        // Add browser control test step into INGenious
        dataWriter.addAbstractTestStep(
                bddStep,
                "Browser",
                "Open the Url [<Data>] in the Browser",
                "Open",
                "@".concat(url),
                ""
        );

        return "Web URL loaded successfully!";
    }

    @Override
    public String getCurrentURL() {
        if (this.page == null) return "ISSUE: No web state-page initialized";

        String url = this.page.url();
        if (url == null || url.isEmpty()) return "ISSUE: No web url available.";

        return url;
    }

    @Override
    public String navigateBack() {
        if (this.page == null) return "ISSUE: No web state-page initialized";

        Response response = this.page.goBack();
        if (response == null) return "ISSUE: Cannot navigate back - no previous page.";

        // Add browser control test step into INGenious
        dataWriter.addConcreteTestStep(
                "Browser",
                "Navigate to the previous page in history",
                "GoBack",
                "",
                ""
        );

        return String.format("Success navigating back to '%s'", response.url());
    }

    @Override
    public String getState() {
        if (this.page == null) return "ISSUE: No web state-page initialized";

        List<String> cssStateWidgets = new ArrayList<>();

        try {
            state = new PlaywrightState(system);

            String interactiveWidgetsSelector = String.join(", ", Stream.of(
                    Arrays.stream(clickableSelectors),
                    Arrays.stream(fillableSelectors),
                    Arrays.stream(selectableSelectors)
            ).flatMap(s -> s).toArray(String[]::new));
            List<ElementHandle> stateElements = state.getPage().querySelectorAll(interactiveWidgetsSelector);

            for (ElementHandle elementHandle : stateElements) {
                PlaywrightWidget widget = new PlaywrightWidget(state, state, elementHandle);

                // For widgets with CSS locators
                if(!widget.get(PlaywrightTags.WebLocatorCSS, "").isEmpty()
                        && !isExternalLink(state, widget.get(PlaywrightTags.WebHref, ""))) {

                    // Prepare the web widget context to be sent to the AI agent
                    Map<String, String> widgetInfo = new LinkedHashMap<>();
                    widgetInfo.put("css", widget.get(PlaywrightTags.WebLocatorCSS));
                    widgetInfo.put("role", widget.get(PlaywrightTags.WebTagName));

                    widgetInfo.put("placeholder", widget.get(PlaywrightTags.WebLocatorPlaceholder));
                    widgetInfo.put("label", widget.get(PlaywrightTags.WebLocatorLabel));
                    widgetInfo.put("alttext", widget.get(PlaywrightTags.WebLocatorAltText));

                    // For select elements list the available options
                    if ("select".equalsIgnoreCase(widget.get(PlaywrightTags.WebTagName, ""))) {
                        List<ElementHandle> options = elementHandle.querySelectorAll("option");
                        List<String> optionValues = new ArrayList<>();

                        for (ElementHandle option : options) {
                            String value = option.getAttribute("value");
                            optionValues.add(value != null ? value : "");
                        }

                        if (!optionValues.isEmpty()) {
                            widgetInfo.put("options", String.join(", ", optionValues));
                        }
                    }
                    // Otherwise, add the text content
                    else {
                        widgetInfo.put("text", widget.get(PlaywrightTags.WebLocatorText).replaceAll("\\s+", " ").trim());
                    }

                    // Then serialize each widget as JSON or custom line format:
                    cssStateWidgets.add(widgetInfo.entrySet().stream()
                            .map(e -> e.getKey() + ": " + e.getValue())
                            .collect(Collectors.joining(" | "))
                    );

                    // Save them in the INGenious object repository
                    try {
                        dataWriter.addWidgetObject(widget, page);
                    } catch (Exception e) {
                        logger.log(Level.ERROR, "Failed add action objects to the OR" + e.getMessage());
                    }
                }
            }

        } catch (PlaywrightException e) {
            logger.log(Level.ERROR, "Failed to collect state interactive elements: " + e.getMessage());
            return "ISSUE trying to obtain state information: " + e.getMessage();
        }

        return String.join("\n", cssStateWidgets);
    }

    private boolean isExternalLink(PlaywrightState state, String href) {
        if (href == null || href.isEmpty()) return false;

        href = href.trim().toLowerCase();

        // Consider these schemes always external
        if (href.startsWith("mailto:") || href.startsWith("tel:") || href.startsWith("javascript:")) return true;

        // Internal anchors or root-relative paths are internal
        if (href.startsWith("/") || href.startsWith("#")) return false;

        // Relative URLs without a scheme or domain are internal
        if (!href.startsWith("http://") && !href.startsWith("https://")) return false;

        // For full URLs, check domain
        String testDomain = extractHostDomain(state.getPage().url());
        return !href.contains(testDomain);
    }

    private String extractHostDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();

            if (host != null && host.startsWith("www.")) {
                host = host.substring(4);
            }

            return host;

        } catch (URISyntaxException e) {
            logger.log(Level.ERROR, "Exception extracting the host domain of: " + url);
        }

        return "";
    }

    @Override
    public String executeClickAction(String bddStep, String rawCssSelector) {
        if (this.page == null) return "ISSUE: No web state-page initialized";

        logger.log(Level.ERROR, "rawCssSelector: " + rawCssSelector);

        String cssSelector = normalizeCssSelector(rawCssSelector);

        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            logger.log(Level.ERROR, "ISSUE with an invalid CSS selector: " + rawCssSelector);
            return "ISSUE with an invalid CSS selector: " + rawCssSelector;
        }

        try {
            logger.log(Level.ERROR, "Normalized CSS Selector: " + cssSelector);

            ElementHandle elementHandle = this.page.querySelector(cssSelector);

            if (elementHandle == null) {
                logger.log(Level.ERROR, "ISSUE because no matching element found for CSS selector: " + cssSelector);
                return "ISSUE because no matching element found for CSS selector: " + cssSelector;
            }

            PlaywrightWidget widget = new PlaywrightWidget(state, state, elementHandle);
            PlaywrightClick clickAction = new PlaywrightClick(widget);
            dataWriter.addActionTestStep(bddStep, state, clickAction, page);

            clickAction.run(system, state, 0);

            String actionDescription = "Executed Click Action in the widget " + cssSelector;
            saveExecutedAction(actionDescription);
            return actionDescription;
        } catch (Exception e) {
            logger.log(Level.ERROR, "Failed to execute action for selector: " + cssSelector + " - " + e.getMessage(), e);
            return "ISSUE executing a click action: " + e.getMessage();
        }
    }

    @Override
    public String executeFillAction(String bddStep, String rawCssSelector, String fillText) {
        if (this.page == null) return "ISSUE: No web state-page initialized";

        logger.log(Level.ERROR, "rawCssSelector: " + rawCssSelector);

        String cssSelector = normalizeCssSelector(rawCssSelector);

        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            logger.log(Level.ERROR, "ISSUE with an invalid CSS selector: " + rawCssSelector);
            return "ISSUE with an invalid CSS selector: " + rawCssSelector;
        }

        try {
            logger.log(Level.ERROR, "Normalized CSS Selector: " + cssSelector);

            ElementHandle elementHandle = this.page.querySelector(cssSelector);

            if (elementHandle == null) {
                logger.log(Level.ERROR, "ISSUE because no matching element found for CSS selector: " + cssSelector);
                return "ISSUE because no matching element found for CSS selector: " + cssSelector;
            }

            PlaywrightWidget widget = new PlaywrightWidget(state, state, elementHandle);
            PlaywrightFill fillAction = new PlaywrightFill(widget, fillText);
            dataWriter.addActionTestStep(bddStep, state, fillAction, page);

            fillAction.run(system, state, 0);

            String actionDescription = "Executed Fill Action " + fillText + " in the widget " + cssSelector;
            saveExecutedAction(actionDescription);
            return actionDescription;
        } catch (Exception e) {
            logger.log(Level.ERROR, "Failed to execute action for selector: " + cssSelector + " - " + e.getMessage(), e);
            return "ISSUE executing a fill action: " + e.getMessage();
        }
    }

    @Override
    public String executeSelectAction(String bddStep, String rawCssSelector, String optionValue) {
        if (this.page == null) return "ISSUE: No web state-page initialized";

        logger.log(Level.ERROR, "rawCssSelector: " + rawCssSelector);

        String cssSelector = normalizeCssSelector(rawCssSelector);

        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            logger.log(Level.ERROR, "ISSUE with an invalid CSS selector: " + rawCssSelector);
            return "ISSUE with an invalid CSS selector: " + rawCssSelector;
        }

        try {
            logger.log(Level.ERROR, "Normalized CSS Selector: " + cssSelector);

            ElementHandle elementHandle = this.page.querySelector(cssSelector);

            if (elementHandle == null) {
                logger.log(Level.ERROR, "ISSUE because no matching element found for CSS selector: " + cssSelector);
                return "ISSUE because no matching element found for CSS selector: " + cssSelector;
            }

            PlaywrightWidget widget = new PlaywrightWidget(state, state, elementHandle);
            PlaywrightSelect selectAction = new PlaywrightSelect(widget, optionValue);
            dataWriter.addActionTestStep(bddStep, state, selectAction, page);

            selectAction.run(system, state, 0);

            String actionDescription = "Select value " + optionValue + " in the widget " + cssSelector;
            saveExecutedAction(actionDescription);
            return actionDescription;
        } catch (Exception e) {
            logger.log(Level.ERROR, "Failed to execute select action for selector: " + cssSelector + " - " + e.getMessage(), e);
            return "ISSUE executing a select action: " + e.getMessage();
        }
    }

    private String normalizeCssSelector(String rawSelector) {
        if (rawSelector == null) return null;

        // 1. Unescape common over-escaped characters
        String normalized = rawSelector
                .replaceAll("\\\\/", "/")
                .replaceAll("\\\\:", ":")
                .replaceAll("\\\\'", "'")
                .replaceAll("\\\\\"", "\"")
                .replaceAll("\\\\\\\\", "\\\\"); // double backslashes

        // 2. Trim and basic cleanup
        normalized = normalized.trim();

        // 3. Optional: Basic sanity check (e.g., must start with . or # or tag)
        if (!normalized.matches("^[.#\\[]?.+")) {
            return null;
        }

        return normalized;
    }

    private void saveExecutedAction(String actionDescription) {
        executedAction.add(actionDescription);
    }

    @Override
    public String checkExecutedActions() {
        if(executedAction.isEmpty()) return "No executed actions yet!";

        return String.join(", ", executedAction);
    }

    @Override
    public String getStateImage() {
        try {
            byte[] screenshotBytes = this.page.screenshot(
                    new Page.ScreenshotOptions().setFullPage(true)
            );
            return Base64.getEncoder().encodeToString(screenshotBytes);
        } catch (Exception e){
            logger.log(Level.ERROR, "Failed to obtain the getStateImage: " + e.getMessage());
            return ""; // This empty string is used in the MCPAgent switch
        }
    }

    @Override
    public String addStepAssert(String bddStep, String assertText) {
        if (this.page == null) return "ISSUE: No web state-page initialized";

        // Verify that the LLM assertText can be used as locator for assertion
        Locator locator = page.locator("text=" + assertText);
        if (locator.count() == 0 ) {
            return "ISSUE: The provided assert text to be used as locator does not match with any GUI web element. " +
                    "Try again with a correct text locator.";
        }
        else if (locator.count() > 1) {
            return "ISSUE: The provided assert text locator is not unique because matches more than one GUI web element";
        }
        else if (!locator.first().isVisible()) {
            return "ISSUE: The assert text locator is correct but the GUI web element is not visible. " +
                    "Try again with a correct text locator.";
        }

        // Save the execution steps when we have valid asserts for the BDD instructions
        dataWriter.addAssertTestStep(bddStep, state, assertText);
        dataWriter.saveExecutionSteps();

        return "Assertion created successfully!";
    }

    @Override
    public void stopTestExecution() {
        // At the end of the generated sequence, save the generated INGenious testCase
        dataWriter.saveExecutionSteps();
        // Then, close the playwright session
        this.system.stop();
    }

}
