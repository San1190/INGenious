package com.ing.ide.main.testar.mcp.helper;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public final class McpFunctionBuilder {

    private static final Logger logger = LogManager.getLogger();

    private McpFunctionBuilder() {}

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
                    logger.log(Level.ERROR, "Parameter missing mcpParam on " + m);
                    continue;
                }

                Map<String,Object> schema = new LinkedHashMap<>();
                schema.put("type", "string");
                if (!mcpParam.description().isEmpty()) schema.put("description", mcpParam.description());
                props.put(mcpParam.name(), schema);
                if (mcpParam.required()) required.add(mcpParam.name());
            }

            Map<String,Object> params = Map.of(
                    "type", "object",
                    "properties", props,
                    "required", required
            );

            functionsMap.add(Map.of(
                    "name", name,
                    "description", mcpMethod.description(),
                    "parameters", params
            ));
        }
        return functionsMap;
    }

}
