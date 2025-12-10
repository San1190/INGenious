package com.ing.ide.main.testar.mcp;

/**
 * Standardize agent MCP feedback.
 */
public final class Feedback {

    private final String message;
    private final boolean issue;

    private Feedback(String message, boolean issue) {
        this.message = message;
        this.issue = issue;
    }
    
    public static Feedback issue(String message) {
        String trimmed = message == null ? "" : message.trim();
        
        if(trimmed.isEmpty()) {
            return new Feedback("ISSUE: Unspecified issue.", true);
        }

        return new Feedback("ISSUE: " + trimmed, true);
    }

    public static Feedback validContext(String message) {
        return new Feedback(message, false);
    }

    public boolean isIssue() {
        return issue;
    }

    @Override
    public String toString() {
        return message;
    }

}
