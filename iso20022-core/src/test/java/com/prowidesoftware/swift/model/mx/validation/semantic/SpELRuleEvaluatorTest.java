package com.prowidesoftware.swift.model.mx.validation.semantic;

import static org.junit.jupiter.api.Assertions.*;

import com.prowidesoftware.swift.model.mx.MxPacs00200112;
import org.junit.jupiter.api.Test;

class SpELRuleEvaluatorTest {

    private final SpELRuleEvaluator evaluator = new SpELRuleEvaluator();

    @Test
    void testExpressionEvaluationWithNullModelThrowsException() {
        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("TEST-01");
        rule.setExpression("FIToFIPmtStsRpt != null");

        // Evaluator throws when trying to evaluate on null root object
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(rule, null));
    }

    @Test
    void testExpressionCacheReusesParsedExpressions() {
        MxPacs00200112 model = new MxPacs00200112();
        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("CACHE-01");
        rule.setExpression("FIToFIPmtStsRpt == null");

        boolean outcome1 = evaluator.evaluate(rule, model);
        assertTrue(outcome1);

        boolean outcome2 = evaluator.evaluate(rule, model);
        assertTrue(outcome2);
    }

    @Test
    void testInvalidSpelSyntaxThrowsIllegalArgumentException() {
        MxPacs00200112 model = new MxPacs00200112();
        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("BAD-SYNTAX");
        rule.setExpression("invalid syntax (((( unclosed");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(rule, model));
        assertTrue(ex.getMessage().contains("SpEL Evaluation failed or blocked"));
    }

    @Test
    void testNullRuleOrExpressionThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null, null));

        SemanticRuleDefinition ruleWithoutExpr = new SemanticRuleDefinition();
        ruleWithoutExpr.setRuleId("NO-EXPR");
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(ruleWithoutExpr, null));
    }
}
