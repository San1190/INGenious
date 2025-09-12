package com.ing.ide.main.testar.mcp.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.ide.main.testar.mcp.McpInterface;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflection-based executor that dispatches MCP tool calls to a concrete implementation.
 * <p>
 * This component scans the annotated {@link McpInterface} for methods marked with {@link McpMethod},
 * indexes them by their exposed tool name (annotation {@code name} or Java method name),
 * records parameter order and JSON field names from {@link McpParam}, and at runtime invokes the matching method.
 */
public final class McpToolExecutor<T> {

    private static final Logger logger = LogManager.getLogger();

    private final T impl;
    private final ObjectMapper mapper;
    private final Map<String, Method> byName = new HashMap<>();
    private final Map<Method, List<String>> paramOrder = new HashMap<>();

    private McpToolExecutor(Class<T> api, T impl, ObjectMapper mapper) {
        this.impl = impl;
        this.mapper = mapper;

        for (Method m : api.getMethods()) {
            McpMethod mcpMethod = m.getAnnotation(McpMethod.class);
            if (mcpMethod == null) continue;

            String name = mcpMethod.name().isEmpty() ? m.getName() : mcpMethod.name();
            if (byName.putIfAbsent(name, m) != null) {
                logger.log(Level.ERROR, "Duplicate mcpMethod name: " + name);
                continue;
            }

            List<String> order = new ArrayList<>();
            for (Parameter p : m.getParameters()) {
                McpParam mcpParam = p.getAnnotation(McpParam.class);
                if (mcpParam == null) {
                    logger.log(Level.ERROR, "Missing mcpParam on " + m);
                    continue;
                }
                order.add(mcpParam.name());
            }
            paramOrder.put(m, order);
        }
    }

    public static <T> McpToolExecutor<T> of(Class<T> api, T impl, ObjectMapper mapper) {
        return new McpToolExecutor<>(api, impl, mapper);
    }

    public Object execute(String functionName, String argumentsJson) throws Exception {
        Method m = byName.get(functionName);
        if (m == null) {
            logger.log(Level.ERROR, "Unknown function: " + functionName);
            return "";
        }

        Object[] args = buildArgs(m, argumentsJson);
        Object result = m.invoke(impl, args);
        return result;
    }

    private Object[] buildArgs(Method m, String argumentsJson) throws Exception {
        Parameter[] ps = m.getParameters();
        if (ps.length == 0) return new Object[0];
        JsonNode root = (argumentsJson == null || argumentsJson.isBlank())
                ? mapper.createObjectNode()
                : mapper.readTree(argumentsJson);

        List<String> names = paramOrder.get(m);
        Object[] out = new Object[ps.length];
        for (int i = 0; i < ps.length; i++) {
            String jsonName = names.get(i);
            JsonNode node = root.get(jsonName);
            out[i] = mapper.convertValue(node, mapper.constructType(ps[i].getParameterizedType()));
        }
        return out;
    }
}