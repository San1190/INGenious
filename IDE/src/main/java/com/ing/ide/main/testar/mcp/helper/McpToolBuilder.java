package com.ing.ide.main.testar.mcp.helper;

import com.ing.ide.main.testar.mcp.McpInterface;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds OpenAI Chat Completions "tools" definitions from the annotated {@link McpInterface} using reflection.
 * <p>
 * This utility scans the given API interface for methods annotated with {@link McpMethod}
 * and parameters annotated with {@link McpParam}, and produces a List of Maps
 * ready to serialize as the request body’s {@code "tools"} array.
 */
public final class McpToolBuilder {

    private McpToolBuilder() {}

    public static List<Map<String,Object>> from(Class<?> apiInterface) {
        List<Map<String,Object>> functionsMap = new ArrayList<>();
        for (Method m : apiInterface.getMethods()) {
            McpMethod mcpMethod = m.getAnnotation(McpMethod.class);
            if (mcpMethod == null) continue;

            String name = mcpMethod.name().isEmpty() ? m.getName() : mcpMethod.name();
            Map<String,Object> props = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            for (Parameter p : m.getParameters()) {
                McpParam mcpParam = p.getAnnotation(McpParam.class);
                if (mcpParam == null) {
                    java.util.logging.Logger.getLogger(McpToolBuilder.class.getName()).log(
                            java.util.logging.Level.SEVERE,
                            "Parameter missing mcpParam on " + m
                    );
                    continue;
                }

                Map<String,Object> schema = new LinkedHashMap<>();
                schema.put("type", "string");
                if (!mcpParam.description().isEmpty()) schema.put("description", mcpParam.description());
                props.put(mcpParam.name(), schema);
                if (mcpParam.required()) required.add(mcpParam.name());
            }

            Map<String,Object> params = new LinkedHashMap<>();
            params.put("type", "object");
            params.put("properties", props);
            params.put("required", required);

            Map<String,Object> function = new LinkedHashMap<>();
            function.put("name", name);
            function.put("description", mcpMethod.description());
            function.put("parameters", params);

            Map<String,Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", function);

            functionsMap.add(tool);
        }
        return functionsMap;
    }

}
