package com.prowidesoftware.swift.model.mx.validation.semantic;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.util.ArrayList;
import java.util.List;

public class SemanticRuleEngine {
    private final List<SemanticRuleDefinition> rules = new ArrayList<>();
    private final RuleEvaluator evaluator;

    public SemanticRuleEngine(RuleEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public void addRule(SemanticRuleDefinition rule) {
        if (rule == null) {
            throw new IllegalArgumentException("rule must not be null");
        }
        if (rule.getRuleId() == null || rule.getRuleId().trim().isEmpty())
            throw new IllegalArgumentException("ruleId is required");
        for (SemanticRuleDefinition existing : rules) {
            if (existing.getRuleId().equals(rule.getRuleId())) {
                throw new IllegalArgumentException("Duplicate rule ID: " + rule.getRuleId());
            }
        }
        if (rule.getMessageType() == null || rule.getMessageType().trim().isEmpty())
            throw new IllegalArgumentException("messageType is required");
        if (rule.getExpression() == null || rule.getExpression().trim().isEmpty())
            throw new IllegalArgumentException("expression is required");
        if (rule.getSeverity() == null || rule.getSeverity().trim().isEmpty())
            throw new IllegalArgumentException("severity is required");
        if (rule.getErrorPath() == null || rule.getErrorPath().trim().isEmpty())
            throw new IllegalArgumentException("errorPath is required");
        rules.add(rule);
    }

    public SemanticValidationResult validate(AbstractMX model) {
        return validate(model, null);
    }

    public SemanticValidationResult validate(AbstractMX model, RuleContext context) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        SemanticValidationResult result = new SemanticValidationResult();

        String modelProcess = model.getBusinessProcess();
        String modelVariant = String.format("%03d", model.getFunctionality()) + "."
                + String.format("%03d", model.getVariant()) + "."
                + String.format("%02d", model.getVersion());
        String modelId = modelProcess + "." + modelVariant;
        String canonicalVersion = String.format("%02d", model.getVersion());

        for (SemanticRuleDefinition rule : rules) {
            // Rule Applicability: exact match or dot-boundary prefix (e.g. "pacs.002" matches "pacs.002.001.12")
            if (rule.getMessageType() != null) {
                String targetType = rule.getMessageType().trim();
                boolean matches = modelId.equals(targetType) || modelId.startsWith(targetType + ".");
                if (!matches) {
                    continue;
                }
            }
            if (rule.getVersions() != null && !rule.getVersions().isEmpty()) {
                String vRaw = String.valueOf(model.getVersion());
                if (!rule.getVersions().contains(canonicalVersion)
                        && !rule.getVersions().contains(vRaw)) {
                    continue;
                }
            }
            if (rule.getProfiles() != null && !rule.getProfiles().isEmpty()) {
                if (context == null
                        || context.getActiveProfile() == null
                        || !rule.getProfiles().contains(context.getActiveProfile())) {
                    continue;
                }
            }

            try {
                result.incrementEvaluatedRuleCount();
                boolean passed = evaluator.evaluate(rule, model);
                if (!passed) {
                    result.addViolation(new SemanticViolation(
                            rule.getRuleId(), rule.getSeverity(), rule.getMessage(), rule.getErrorPath()));
                }
            } catch (Exception e) {
                result.addTechnicalError(
                        new TechnicalEvaluationError(rule.getRuleId(), "Rule Evaluation Error: " + e.getMessage()));
            }
        }
        return result;
    }
}
