package com.ing.ide.main.testar;

import com.ing.datalib.component.*;
import com.ing.datalib.or.ObjectRepository;
import com.ing.datalib.or.common.ObjectGroup;
import com.ing.datalib.or.web.WebOR;
import com.ing.datalib.or.web.WebORObject;
import com.ing.datalib.or.web.WebORPage;
import com.ing.ide.main.mainui.components.testdesign.tree.model.ReusableTreeModel;
import com.ing.ide.main.testar.playwright.actions.PlaywrightClick;
import com.ing.ide.main.testar.playwright.actions.PlaywrightFill;
import com.ing.ide.main.testar.playwright.actions.PlaywrightSelect;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightTags;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testar.monkey.alayer.Action;
import org.testar.monkey.alayer.Tags;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

public class TESTARDataWriter {

    private static final Logger logger = LogManager.getLogger();

    private final Project project;
    private final ObjectRepository objectRepository;
    private final WebOR webOR;
    private final Scenario scenario;
    private final TestCase mainTestCase;

    private final Project reusableProject;
    private final Scenario reusableScenario;

    public TESTARDataWriter(Project project) {
        // Prepare the INGenious Object Repository used to store steps information
        // This OR is common to all scenarios and test cases
        this.project = project;
        objectRepository = new ObjectRepository(project);
        webOR = objectRepository.getWebOR();
        webOR.setObjectRepository(objectRepository);

        // Prepare an INGenious Scenario in the Test Plan
        this.scenario = new Scenario(this.project, "MCP_Generated");
        // Prepare an INGenious TestCase in the created scenario of the Test Plan
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        this.mainTestCase = scenario.addTestCase(timestamp);

        // Prepare an INGenious reusable project
        this.reusableProject = new Project(project.getLocation());
        this.reusableProject.setName("BDD mapping");
        // Prepare an INGenious Scenario to be a reusable component
        this.reusableScenario = new Scenario(this.reusableProject, "StepsDefinition");
    }

    /** Add the action-element info into INGenious */
    public WebORObject addWidgetObject(PlaywrightWidget playwrightWidget, Page page) {
        WebORPage webORPage = webOR.addPage(page.title());

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

    public void addAbstractTestStep(
            String bddStep,
            String object,
            String description,
            String testAction,
            String input,
            String reference
    ) {
        String bddStepParsed = bddStep.replaceAll("[\\\\/?:*\"|><]", "_");
        String bddAction = "StepsDefinition:".concat(bddStepParsed);

        // First, create the abstract test step in the main test case of the test plan
        // Only if it does not already exist
        boolean exists = mainTestCase.getTestSteps()
                .stream()
                .anyMatch(testStep -> bddAction.equals(testStep.getAction()));
        if(!exists) {
            TestStep abstractTestStep = mainTestCase.addNewStep();
            abstractTestStep.setObject("Execute");
            abstractTestStep.setDescription(bddStep);
            abstractTestStep.setAction(bddAction);
        }

        // Second, create a test case in the reusable scenario
        // Only if it does not already exist
        TestCase reusableTestCase = reusableScenario.getTestCaseByName(bddStepParsed);
        if(reusableTestCase == null) {
            reusableTestCase = reusableScenario.addTestCase(bddStepParsed);
        }
        TestStep concreteTestStep = reusableTestCase.addNewStep();
        concreteTestStep.setObject(object);
        concreteTestStep.setDescription(description);
        concreteTestStep.setAction(testAction);
        concreteTestStep.setInput(input);
        concreteTestStep.setReference(reference);
        reusableTestCase.save(); // TODO: This is maybe not needed
    }

    public void addConcreteTestStep(
            String object,
            String description,
            String testAction,
            String input,
            String reference
    ) {
        TestStep concreteTestStep = mainTestCase.addNewStep();
        concreteTestStep.setObject(object);
        concreteTestStep.setDescription(description);
        concreteTestStep.setAction(testAction);
        concreteTestStep.setInput(input);
        concreteTestStep.setReference(reference);
    }

    /** Add the TestStep into INGenious */
    public void addActionTestStep(String bddStep, PlaywrightState state, Action action, Page page) {
        // Add the state-page to the OR
        String webPageTitle = state.getPage().title();
        WebORPage webORPage = webOR.addPage(webPageTitle);
        // Add the data of the selected action-element into the OR
        WebORObject webORObject = addWidgetObject((PlaywrightWidget) action.get(Tags.OriginWidget), page);

        // Create an INGenious action test step
        String object = webORObject.getName();
        String description = "";
        String testAction = "";
        String input = "";
        String reference = webORPage.getName();

        if(action instanceof PlaywrightClick) {
            description = "Click the [<Object>]";
            testAction = "Click";
        }
        else if(action instanceof PlaywrightFill) {
            description = "Enter the value [<Data>] in the Field [<Object>]";
            testAction = "Fill";
            input = "@" + ((PlaywrightFill)action).getTypedText();
        }
        else if(action instanceof PlaywrightSelect) {
            description = "Select item in [<Object>] which has text: [<Data>]";
            testAction = "SelectSingleByText";
            input = "@" + ((PlaywrightSelect)action).getOptionValue();
        }
        else testAction = "Unknown";

        addAbstractTestStep(
                bddStep,
                object,
                description,
                testAction,
                input,
                reference
        );
    }

    public void addAssertTestStep(String bddStep, PlaywrightState state, String assertText) {
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
        String object = webORObject.getName();
        String description = "Assert if [<Object>] is visible";
        String testAction = "assertElementIsVisible";
        String input = "";
        String reference = webORPage.getName();

        addAbstractTestStep(
                bddStep,
                object,
                description,
                testAction,
                input,
                reference
        );
    }

    public void saveExecutionSteps() {
        this.mainTestCase.save();
        this.scenario.save();
        this.objectRepository.save();
        this.project.save();

        this.reusableScenario.save();
        this.reusableProject.save();
        // TODO: Check how to save this as reusable component
        //ReusableTreeModel reusableTreeModel = new ReusableTreeModel();
        //reusableTreeModel.setProject(this.reusableProject);
        //reusableTreeModel.save();
    }

}
