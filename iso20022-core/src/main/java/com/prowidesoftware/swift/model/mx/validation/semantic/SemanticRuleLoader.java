package com.prowidesoftware.swift.model.mx.validation.semantic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SemanticRuleLoader {
    private static final Gson GSON = new Gson();

    /**
     * Loads rules from an InputStream (supports both JSON array format and single JSON object).
     * Validates required fields fail-fast and detects duplicate rule IDs.
     */
    public static List<SemanticRuleDefinition> loadJsonRules(InputStream is) {
        if (is == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || root.isJsonNull()) {
                return Collections.emptyList();
            }

            List<SemanticRuleDefinition> rules = new ArrayList<>();
            if (root.isJsonArray()) {
                JsonArray array = root.getAsJsonArray();
                for (JsonElement el : array) {
                    if (el.isJsonObject()) {
                        SemanticRuleDefinition rule = GSON.fromJson(el, SemanticRuleDefinition.class);
                        rules.add(rule);
                    } else {
                        throw new IllegalArgumentException("Array elements must be JSON objects");
                    }
                }
            } else if (root.isJsonObject()) {
                SemanticRuleDefinition rule = GSON.fromJson(root, SemanticRuleDefinition.class);
                rules.add(rule);
            } else {
                throw new IllegalArgumentException("Expected JSON array or JSON object for semantic rules");
            }

            Set<String> ruleIds = new HashSet<>();
            // Validate and enforce fail-fast structure
            for (SemanticRuleDefinition rule : rules) {
                if (rule == null) {
                    throw new IllegalArgumentException("Rule definition cannot be null");
                }
                if (rule.getRuleId() == null || rule.getRuleId().trim().isEmpty()) {
                    throw new IllegalArgumentException("ruleId is required in JSON rule");
                }
                if (!ruleIds.add(rule.getRuleId())) {
                    throw new IllegalArgumentException("Duplicate rule ID detected: " + rule.getRuleId());
                }
                if (rule.getMessageType() == null
                        || rule.getMessageType().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "messageType is required in JSON rule [" + rule.getRuleId() + "]");
                }
                if (rule.getExpression() == null || rule.getExpression().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "expression is required in JSON rule [" + rule.getRuleId() + "]");
                }
                if (rule.getSeverity() == null || rule.getSeverity().trim().isEmpty()) {
                    throw new IllegalArgumentException("severity is required in JSON rule [" + rule.getRuleId() + "]");
                }
                if (rule.getErrorPath() == null || rule.getErrorPath().trim().isEmpty()) {
                    throw new IllegalArgumentException("errorPath is required in JSON rule [" + rule.getRuleId() + "]");
                }
            }

            return Collections.unmodifiableList(rules);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to load semantic rules from JSON: " + e.getMessage(), e);
        }
    }
}
