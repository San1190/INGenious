package com.ing.ide.main.testar.mcp;

import com.ing.datalib.component.Project;
import com.ing.datalib.component.Scenario;
import com.ing.datalib.component.TestCase;
import com.ing.datalib.component.TestStep;
import com.ing.datalib.or.ObjectRepository;
import com.ing.datalib.or.common.ObjectGroup;
import com.ing.datalib.or.web.WebOR;
import com.ing.datalib.or.web.WebORObject;
import com.ing.datalib.or.web.WebORPage;
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
import org.testar.monkey.alayer.Action;
import org.testar.monkey.alayer.Tags;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MCPInterface {

    private static final Logger logger = LogManager.getLogger();

    private final Project project;
    private final ObjectRepository objectRepository;
    private final WebOR webOR;
    private final Scenario scenario;
    private final TestCase testCase;

    private PlaywrightSUT system;
    private Page page;

    private List<ElementHandle> stateElements = new ArrayList<>();
    private PlaywrightState state;

    private final List<String> executedAction = new ArrayList<>();

    // Define selectors for clickable elements
    public final static String[] clickableSelectors = {
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
    public final static String[] fillableSelectors = {
            "input[type='text']",      // Text input fields
            "textarea",                // Text areas
            "input[class='input']",    // Input fields
            "input[type='email']",     // Email input fields
            "input[type='password']"   // Password input fields
    };

    // Define selectors for selectable elements
    public final static String[] selectableSelectors = {
            "select"                   // Dropdowns
    };

    public MCPInterface(Project project) {
        // Prepare the INGenious object repository used to store steps information
        this.project = project;
        objectRepository = new ObjectRepository(project);
        webOR = objectRepository.getWebOR();
        webOR.setObjectRepository(objectRepository);

        // Prepare an INGenious TestCase
        this.scenario = new Scenario(project, "MCP_Generated");
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        this.testCase = scenario.addTestCase(timestamp);
    }

    public String loadWebURL(String url){
        try {
            this.system = new PlaywrightSUT(url);
            this.page = this.system.getPage();
        } catch (Exception e) {
            logger.log(Level.ERROR, "Failed to run PlaywrightSUT with URL: " + url);
            logger.log(Level.ERROR, e.getMessage());
            return "ISSUE loading the Web URL: " + e.getMessage();
        }

        // Add browser control test step into INGenious
        TestStep initialTestStep = testCase.addNewStep();
        initialTestStep.setObject("Browser");
        initialTestStep.setDescription("Open the Url [<Data>] in the Browser");
        initialTestStep.setAction("Open");
        initialTestStep.setInput("@".concat(url));

        return "Web URL loaded successfully!";
    }

    public String getCurrentURL() {
        if (this.page == null) return "ISSUE: No web state-page initialized";

        String url = this.page.url();
        if (url == null || url.isEmpty()) return "ISSUE: No web url available.";

        return url;
    }

    public String navigateBack() {
        if (this.page == null) return "ISSUE: No web state-page initialized";

        Response response = this.page.goBack();
        if (response == null) return "ISSUE: Cannot navigate back - no previous page.";

        // Add browser control test step into INGenious
        TestStep goBackTestStep = testCase.addNewStep();
        goBackTestStep.setObject("Browser");
        goBackTestStep.setDescription("Navigate to the previous page in history");
        goBackTestStep.setAction("GoBack");

        return String.format("Success navigating back to '%s'", response.url());
    }

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
            stateElements = state.getPage().querySelectorAll(interactiveWidgetsSelector);

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
                    String webPageTitle = state.getPage().title();
                    WebORPage webORPage = webOR.addPage(webPageTitle);
                    try {
                        addWidgetObject(webORPage, widget);
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
            addActionTestStep(testCase, state, clickAction);

            clickAction.run(system, state, 0);

            String actionDescription = "Executed Click Action in the widget " + cssSelector;
            saveExecutedAction(actionDescription);
            return actionDescription;
        } catch (Exception e) {
            logger.log(Level.ERROR, "Failed to execute action for selector: " + cssSelector + " - " + e.getMessage(), e);
            return "ISSUE executing a click action: " + e.getMessage();
        }
    }

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
            addActionTestStep(testCase, state, fillAction);

            fillAction.run(system, state, 0);

            String actionDescription = "Executed Fill Action " + fillText + " in the widget " + cssSelector;
            saveExecutedAction(actionDescription);
            return actionDescription;
        } catch (Exception e) {
            logger.log(Level.ERROR, "Failed to execute action for selector: " + cssSelector + " - " + e.getMessage(), e);
            return "ISSUE executing a fill action: " + e.getMessage();
        }
    }

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
            addActionTestStep(testCase, state, selectAction);

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

    public String checkExecutedActions() {
        if(executedAction.isEmpty()) return "No executed actions yet!";

        return String.join(", ", executedAction);
    }

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

    public String addFinalAssert(String bddStep, String assertText) {
        if (this.page == null) return "ISSUE: No web state-page initialized";

        // Verify that the LLM assertText can be used as locator for assertion
        Locator locator = page.locator("text=" + assertText);
        if (locator.count() == 0 ) {
            return "ISSUE: The provided assert text to be used as locator does not match with any GUI web element";
        }
        /*else if (locator.count() > 1) {
            return "ISSUE: The provided assert text locator is not unique because matches more than one GUI web element";
        }*/
        else if (!locator.first().isVisible()) {
            return "ISSUE: The assert text locator is correct but the GUI web element is not visible";
        }

        addAssertTestStep(testCase, assertText);

        return "OK";
    }

    public void shutdown() {
        // At the end of the generated sequence, save the generated INGenious testCase
        this.testCase.save();
        this.scenario.save();
        this.objectRepository.save();
        this.project.save();
        // Then, close the playwright session
        this.system.stop();
    }

    /** Add the action-element info into INGenious */
    private WebORObject addWidgetObject(WebORPage webORPage, PlaywrightWidget playwrightWidget) {
        String elementDescription = describeElement(playwrightWidget);
        ObjectGroup objectGroup = webORPage.addObjectGroup(elementDescription);
        WebORObject webORObject = (WebORObject) objectGroup.addObject(elementDescription);

        String text = playwrightWidget.get(PlaywrightTags.WebLocatorText);
        if (text != null && !text.isEmpty()) {
            String evaluatedText = evaluateLocatorWithExactFallback(
                    exact -> {
                        Page.GetByTextOptions opts = new Page.GetByTextOptions().setExact(exact);
                        return page.getByText(text, opts);
                    }, text
            );
            webORObject.setAttributeByName("Text", evaluatedText);
        }

        String label = playwrightWidget.get(PlaywrightTags.WebLocatorLabel);
        if (label != null && !label.isEmpty()) {
            String evaluatedLabel = evaluateLocatorWithExactFallback(
                    exact -> {
                        Page.GetByLabelOptions opts = new Page.GetByLabelOptions().setExact(exact);
                        return page.getByLabel(label, opts);
                    }, label
            );
            webORObject.setAttributeByName("Label", evaluatedLabel);
        }

        webORObject.setAttributeByName("Role", playwrightWidget.get(PlaywrightTags.WebLocatorRole));
        webORObject.setXpath(playwrightWidget.get(PlaywrightTags.WebLocatorXPath));
        webORObject.setCss(playwrightWidget.get(PlaywrightTags.WebLocatorCSS));
        webORObject.setAttributeByName("Placeholder", playwrightWidget.get(PlaywrightTags.WebLocatorPlaceholder));
        webORObject.setAttributeByName("AltText", playwrightWidget.get(PlaywrightTags.WebLocatorAltText));
        webORObject.setAttributeByName("Title", playwrightWidget.get(PlaywrightTags.WebLocatorTitle));
        webORObject.setAttributeByName("TestId", playwrightWidget.get(PlaywrightTags.WebLocatorTestId));

        return webORObject;
    }

    private String evaluateLocatorWithExactFallback(Function<Boolean, Locator> locatorBuilder, String value) {
        try {
            Locator locator = locatorBuilder.apply(false);
            int count = locator.count();
            if (count == 1) return value;

            // If default text or label locator matches more than 1 element
            // Try to automatically consider an exact locator
            if (count > 1) {
                Locator exactLocator = locatorBuilder.apply(true);
                int exactCount = exactLocator.count();
                if (exactCount == 1) return value + ";exact";
            }
        } catch (Exception e) {
            logger.log(Level.ERROR, "Locator evaluation failed for '{}'", value, e);
        }
        return value;
    }

    private String describeElement(PlaywrightWidget widget) {
        // Get visual element descriptor
        String name = getFirstNonEmpty(
                widget.get(PlaywrightTags.WebInnerText, "").trim(),
                widget.get(PlaywrightTags.WebLocatorLabel, "").trim(),
                widget.get(PlaywrightTags.WebName, ""),
                widget.get(PlaywrightTags.WebId, ""),
                widget.get(PlaywrightTags.WebAriaLabel, ""),
                widget.get(PlaywrightTags.WebPlaceholder, ""),
                widget.get(PlaywrightTags.WebValue, "")
        );
        String descriptor = name.replaceAll("\\s+", "");
        if (descriptor.isEmpty()) descriptor = "unnamed";

        // Get type of the element
        String tag = widget.get(PlaywrightTags.WebTagName, "");
        String type = widget.get(PlaywrightTags.WebType, "");
        String label;
        switch (tag) {
            case "button":
                label = "[button]";
                break;
            case "a":
                label = "[link]";
                break;
            case "input":
                if ("submit".equalsIgnoreCase(type) || "button".equalsIgnoreCase(type)) {
                    label = "[input-button]";
                } else {
                    label = "[input]";
                }
                break;
            case "select":
                // Handle <select> elements
                ElementHandle selectedOption = widget.getElementHandle().querySelector("option[selected]");
                if (selectedOption == null) {
                    selectedOption = widget.getElementHandle().querySelector("option");
                }
                String selectedText = selectedOption != null ? selectedOption.innerText().trim() : "";
                String selectDescriptor = getFirstNonEmpty(
                        widget.get(PlaywrightTags.WebName, ""),
                        widget.get(PlaywrightTags.WebId, ""),
                        selectedText
                );
                descriptor = selectDescriptor.replaceAll("\\s+", "");
                if (descriptor.isEmpty()) descriptor = "unnamed";
                label = "[select]";
                break;
            case "textarea":
                label = "[textarea]";
                break;
            case "label":
                label = "[label]";
                break;
            case "img":
                label = "[image]";
                break;
            case "svg":
                label = "[icon]";
                break;
            default:
                label = "[" + tag + "]";
                break;
        }

        return descriptor + label;
    }

    private String getFirstNonEmpty(String... values) {
        for (String val : values) {
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return "";
    }

    /** Add the TestStep into INGenious */
    private void addActionTestStep(TestCase testCase, PlaywrightState state, Action action) {
        // Add the state-page to the OR
        String webPageTitle = state.getPage().title();
        WebORPage webORPage = webOR.addPage(webPageTitle);
        // Add the data of the selected action-element into the OR
        WebORObject webORObject = addWidgetObject(webORPage, (PlaywrightWidget) action.get(Tags.OriginWidget));
        // Create an INGenious action test step
        TestStep testStep = testCase.addNewStep();
        testStep.setReference(webORPage.getName());
        testStep.setObject(webORObject.getName());
        if(action instanceof PlaywrightClick) {
            testStep.setAction("Click");
            testStep.setDescription("Click the [<Object>]");
        }
        else if(action instanceof PlaywrightFill) {
            testStep.setAction("Fill");
            testStep.setInput("@" + ((PlaywrightFill)action).getTypedText());
            testStep.setDescription("Enter the value [<Data>] in the Field [<Object>]");
        }
        else if(action instanceof PlaywrightSelect) {
            testStep.setAction("SelectSingleByText");
            testStep.setInput("@" + ((PlaywrightSelect)action).getOptionValue());
            testStep.setDescription("Select item in [<Object>] which has text: [<Data>]");
        }
        else testStep.setAction("Unknown");
    }

    private void addAssertTestStep(TestCase testCase, String assertText) {
        // Add the state-page to the OR
        String webPageTitle = state.getPage().title();
        WebORPage webORPage = webOR.addPage(webPageTitle);

        // Add the element to the OR
        String trimmedText = assertText.length() > 10 ? assertText.substring(0, 10) : assertText;
        String elementDescription = "assert" + trimmedText + "[text]";
        ObjectGroup objectGroup = webORPage.addObjectGroup(elementDescription);
        WebORObject webORObject = (WebORObject) objectGroup.addObject(elementDescription);
        webORObject.setAttributeByName("Text", assertText);

        // Create an INGenious action test step
        TestStep testStep = testCase.addNewStep();
        testStep.setReference(webORPage.getName());
        testStep.setObject(webORObject.getName());
        testStep.setAction("assertElementIsVisible");
        testStep.setDescription("Assert if [<Object>] is visible");
    }
}
