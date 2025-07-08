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
import com.ing.ide.main.testar.playwright.system.PlaywrightSUT;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightTags;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testar.monkey.alayer.Action;
import org.testar.monkey.alayer.Tags;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
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

    private List<String> executedAction = new ArrayList<>();

    // Define selectors for clickable elements
    public final static String[] clickableSelectors = {
            "a",                       // Links
            "button",                  // Buttons
            "input[type='button']",    // Input button
            "input[type='submit']",    // Input submit button
            "input[type='reset']",     // Input reset button
            "input[type='checkbox']",  // Checkbox inputs
            "input[type='radio']",     // Radio button inputs
            "select",                  // Dropdowns
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

    public boolean loadWebURL(String url){
        try {
            this.system = new PlaywrightSUT(url);
            this.page = this.system.getPage();
        } catch (Exception e) {
            logger.log(Level.ERROR, "Failed to run PlaywrightSUT with URL: " + url);
            return false;
        }

        TestStep initialTestStep = testCase.addNewStep();
        initialTestStep.setObject("Browser");
        initialTestStep.setDescription("Open the Url [<Data>] in the Browser");
        initialTestStep.setAction("Open");
        initialTestStep.setInput("@".concat(url));

        return true;
    }

    public List<String> getState() {
        state = new PlaywrightState(system);
        List<String> cssStateWidgets = new ArrayList<>();

        try {
            String interactiveWidgetsSelector = String.join(", ", Stream.concat(
                    Arrays.stream(clickableSelectors),
                    Arrays.stream(fillableSelectors)
            ).toArray(String[]::new));
            stateElements = state.getPage().querySelectorAll(interactiveWidgetsSelector);

            for (ElementHandle elementHandle : stateElements) {
                PlaywrightWidget widget = new PlaywrightWidget(state, state, elementHandle);

                // For widgets with CSS locators
                if(!widget.get(PlaywrightTags.WebLocatorCSS, "").isEmpty()
                        && !isExternalLink(state, widget.get(PlaywrightTags.WebHref, ""))) {
                    // Send it to the AI agent
                    cssStateWidgets.add(widget.get(PlaywrightTags.WebLocatorCSS));
                    // Save them in the INGenious object repository
                    String webPageTitle = state.getPage().title();
                    WebORPage webORPage = webOR.addPage(webPageTitle);
                    try {
                        addActionObject(webORPage, new PlaywrightClick(widget));
                    } catch (Exception e) {
                        logger.log(Level.ERROR, "Failed add action objects to the OR" + e.getMessage());
                    }
                }
            }

        } catch (PlaywrightException e) {
            logger.log(Level.ERROR, "Failed to collect state interactive elements: " + e.getMessage());
        }

        return cssStateWidgets;
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
        if (this.page == null) return "Error: No page initialized";

        logger.log(Level.ERROR, "rawCssSelector: " + rawCssSelector);

        String cssSelector = normalizeCssSelector(rawCssSelector);

        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            logger.log(Level.ERROR, "Invalid CSS selector: " + rawCssSelector);
            return "Error: Invalid CSS selector";
        }

        try {
            logger.log(Level.ERROR, "Normalized CSS Selector: " + cssSelector);

            ElementHandle elementHandle = this.page.querySelector(cssSelector);

            if (elementHandle == null) {
                logger.log(Level.ERROR, "No matching element found: " + cssSelector);
                return "No matching element found";
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
            return "Error executing action: " + e.getMessage();
        }
    }

    public String executeFillAction(String bddStep, String rawCssSelector, String fillText) {
        if (this.page == null) return "Error: No page initialized";

        logger.log(Level.ERROR, "rawCssSelector: " + rawCssSelector);

        String cssSelector = normalizeCssSelector(rawCssSelector);

        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            logger.log(Level.ERROR, "Invalid CSS selector: " + rawCssSelector);
            return "Error: Invalid CSS selector";
        }

        try {
            logger.log(Level.ERROR, "Normalized CSS Selector: " + cssSelector);

            ElementHandle elementHandle = this.page.querySelector(cssSelector);

            if (elementHandle == null) {
                logger.log(Level.ERROR, "No matching element found: " + cssSelector);
                return "No matching element found";
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
            return "Error executing action: " + e.getMessage();
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

    public List<String> checkExecutedActions() {
        return executedAction;
    }

    public String getStateImage() {
        if (this.page == null) return "Error: No page initialized";

        byte[] screenshotBytes = this.page.screenshot(
                new Page.ScreenshotOptions().setFullPage(true)
        );
        return Base64.getEncoder().encodeToString(screenshotBytes);
    }

    public void stopTest(String assertText) {
        addAssertTestStep(testCase, assertText);
        shutdown();
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
    private WebORObject addActionObject(WebORPage webORPage, Action action) {
        PlaywrightWidget playwrightWidget = (PlaywrightWidget) action.get(Tags.OriginWidget);

        String elementDescription = describeElement(playwrightWidget);
        ObjectGroup objectGroup = webORPage.addObjectGroup(elementDescription);
        WebORObject webORObject = (WebORObject) objectGroup.addObject(elementDescription);

        webORObject.setAttributeByName("Role", playwrightWidget.get(PlaywrightTags.WebLocatorRole));
        webORObject.setXpath(playwrightWidget.get(PlaywrightTags.WebLocatorXPath));
        webORObject.setAttributeByName("Text", playwrightWidget.get(PlaywrightTags.WebLocatorText));
        webORObject.setCss(playwrightWidget.get(PlaywrightTags.WebLocatorCSS));
        webORObject.setAttributeByName("Placeholder", playwrightWidget.get(PlaywrightTags.WebLocatorPlaceholder));
        webORObject.setAttributeByName("Label", playwrightWidget.get(PlaywrightTags.WebLocatorLabel));
        webORObject.setAttributeByName("AltText", playwrightWidget.get(PlaywrightTags.WebLocatorAltText));
        webORObject.setAttributeByName("Title", playwrightWidget.get(PlaywrightTags.WebLocatorTitle));
        webORObject.setAttributeByName("TestId", playwrightWidget.get(PlaywrightTags.WebLocatorTestId));

        return webORObject;
    }

    private String describeElement(PlaywrightWidget widget) {
        // Get visual element descriptor
        String name = getFirstNonEmpty(
                widget.get(PlaywrightTags.WebInnerText, "").trim(),
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
        WebORObject webORObject = addActionObject(webORPage, action);
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
        else testStep.setAction("Unknown");
    }

    private void addAssertTestStep(TestCase testCase, String assertText) {
        // Add the state-page to the OR
        String webPageTitle = state.getPage().title();
        WebORPage webORPage = webOR.addPage(webPageTitle);

        // Add the element to the OR
        String elementDescription = "assert[text]";
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
