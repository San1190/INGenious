package com.ing.ide.main.testar.playwright.system;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import org.testar.monkey.Drag;
import org.testar.monkey.Util;
import org.testar.monkey.alayer.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaywrightWidget implements Widget, Serializable {

    PlaywrightState root;
    PlaywrightWidget parent;
    Map<Tag<?>, Object> tags = new HashMap<>();
    List<PlaywrightWidget> children = new ArrayList<>();

    private ElementHandle elementHandle;

    protected PlaywrightWidget(PlaywrightState state, PlaywrightWidget parent) {
        this.parent = parent;
        this.root = state;

        if (parent != null) {
            root.connect(parent, this);
        }
    }

    public PlaywrightWidget(PlaywrightState state, PlaywrightWidget parent, ElementHandle elementHandle) {
        this(state, parent);

        this.elementHandle = elementHandle;
        Page statePage = state.getPage();
        this.set(Tags.Shape, Rect.fromCoordinates(0, 0, statePage.viewportSize().width, statePage.viewportSize().height));
        this.set(Tags.Role, Roles.Widget);

        loadAttributes(this.elementHandle);
    }

    public ElementHandle getElementHandle() {
        return elementHandle;
    }

    private void loadAttributes(ElementHandle element) {
        // Evaluate a JavaScript function inside the browser context to extract multiple attributes
        Object result = element.evaluate("el => { " +
                "  return {" +
                "    id: el.id || ''," +
                "    name: el.name || ''," +
                "    tagName: (el.tagName || '').toLowerCase()," +
                "    textContent: el.textContent || ''," +
                "    value: el.value || ''," +
                "    placeholder: el.getAttribute('placeholder') || ''," +
                "    ariaLabel: el.getAttribute('aria-label') || ''," +
                "    role: el.getAttribute('role') || ''," +
                "    href: el.getAttribute('href') || ''," +
                "    type: el.getAttribute('type') || ''," +
                "    title: el.getAttribute('title') || ''," +
                "    innerText: el.innerText || ''" +
                "  };" +
                "}");

        Map<String, Object> attributes = (Map<String, Object>) result;

        this.set(PlaywrightTags.WebId, (String) attributes.get("id"));
        this.set(PlaywrightTags.WebName, (String) attributes.get("name"));
        this.set(PlaywrightTags.WebTagName, (String) attributes.get("tagName"));
        this.set(PlaywrightTags.WebValue, (String) attributes.get("value"));
        this.set(PlaywrightTags.WebTextContent, (String) attributes.get("textContent"));
        this.set(PlaywrightTags.WebPlaceholder, (String) attributes.get("placeholder"));
        this.set(PlaywrightTags.WebAriaLabel, (String) attributes.get("ariaLabel"));
        this.set(PlaywrightTags.WebRole, (String) attributes.get("role"));
        this.set(PlaywrightTags.WebHref, (String) attributes.get("href"));
        this.set(PlaywrightTags.WebType, (String) attributes.get("type"));
        this.set(PlaywrightTags.WebTitle, (String) attributes.get("title"));
        this.set(PlaywrightTags.WebInnerText, (String) attributes.get("innerText"));

        // TODO: Change by a configurable abstraction mechanism
        this.set(Tags.AbstractID, attributes.get("id") + "_" + attributes.get("name"));
        this.set(Tags.ConcreteID, attributes.get("id") + "_" + attributes.get("name") + "_" + attributes.get("textContent"));
    }

    final public void moveTo(Widget p, int idx) {
        root.setParent(this, p, idx);
    }

    public final PlaywrightWidget addChild() {
        return root.addChild(this);
    }

    public final PlaywrightState root() {
        return root;
    }

    public final PlaywrightWidget parent() {
        return root.getParent(this);
    }

    public final PlaywrightWidget child(int i) {
        return root.getChild(this, i);
    }

    public final void remove() {
        root.remove(this);
    }

    public final int childCount() {
        return root.childCount(this);
    }

    public final <T> T get(Tag<T> tag) {
        return root.get(this, tag);
    }

    public final <T> void set(Tag<T> tag, T value) {
        root.setTag(this, tag, value);
    }

    public final <T> T get(Tag<T> tag, T defaultValue) {
        return root.get(this, tag, defaultValue);
    }

    public final Iterable<Tag<?>> tags() {
        return root.tags(this);
    }

    public final void remove(Tag<?> tag) {
        root.remove(this, tag);
    }

    public String getRepresentation(String tab) {
        return "COMPLETE: PlaywrightWidget getRepresentation";
    }

    @Override
    public String toString(Tag<?>... tags) {
        return Util.widgetDesc(this, tags);
    }

    @Override
    public Drag[] scrollDrags(double scrollArrowSize, double scrollThick) {
        return null;
    }
}
