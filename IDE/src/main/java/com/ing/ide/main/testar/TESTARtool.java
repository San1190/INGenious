package com.ing.ide.main.testar;

import com.ing.datalib.component.Project;
import com.ing.datalib.component.Scenario;
import com.ing.datalib.component.TestCase;
import com.ing.datalib.component.TestStep;
import com.ing.datalib.or.ObjectRepository;
import com.ing.datalib.or.common.ORObjectInf;
import com.ing.datalib.or.common.ObjectGroup;
import com.ing.datalib.or.web.WebOR;
import com.ing.datalib.or.web.WebORObject;
import com.ing.datalib.or.web.WebORPage;
import com.ing.ide.main.testar.playwright.actions.PlaywrightClick;
import com.ing.ide.main.testar.playwright.actions.PlaywrightFill;
import com.ing.ide.main.testar.playwright.system.PlaywrightSUT;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.ing.ide.main.testar.reporting.HtmlReport;
import com.ing.ide.main.testar.statemodel.StateModelConfig;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testar.CodingManager;
import org.testar.StateManagementTags;
import org.testar.monkey.alayer.*;
import org.testar.statemodel.StateModelManager;
import org.testar.statemodel.StateModelManagerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class TESTARtool {
	private static final Logger logger = LogManager.getLogger();

	private final Project project;
	private final String webSUT;
	private final int numberActions;
	private final String filterPattern;
	private final String suspiciousPattern;
	private final Map<String, String> triggerActionsMap;

	public TESTARtool(Project project, String webSUT, Map<String, String> triggerActionsMap, int numberActions, String filterPattern, String suspiciousPattern) {
		this.project = project;
		this.webSUT = webSUT;
		this.triggerActionsMap = triggerActionsMap;
		this.numberActions = numberActions;
		this.filterPattern = filterPattern;
		this.suspiciousPattern = suspiciousPattern;
	}

	public String generateSequence() {
		// Prepare INGenious object repository used to store steps information
		ObjectRepository objectRepository = new ObjectRepository(project);
		WebOR webOR = objectRepository.getWebOR();
		webOR.setObjectRepository(objectRepository);
		WebORPage webORPage = new WebORPage("Parabank", webOR);
		webOR.setPages(Arrays.asList(webORPage));

		// Prepare an INGenious TestCase
		Scenario scenario = new Scenario(project, "Parabank");
		TestCase testCase = scenario.addTestCase("Parabank".concat("_sequence_" + 1));
		// Open Browser step
		TestStep initialTestStep = testCase.addNewStep();
		initialTestStep.setObject("Browser");
		initialTestStep.setDescription("Open the testing URL");
		initialTestStep.setAction("Open");
		initialTestStep.setInput("@".concat(webSUT));

		// TODO: Make a real configurable abstraction mechanism in Actions, Widget, State
		// Abstraction settings
		List<Tag<?>> tagList = Collections.singletonList(StateManagementTags.getTagFromSettingsString("WebWidgetId"));
		Tag<?>[] abstractTags = tagList.toArray(new Tag<?>[0]);
		CodingManager.setCustomTagsForConcreteId(abstractTags);
		CodingManager.setCustomTagsForAbstractId(abstractTags);

		// Initialize the State Model
		StateModelManager stateModelManager = StateModelManagerFactory.getStateModelManager(
				"model",
				"1",
				StateModelConfig.getDefaultConfig());
		stateModelManager.notifyTestSequencedStarted();

		// Initialize a TESTAR HTML report
		HtmlReport htmlReport = new HtmlReport();

		// Initialize the System Under Test
		// which initializes Playwright and launch Chromium
		PlaywrightSUT system = new PlaywrightSUT(webSUT);

		// By default, the SUT does not contains failures
		Verdict verdict = Verdict.OK;

		try {
			// Trigger initial actions (e.g., login)
			triggerInitialActions(system, triggerActionsMap);

			for(int i = 0; i < numberActions; i++) {
				// Get the State of the playwright page
				PlaywrightState state = getState(system);
				// Add the state information in the HTML report
				htmlReport.addState(state);

				// Apply test oracles into the state
				verdict = getVerdict(state);
				// If the test verdict is not OK, return the failure verdict
				if(!verdict.equals(Verdict.OK)) {
					htmlReport.addFinalVerdict(verdict);
					return verdict.toString();
				}

				// Derive TESTAR available actions to interact with web elements
				Set<Action> actions = deriveActions(state);
				// Control the web page by checking the state contains available actions
				actions = controlWebPage(state, actions);
				// Write available derived actions in the HTML report
				htmlReport.addDerivedActions(actions);
				// Save state and actions information into the state model
				stateModelManager.notifyNewStateReached(state, actions);

				// Select one of available actions
				Action selectedAction = selectRandomAction(actions);
				// Write selected action information in the HTML report
				htmlReport.addSelectedAction(selectedAction);
				// Save the selected action information into the state model
				stateModelManager.notifyActionExecution(selectedAction);

				// Add the TestStep into INGenious
				PlaywrightWidget playwrightWidget = (PlaywrightWidget) selectedAction.get(Tags.OriginWidget);
				ElementHandle elementHandle = playwrightWidget.getElementHandle();

				TestStep testStep = testCase.addNewStep();

				testStep.setReference(webORPage.getName());

				// TODO: Refactor hehe
				if(elementHandle.getAttribute("id") != null) {
					String id = elementHandle.getAttribute("id");
					ObjectGroup objectGroup = webORPage.addObjectGroup(id);
					WebORObject webORObject = (WebORObject) objectGroup.addObject(id);
					webORObject.setAttributeByName("css", "#" + id);
					testStep.setObject(webORObject.getName());
				}
				else if(elementHandle.getAttribute("name") != null) {
					String name = elementHandle.getAttribute("name");
					ObjectGroup objectGroup = webORPage.addObjectGroup(name);
					WebORObject webORObject = (WebORObject) objectGroup.addObject(name);
					webORObject.setAttributeByName("css", "[name='" + name + "']");
					testStep.setObject(webORObject.getName());
				}
				else if(elementHandle.getAttribute("href") != null) {
					String href = elementHandle.getAttribute("href");
					ObjectGroup objectGroup = webORPage.addObjectGroup(href);
					WebORObject webORObject = (WebORObject) objectGroup.addObject(href);
					webORObject.setAttributeByName("css", "a[href='" + href + "']");
					testStep.setObject(webORObject.getName());
				}
				else if(elementHandle.textContent() != null) {
					String textContent = elementHandle.textContent();
					ObjectGroup objectGroup = webORPage.addObjectGroup(textContent);
					WebORObject webORObject = (WebORObject) objectGroup.addObject(textContent);
					webORObject.setAttributeByName("Label", textContent);
					testStep.setObject(webORObject.getName());
				}
				else {
					ObjectGroup objectGroup = webORPage.addObjectGroup("Unknown");
					WebORObject webORObject = (WebORObject) objectGroup.addObject("Unknown");
				}

				testStep.setDescription(selectedAction.get(Tags.Desc, "NoDesc"));

				if(selectedAction instanceof PlaywrightClick)
					testStep.setAction("Click");
				else if(selectedAction instanceof PlaywrightFill) {
					testStep.setAction("Fill");
					testStep.setInput("@" + ((PlaywrightFill)selectedAction).getTypedText());
				}
				else testStep.setAction("Unknown");

				System.out.println("TestStep: " + testStep);

				// Execute the selected action
				executeAction(system, state, selectedAction);
			}

			// Get the last state
			PlaywrightState lastState = getState(system);
			// Apply test oracles into the last state
			verdict = getVerdict(lastState);
			// Add the final state information in the HTML report
			htmlReport.addState(lastState);
			// Add the final verdict information in the HTML report
			htmlReport.addFinalVerdict(verdict);
			// Save the final state and actions into the state model
			stateModelManager.notifyNewStateReached(lastState, deriveActions(lastState));

		} catch(Exception e) {
			logger.log(Level.ERROR, e.getMessage());
			return "Error occurred: " + e.getMessage();
		} finally {
			stateModelManager.notifyTestSequenceStopped();
			stateModelManager.notifyTestingEnded();
			system.getBrowser().close();
		}

		// At the end of the generated sequence, save the generated INGenious testCase
		testCase.save();
		scenario.save();
		objectRepository.save();
		project.save();

		// return the verdict, also applied in the lastState
		return verdict.toString();
	}

	private boolean triggerInitialActions(PlaywrightSUT system, Map<String, String> triggerActionsMap) {
		try {
			// Iterate over the selector-value pairs
			for (Map.Entry<String, String> entry : triggerActionsMap.entrySet()) {
				String selector = entry.getKey();
				String value = entry.getValue();

				// Wait for each field to appear
				system.getPage().waitForSelector(selector);

				// Then, click or fill if value exists
				if(value.isEmpty()) system.getPage().click(selector);
				else system.getPage().fill(selector, value);
			}

			return true;
		} catch (Exception e) {
			logger.log(Level.ERROR, "Trigger Initial Actions failed: " + e.getMessage());
			return false;
		}
	}

	private PlaywrightState getState(PlaywrightSUT system) {
		PlaywrightState state = new PlaywrightState(system);

		// TODO: Make this widget fetch process hierarchical
		List<ElementHandle> stateElements = state.getPage().querySelectorAll("*");
		for(ElementHandle elementHandle : stateElements){
			new PlaywrightWidget(state, state, elementHandle);
		}

		return state;
	}

	private Verdict getVerdict(PlaywrightState state) {
		Page statePage = state.getPage();

		// Define the regex pattern to search for "error" or "exception" (case-insensitive)
		Pattern errorPattern = Pattern.compile(suspiciousPattern, Pattern.CASE_INSENSITIVE);

		// Query all relevant elements that might contain error messages
		List<ElementHandle> messageElements = statePage.querySelectorAll("div, span, p, h1, h2, h3");

		// Iterate through the elements to check for error messages
		for (ElementHandle element : messageElements) {
			String textContent;
			try {
				// Get the text content of the element
				textContent = element.textContent();
			} catch (Exception e) {
				// If there's an issue fetching the innerText, skip this element
				continue;
			}

			// Check if the text matches the error pattern
			if (errorPattern.matcher(textContent).find()) {
				// Return the found error message
				new Verdict(Verdict.SEVERITY_FAIL, "Failure: " + textContent.trim());
			}
		}

		// If no error messages were found, return "OK"
		return Verdict.OK;
	}

	private Set<Action> deriveActions(PlaywrightState state) {
		// Define selectors for clickable elements
		String[] clickableSelectors = {
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
		String[] fillableSelectors = {
				"input[type='text']",      // Text input fields
				"textarea",                // Text areas
				"input[class='input']",    // Input fields
				"input[type='email']",     // Email input fields
				"input[type='password']"   // Password input fields
		};

		// Compile the filter pattern into a regex
		Pattern pattern = Pattern.compile(filterPattern);

		Set<Action> actions = new HashSet<>();

		for (Widget widget : state) {
			ElementHandle element = ((PlaywrightWidget) widget).getElementHandle();

			if (element == null || !element.isVisible()) continue;

			// Create click actions for non-filtered clickable elements
			for (String selector : clickableSelectors) {
				// Evaluate the element in the context of its clickable selector
				Object isClickable = element.evaluate(String.format("el => el.matches(\"%s\")", selector));
				if (isClickable instanceof Boolean && (Boolean) isClickable) {
					String elementContent = element.textContent() + (element.getAttribute("href") != null ? element.getAttribute("href") : "");
					if (!pattern.matcher(elementContent).find()) {
						actions.add(new PlaywrightClick((PlaywrightWidget) widget));
					}
				}
			}

			// Create fill actions for non-filtered fillable elements
			for (String selector : fillableSelectors) {
				// Evaluate the element in the context of its fillable selector
				Object isFillable = element.evaluate(String.format("el => el.matches(\"%s\")", selector));
				if (isFillable instanceof Boolean && (Boolean) isFillable) {
					String elementContent = element.textContent();
					if (elementContent != null && !pattern.matcher(elementContent).find()) {
						actions.add(new PlaywrightFill((PlaywrightWidget) widget, RandomStringUtils.randomAlphabetic(10)));
					}
				}
			}
		}

		// Return the derived actions
		return actions;
	}

	//TODO: Derive a TESTARActionBack instead of navigate to the initial state
	private Set<Action> controlWebPage(PlaywrightState state, Set<Action> actions){
		Page statePage = state.getPage();

		// If TESTAR was not able to derive any action,
		// maybe because the DOM state does not contain interactive elements
		// Or if TESTAR is not in the webSUT URL
		if(actions.isEmpty() || !statePage.url().contains(webSUT)) {
			// Navigate to the original webSUT URL
			statePage.navigate(webSUT);
			// And derive again the available actions
			actions = deriveActions(state);
		}
		return actions;
	}

	private Action selectRandomAction(Set<Action> actions) {
		List<Action> actionList = new ArrayList<>(actions);
		return actionList.get(new Random().nextInt(actionList.size()));
	}

	private boolean executeAction(PlaywrightSUT system, PlaywrightState state, Action action) {
		try {
			action.run(system, state, 0.0);
		} catch(Exception e) {
			logger.log(Level.ERROR, e.getMessage());
			return false;
		}
		return true;
	}

}
