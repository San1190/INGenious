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

        // TODO: Change by a configurable abstraction mechanism
        this.set(Tags.AbstractID, getAttributes(elementHandle));
        this.set(Tags.ConcreteID, getAttributes(elementHandle));
    }

    public ElementHandle getElementHandle() {
        return elementHandle;
    }

    private String getAttributes(ElementHandle element) {
        // Evaluate a JavaScript function inside the browser context
        Object result = element.evaluate("el => { " +
                "  return {" +
                "    id: el.id || ''," +
                "    name: el.name || ''," +
                "    tagName: (el.tagName || '').toLowerCase()," +
                "    textContent: el.textContent || ''" +
                "  };" +
                "}");

        Map<String, Object> attributes = (Map<String, Object>) result;

        String id = (String) attributes.get("id");
        String name = (String) attributes.get("name");
        String tagName = (String) attributes.get("tagName");
        String textContent = (String) attributes.get("textContent");

        return (!id.isEmpty() ? id : "") +
                (!name.isEmpty() ? "-" + name : "") +
                (!tagName.isEmpty() ? "-" + tagName : "") +
                (!textContent.isEmpty() ? "-" + textContent : "");
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
