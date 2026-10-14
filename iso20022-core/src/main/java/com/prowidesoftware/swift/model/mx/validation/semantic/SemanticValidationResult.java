package com.prowidesoftware.swift.model.mx.validation.semantic;

import java.util.ArrayList;
import java.util.List;

public class SemanticValidationResult {
    private final List<SemanticViolation> violations = new ArrayList<>();
    private final List<TechnicalEvaluationError> technicalErrors = new ArrayList<>();
    private int evaluatedRuleCount = 0;

    public void incrementEvaluatedRuleCount() {
        this.evaluatedRuleCount++;
    }

    public int getEvaluatedRuleCount() {
        return evaluatedRuleCount;
    }

    public void addViolation(SemanticViolation violation) {
        this.violations.add(violation);
    }

    public void addTechnicalError(TechnicalEvaluationError error) {
        this.technicalErrors.add(error);
    }

    public boolean hasErrors() {
        for (SemanticViolation v : violations) {
            String sev = v.getSeverity();
            if (sev != null && ("ERROR".equalsIgnoreCase(sev) || "FATAL".equalsIgnoreCase(sev))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasWarnings() {
        for (SemanticViolation v : violations) {
            String sev = v.getSeverity();
            if (sev != null
                    && ("WARNING".equalsIgnoreCase(sev)
                            || "WARN".equalsIgnoreCase(sev)
                            || "INFO".equalsIgnoreCase(sev))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTechnicalErrors() {
        return !technicalErrors.isEmpty();
    }

    public boolean hasFailures() {
        return hasErrors() || hasTechnicalErrors();
    }

    public List<SemanticViolation> getViolations() {
        return java.util.Collections.unmodifiableList(violations);
    }

    public List<TechnicalEvaluationError> getTechnicalErrors() {
        return java.util.Collections.unmodifiableList(technicalErrors);
    }
}
