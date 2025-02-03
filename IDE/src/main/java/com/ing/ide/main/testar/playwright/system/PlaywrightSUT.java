package com.ing.ide.main.testar.playwright.system;

import com.microsoft.playwright.*;
import org.testar.monkey.alayer.SUTBase;
import org.testar.monkey.alayer.exceptions.SystemStopException;

public class PlaywrightSUT extends SUTBase {

    private final Browser browser;
    private final BrowserContext browserContext;
    private final Page page;

    public PlaywrightSUT(String webSUT) {
        Playwright playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        this.browserContext = browser.newContext();
        this.page = browser.newPage();
        this.page.setDefaultTimeout(2000); // 2 seconds of timeout
        page.navigate(webSUT); // Navigate to the webSUT URL
    }

    public Browser getBrowser() {
        return browser;
    }

    public Page getPage() {
        return page;
    }

    @Override
    public void stop() throws SystemStopException {
        browserContext.close();
    }

    @Override
    public boolean isRunning() {
        return browserContext.pages().size() == 0;
    }

    @Override
    public String getStatus() {
        // Gather details of open pages
        StringBuilder pageDetails = new StringBuilder();
        for (Page page : browserContext.pages()) {
            String title = page.title();
            pageDetails.append(String.format(" | Title: %s ", title != null ? title : "Untitled"));
        }
        return "PlaywrightSUT status: " + pageDetails;
    }

    @Override
    public void setNativeAutomationCache() {

    }
}
