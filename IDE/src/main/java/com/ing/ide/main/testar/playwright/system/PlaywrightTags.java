package com.ing.ide.main.testar.playwright.system;

import org.testar.monkey.alayer.Tag;
import org.testar.monkey.alayer.TagsBase;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PlaywrightTags extends TagsBase {

    protected static final Set<Tag<?>> wdTags = new HashSet<>();

    private PlaywrightTags() { }

    public static final Tag<String> WebId = from("WebId", String.class);

    public static final Tag<String> WebName = from("WebName", String.class);

    public static final Tag<String> WebTagName = from("WebTagName", String.class);

    public static final Tag<String> WebValue = from("WebValue", String.class);

    public static final Tag<String> WebTextContent = from("WebTextContent", String.class);

    public static final Tag<String> WebPlaceholder = from("WebPlaceholder", String.class);

    public static final Tag<String> WebAriaLabel = from("WebAriaLabel", String.class);

    public static final Tag<String> WebRole = from("WebRole", String.class);

    public static final Tag<String> WebHref = from("WebHref", String.class);

    public static final Tag<String> WebType = from("WebType", String.class);

    public static final Tag<String> WebTitle = from("WebTitle", String.class);

    public static final Tag<String> WebInnerText = from("WebInnerText", String.class);

    public static Set<Tag<?>> getAllTags() {
        return tagSet;
    }

    protected static <T> Tag<T> from(String name, Class<T> valueType) {
        Tag<T> tag = TagsBase.from(name, valueType);
        wdTags.add(tag);
        return tag;
    }

    public static Set<Tag<?>> getPlaywrightTags() {
        return Collections.unmodifiableSet(wdTags);
    }
}