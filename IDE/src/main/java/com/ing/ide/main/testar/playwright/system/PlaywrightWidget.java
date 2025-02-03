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
        String id = element.getAttribute("id");
        String name = element.getAttribute("name");
        String tagName = element.getProperty("tagName").jsonValue().toString().toLowerCase();
        String textContent = element.textContent();

        return (id != null && !id.isEmpty() ? id : "") +
                (name != null && !name.isEmpty() ? "-" + name : "") +
                (!tagName.isEmpty() ? "-" + tagName : "") +
                (textContent != null && !textContent.isEmpty() ? "-" + textContent : "");
    }

    // TODO: Check the performance of this Dom Path feature
    private String generateDomPath(ElementHandle element) {
        StringBuilder path = new StringBuilder();
        ElementHandle current = element;

        while (current != null) {
            String tagName = current.getProperty("tagName").jsonValue().toString().toLowerCase();
            int index = getElementIndex(current);

            // Add the current tag and its index to the path
            path.insert(0, "/" + tagName + "[" + index + "]");

            // Get the parent element using evaluate and check if it exists
            current = current.evaluateHandle("el => el.parentElement").asElement();
        }
        return path.toString();
    }

    private int getElementIndex(ElementHandle element) {
        ElementHandle parent = element.evaluateHandle("el => el.parentElement").asElement();
        if (parent == null) {
            return 1; // Root element, always index 1
        }

        int index = 1;
        String tagName = element.getProperty("tagName").jsonValue().toString().toLowerCase();

        // Get all siblings with the same tag name
        List<ElementHandle> siblings = parent.querySelectorAll(tagName);

        // Count the position of the element among its siblings
        for (ElementHandle sibling : siblings) {
            if (sibling.equals(element)) {
                break;
            }
            index++;
        }
        return index;
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
