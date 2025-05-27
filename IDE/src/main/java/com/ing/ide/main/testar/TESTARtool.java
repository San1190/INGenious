package com.ing.ide.main.testar;

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
import com.ing.ide.main.testar.playwright.actions.PlaywrightRefresh;
import com.ing.ide.main.testar.playwright.system.PlaywrightSUT;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.ing.ide.main.testar.reporting.HtmlReport;
import com.ing.ide.main.testar.statemodel.StateModelConfig;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testar.CodingManager;
import org.testar.StateManagementTags;
import org.testar.monkey.alayer.*;
import org.testar.statemodel.StateModelManager;
import org.testar.statemodel.StateModelManagerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TESTARtool {
	private static final Logger logger = LogManager.getLogger();

	private final Project project;
	private final ObjectRepository objectRepository;
	private final WebOR webOR;
	private final String webSUT;
	private final int numberActions;
	private final String filterPattern;
	private final String suspiciousPattern;
	private final Map<String, String> triggerActionsMap;

	// Define selectors for clickable elements
	private final String[] clickableSelectors = {
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
	private final String[] fillableSelectors = {
			"input[type='text']",      // Text input fields
			"textarea",                // Text areas
			"input[class='input']",    // Input fields
			"input[type='email']",     // Email input fields
			"input[type='password']"   // Password input fields
	};

	public TESTARtool(Project project, String webSUT, Map<String, String> triggerActionsMap, int numberActions, String filterPattern, String suspiciousPattern) {
		this.project = project;
		this.webSUT = webSUT;
		this.triggerActionsMap = triggerActionsMap;
		this.numberActions = numberActions;
		this.filterPattern = filterPattern;
		this.suspiciousPattern = suspiciousPattern;

		// Prepare the INGenious object repository used to store steps information
		objectRepository = new ObjectRepository(project);
		webOR = objectRepository.getWebOR();
		webOR.setObjectRepository(objectRepository);

		initAbstractionIdentifiers();
	}

	private void initAbstractionIdentifiers(){
		// TODO: Make a real configurable abstraction mechanism in Actions, Widget, State
		List<Tag<?>> tagList = Collections.singletonList(StateManagementTags.getTagFromSettingsString("WebWidgetId"));
		Tag<?>[] abstractTags = tagList.toArray(new Tag<?>[0]);
		CodingManager.setCustomTagsForConcreteId(abstractTags);
		CodingManager.setCustomTagsForAbstractId(abstractTags);
	}

	public String generateSequence() {
		// Prepare an INGenious TestCase
		Scenario scenario = new Scenario(project, "TESTAR_Generated");
		TestCase testCase = createTestCase(scenario);

		// Initialize the State Model
		StateModelManager stateModelManager = StateModelManagerFactory.getStateModelManager(
				"model",
				"1",
				StateModelConfig.getDefaultConfig());
		stateModelManager.notifyTestSequencedStarted();

		// Initialize the TESTAR HTML report
		HtmlReport htmlReport = new HtmlReport();

		// Initialize the System Under Test
		// which initializes Playwright and launch Chromium
		PlaywrightSUT system = new PlaywrightSUT(webSUT);

		// By default, the SUT does not contains failures
		Verdict verdict = Verdict.OK;

		try {
			// Trigger initial actions steps (e.g., open browser, login)
			triggerInitialActionSteps(testCase, system, triggerActionsMap);

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
				Action selectedAction = selectRandomAction(state, actions);
				// Write selected action information in the HTML report
				htmlReport.addSelectedAction(selectedAction);
				// Save the selected action information into the state model
				stateModelManager.notifyActionExecution(selectedAction);

				// Execute the selected action
				executeAction(testCase, system, state, selectedAction);
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

	private TestCase createTestCase(Scenario scenario) {
		String urlCaseName = escapeURL(webSUT);
		String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
		return scenario.addTestCase(urlCaseName + "_sequence_" + timestamp);
	}

	private String escapeURL(String url) {
		if (url == null || url.isEmpty()) return "";
		String cleanUrl = url.replaceFirst("^(http[s]?://)", "");
		cleanUrl = cleanUrl.replaceAll("[^a-zA-Z0-9]", "_");
		cleanUrl = cleanUrl.replaceAll("_+", "_");
		if (cleanUrl.endsWith("_")) {
			cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
		}
		return cleanUrl;
	}

	private void triggerInitialActionSteps(TestCase testCase, PlaywrightSUT system, Map<String, String> triggerActionsMap) {
		// Add the open Browser step
		TestStep initialTestStep = testCase.addNewStep();
		initialTestStep.setObject("Browser");
		initialTestStep.setDescription("Open the testing URL");
		initialTestStep.setAction("Open");
		initialTestStep.setInput("@".concat(webSUT));

		try {
			// Add the state-page to the OR
			String webPageTitle = system.getPage().title();
			WebORPage webORPage = webOR.addPage(webPageTitle);

			// Iterate over the selector-value pairs
			for (Map.Entry<String, String> entry : triggerActionsMap.entrySet()) {
				String selector = entry.getKey();
				String value = entry.getValue();

				// Skip null or empty selectors (keys)
				if (selector == null || selector.trim().isEmpty()) continue;

				/**
				// Wait for each field to appear
				system.getPage().waitForSelector(selector);

				// Then, click or fill if value exists
				if(value.isEmpty()) system.getPage().click(selector);
				else system.getPage().fill(selector, value);
				 **/

				// Wait for element to be attached in the DOM
				Locator locator = system.getPage().locator(selector);
				locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));

				// Check if the element is visible
				boolean isVisible = locator.isVisible();

				if (isVisible) {
					locator.click();
				} else {
					// Force click even if it's hidden
					locator.click(new Locator.ClickOptions().setForce(true));
				}

				// Add the triggered action as INGenious TestStep
				TestStep testStep = testCase.addNewStep();
				testStep.setReference(webORPage.getName());
				ObjectGroup objectGroup = webORPage.addObjectGroup(selector);
				WebORObject webORObject = (WebORObject) objectGroup.addObject(selector);
				webORObject.setAttributeByName("css", selector);
				testStep.setObject(webORObject.getName());

				if(value.isEmpty()) {
					testStep.setAction("Click");
					testStep.setDescription("Click the [<Object>]");
				}
				else {
					testStep.setAction("Fill");
					testStep.setInput("@" + value);
					testStep.setDescription("Enter the value [<Data>] in the Field [<Object>]");
				}
			}
		} catch (Exception e) {
			logger.log(Level.ERROR, "Trigger Initial Actions failed: " + e.getMessage());
		}
	}

	private PlaywrightState getState(PlaywrightSUT system) {
		PlaywrightState state = new PlaywrightState(system);

		try {
			// Wait for page to finish loading before querying elements
			state.getPage().waitForLoadState(LoadState.LOAD);

			// TODO: Make this widget fetch process hierarchical
			//List<ElementHandle> stateElements = state.getPage().querySelectorAll("*");
			String interactiveWidgetsSelector = String.join(", ", Stream.concat(
					Arrays.stream(clickableSelectors),
					Arrays.stream(fillableSelectors)
			).toArray(String[]::new));
			List<ElementHandle> stateElements = state.getPage().querySelectorAll(interactiveWidgetsSelector);

			for (ElementHandle elementHandle : stateElements) {
				new PlaywrightWidget(state, state, elementHandle);
			}

		} catch (PlaywrightException e) {
			logger.log(Level.ERROR, "Failed to collect state due to navigation or page reload: " + e.getMessage());
		}

		return state;
	}

	private Verdict getVerdict(PlaywrightState state) {
		Page page = state.getPage();

		// Use evaluateHandle to run JS and filter DOM elements matching the pattern
		JSHandle handle = page.evaluateHandle(
				"patternStr => {" +
						"  const regex = new RegExp(patternStr, 'i');" +
						"  return Array.from(document.querySelectorAll('div, span, p, h1, h2, h3'))" +
						"    .filter(el => regex.test(el.textContent))" +
						"    .map(el => el.textContent);" +
						"}",
				suspiciousPattern
		);

		// Get the matching text contents as Java list
		List<String> matchingTexts = (List<String>) handle.jsonValue();

		if (matchingTexts != null && !matchingTexts.isEmpty()) {
			return new Verdict(Verdict.SEVERITY_FAIL, "Failure: " + matchingTexts.get(0).trim());
		}

		return Verdict.OK;
	}

	private Set<Action> deriveActions(PlaywrightState state) {
		// Compile the filter pattern into a regex
		Pattern pattern = Pattern.compile(filterPattern);

		Set<Action> actions = new HashSet<>();

		for (Widget widget : state) {
			ElementHandle element = ((PlaywrightWidget) widget).getElementHandle();

			if (element == null || !element.isVisible()) continue;

			Object result = element.evaluate(
					"(el, selectors) => {" +
							"  const [clickable, fillable] = selectors;" +
							"  return {" +
							"    isClickable: el.matches(clickable)," +
							"    isFillable: el.matches(fillable)," +
							"    text: el.textContent || ''," +
							"    href: el.getAttribute('href') || ''" +
							"  };" +
							"}",
					Arrays.asList(String.join(", ", clickableSelectors), String.join(", ", fillableSelectors))
			);

			Map<String, Object> map = (Map<String, Object>) result;

			boolean isClickable = Boolean.TRUE.equals(map.get("isClickable"));
			boolean isFillable = Boolean.TRUE.equals(map.get("isFillable"));
			String text = (String) map.get("text");
			String href = (String) map.get("href");


			if (isClickable && !pattern.matcher(text + href).find() && !isExternalLink(href)) {
				actions.add(new PlaywrightClick((PlaywrightWidget) widget));
			}

			if (isFillable && !pattern.matcher(text).find()) {
				actions.add(new PlaywrightFill((PlaywrightWidget) widget, RandomStringUtils.randomAlphabetic(10)));
			}
		}

		// If TESTAR was able to derive actions in this state
		if(!actions.isEmpty()){
			// Add the state-page to the OR
			String webPageTitle = state.getPage().title();
			WebORPage webORPage = webOR.addPage(webPageTitle);
			// Add the data of action-elements into the OR
			for(Action action : actions){
				try {
					addActionObject(webORPage, action);
				} catch (Exception e) {
					logger.log(Level.ERROR, "Failed add action objects to the OR" + e.getMessage());
				}
			}
		}

		// Return the derived actions
		return actions;
	}

	private boolean isExternalLink(String href) {
		if (href == null || href.isEmpty()) return false;

		href = href.trim().toLowerCase();

		// Consider these schemes always external
		if (href.startsWith("mailto:") || href.startsWith("tel:") || href.startsWith("javascript:")) return true;

		// Internal anchors or root-relative paths are internal
		if (href.startsWith("/") || href.startsWith("#")) return false;

		// Relative URLs without a scheme or domain are internal
		if (!href.startsWith("http://") && !href.startsWith("https://")) return false;

		// For full URLs, check domain
		String testDomain = extractHostDomain(webSUT);
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

	private Action selectRandomAction(PlaywrightState state, Set<Action> actions) {
		if(actions.isEmpty()){
			return new PlaywrightRefresh(state);
		} else {
			List<Action> actionList = new ArrayList<>(actions);
			return actionList.get(new Random().nextInt(actionList.size()));
		}
	}

	private void executeAction(TestCase testCase, PlaywrightSUT system, PlaywrightState state, Action action) {
		// Create an INGenious TestStep based on the TESTAR action to be executed
		try {
			addActionTestStep(testCase, state, action);
		} catch (Exception e) {
			logger.log(Level.ERROR, "Failed add executed action object to the OR" + e.getMessage());
		}

		try {
			action.run(system, state, 0.0);
		} catch(Exception e) {
			logger.log(Level.ERROR, e.getMessage());
		}
	}

	/** Add the action-element info into INGenious */
	private WebORObject addActionObject(WebORPage webORPage, Action action) {
		PlaywrightWidget playwrightWidget = (PlaywrightWidget) action.get(Tags.OriginWidget);
		ElementHandle elementHandle = playwrightWidget.getElementHandle();

		String elementDescription = describeElement(elementHandle);
		ObjectGroup objectGroup = webORPage.addObjectGroup(elementDescription);
		WebORObject webORObject = (WebORObject) objectGroup.addObject(elementDescription);
		applyCssLocator(webORObject, elementHandle);
		applyRoleLocator(webORObject, elementHandle);

		return webORObject;
	}

	/** Sets the best CSS locator attribute on the OR object */
	private void applyCssLocator(WebORObject obj, ElementHandle el) {
		String id = el.getAttribute("id");
		if (id != null && !id.isEmpty()) {
			obj.setCss("#" + id);
			return;
		}

		String name = el.getAttribute("name");
		if (name != null && !name.isEmpty()) {
			obj.setCss("[name='" + name + "']");
			return;
		}

		String href = el.getAttribute("href");
		if (href != null && !href.isEmpty()) {
			obj.setCss("a[href='" + href + "']");
			return;
		}

		String value = el.getAttribute("value");
		if (value != null && !value.isEmpty()) {
			obj.setCss("[value='" + value + "']");
		}
	}

	private boolean applyRoleLocator(WebORObject obj, ElementHandle el) {
		try {
			// Get the role attribute (explicit or implied)
			String role = el.getAttribute("role");
			if (role == null || role.isEmpty()) {
				// Attempt to derive role from tag if not explicitly set
				String tagName = el.evaluate("el => el.tagName.toLowerCase()").toString();
				switch (tagName) {
					case "button":
						role = "button";
						break;
					case "a":
						role = "link";
						break;
					case "input":
						String type = el.getAttribute("type");
						if ("checkbox".equalsIgnoreCase(type)) role = "checkbox";
						else if ("radio".equalsIgnoreCase(type)) role = "radio";
						else if ("button".equalsIgnoreCase(type)) role = "button";
						else if ("submit".equalsIgnoreCase(type)) role = "button";
						else role = "textbox";
						break;
					case "select":
						role = "combobox";
						break;
					case "textarea":
						role = "textbox";
						break;
					default:
						// unsupported tag
						return false;
				}
			}

			// Try to get accessible name
			String name = el.evaluate("el => el.ariaLabel || el.getAttribute('aria-label') || el.innerText || el.getAttribute('title') || el.value || ''").toString().trim();
			if (name.isEmpty()) {
				return false; // no accessible name, skip
			}

			// Save in the format: "ROLE;NAME"
			String rolename = role.toUpperCase() + ";" + name;
			obj.setAttributeByName("Role", rolename);
			return true;

		} catch (Exception e) {
			// Log if needed
			return false;
		}
	}

	public String describeElement(ElementHandle element) {
		// Get visual element descriptor
		String name = getFirstNonEmpty(
				element.innerText().trim(),
				element.getAttribute("name"),
				element.getAttribute("id"),
				element.getAttribute("aria-label"),
				element.getAttribute("placeholder"),
				element.getAttribute("value")
		);
		String descriptor = name.replaceAll("\\s+", "");
		if (descriptor.isEmpty()) descriptor = "unnamed";

		// Get type of the element
		String tag = element.evaluate("el => el.tagName.toLowerCase()").toString();
		String type = element.getAttribute("type");
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
				ElementHandle selectedOption = element.querySelector("option[selected]");
				if (selectedOption == null) {
					selectedOption = element.querySelector("option");
				}
				String selectedText = selectedOption != null ? selectedOption.innerText().trim() : "";
				String selectDescriptor = getFirstNonEmpty(
						element.getAttribute("name"),
						element.getAttribute("id"),
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

}
