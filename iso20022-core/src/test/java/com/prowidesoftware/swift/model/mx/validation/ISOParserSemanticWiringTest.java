package com.prowidesoftware.swift.model.mx.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fss.iso20022.api.ResponseStatus;
import com.fss.iso20022.api.ValidationResponse;
import com.prowidesoftware.swift.model.mx.validation.builder.ValidationResponseBuilder;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticRuleDefinition;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ISOParserSemanticWiringTest {

    private SemanticRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SemanticRuleEngine(new com.prowidesoftware.swift.model.mx.validation.semantic.SpELRuleEvaluator());
    }

    private static String validPacs002Xml(String msgId, String txSts) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>" + msgId + "</MsgId>\n"
                + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "        </GrpHdr>\n"
                + "        <TxInfAndSts>\n"
                + "            <OrgnlInstrId>INSTR123</OrgnlInstrId>\n"
                + "            <TxSts>" + txSts + "</TxSts>\n"
                + "        </TxInfAndSts>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";
    }

    @Test
    void testInboundPipeline_PassingSemanticRule_YieldsValidResponse() {
        SemanticRuleDefinition passRule = new SemanticRuleDefinition();
        passRule.setRuleId("RULE-PASS-01");
        passRule.setMessageType("pacs.002.001.12");
        passRule.setSeverity("FATAL");
        passRule.setErrorPath("GrpHdr/MsgId");
        passRule.setExpression("FIToFIPmtStsRpt?.grpHdr?.msgId != null");
        passRule.setMessage("MsgId must be present");
        engine.addRule(passRule);

        String xml = validPacs002Xml("BIZ-MSG-999", "ACCP");
        ISOParserResult parseResult = ISOParser.parse(xml, engine);

        assertNotNull(parseResult);
        assertEquals(ISOParserResult.SchemaValidationStatus.PASS, parseResult.getSchemaStatus());
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, parseResult.getModelStatus());
        assertNotNull(parseResult.getSemanticResult());
        assertFalse(parseResult.getSemanticResult().hasFailures());

        ValidationResponse response = ValidationResponseBuilder.build(parseResult);
        assertEquals(ResponseStatus.VALID, response.getStatus());
        assertEquals("pacs.002.001.12", response.getMessageType());
        assertEquals("BIZ-MSG-999", response.getBusinessMessageId());
        assertTrue(response.getErrors().isEmpty());
    }

    @Test
    void testInboundPipeline_FailingSemanticRule_YieldsRejectedResponse() {
        SemanticRuleDefinition failRule = new SemanticRuleDefinition();
        failRule.setRuleId("RULE-FAIL-01");
        failRule.setMessageType("pacs.002.001.12");
        failRule.setSeverity("FATAL");
        failRule.setErrorPath("GrpHdr/MsgId");
        failRule.setExpression("FIToFIPmtStsRpt?.grpHdr?.msgId == 'EXPECTED-DIFFERENT-ID'");
        failRule.setMessage("MsgId must match expected ID");
        engine.addRule(failRule);

        String xml = validPacs002Xml("BIZ-MSG-888", "ACCP");
        ISOParserResult parseResult = ISOParser.parse(xml, engine);

        assertNotNull(parseResult);
        assertEquals(ISOParserResult.SchemaValidationStatus.PASS, parseResult.getSchemaStatus());
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, parseResult.getModelStatus());
        assertNotNull(parseResult.getSemanticResult());
        assertTrue(parseResult.getSemanticResult().hasFailures());

        ValidationResponse response = ValidationResponseBuilder.build(parseResult);
        assertEquals(ResponseStatus.REJECTED, response.getStatus());
        assertEquals("pacs.002.001.12", response.getMessageType());
        assertEquals("BIZ-MSG-888", response.getBusinessMessageId());
        assertFalse(response.getErrors().isEmpty());
        assertThat(response.getErrors().get(0).getRuleId()).isEqualTo("RULE-FAIL-01");
        assertThat(response.getErrors().get(0).getMessage()).contains("MsgId must match expected ID");
    }

    @Test
    void testInboundPipeline_SemanticTechnicalError_YieldsSystemError() {
        SemanticRuleDefinition badRule = new SemanticRuleDefinition();
        badRule.setRuleId("RULE-BAD-SYNTAX");
        badRule.setMessageType("pacs.002.001.12");
        badRule.setSeverity("FATAL");
        badRule.setErrorPath("GrpHdr");
        badRule.setExpression("unparseable (((( syntax");
        badRule.setMessage("Bad rule");
        engine.addRule(badRule);

        String xml = validPacs002Xml("BIZ-MSG-666", "ACCP");
        ISOParserResult parseResult = ISOParser.parse(xml, engine);

        assertNotNull(parseResult);
        assertEquals(ISOParserResult.SchemaValidationStatus.PASS, parseResult.getSchemaStatus());
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, parseResult.getModelStatus());
        assertNotNull(parseResult.getSemanticResult());
        assertTrue(parseResult.getSemanticResult().hasTechnicalErrors());

        ValidationResponse response = ValidationResponseBuilder.build(parseResult);
        assertEquals(ResponseStatus.SYSTEM_ERROR, response.getStatus());
    }

    @Test
    void testInboundPipeline_WarningSemanticRule_RemainsValid() {
        SemanticRuleDefinition warnRule = new SemanticRuleDefinition();
        warnRule.setRuleId("RULE-WARN-01");
        warnRule.setMessageType("pacs.002.001.12");
        warnRule.setSeverity("WARNING");
        warnRule.setErrorPath("GrpHdr/MsgId");
        warnRule.setExpression("FIToFIPmtStsRpt?.grpHdr?.msgId == 'SPECIAL'");
        warnRule.setMessage("MsgId should ideally be SPECIAL");
        engine.addRule(warnRule);

        String xml = validPacs002Xml("BIZ-MSG-777", "ACCP");
        ISOParserResult parseResult = ISOParser.parse(xml, engine);

        assertNotNull(parseResult);
        assertEquals(ISOParserResult.SchemaValidationStatus.PASS, parseResult.getSchemaStatus());
        assertNotNull(parseResult.getSemanticResult());
        assertFalse(parseResult.getSemanticResult().hasFailures());
        assertTrue(parseResult.getSemanticResult().hasWarnings());

        ValidationResponse response = ValidationResponseBuilder.build(parseResult);
        assertEquals(ResponseStatus.VALID, response.getStatus());
        assertEquals("BIZ-MSG-777", response.getBusinessMessageId());
        assertFalse(response.getErrors().isEmpty());
        assertThat(response.getErrors().get(0).getSeverity()).isEqualTo("WARNING");
        assertThat(response.getErrors().get(0).getRuleId()).isEqualTo("RULE-WARN-01");
    }

    @Test
    void testInboundPipeline_StructuralFailureSkipsSemanticValidation() {
        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("RULE-SHOULD-NOT-RUN");
        rule.setMessageType("pacs.002.001.12");
        rule.setSeverity("FATAL");
        rule.setErrorPath("GrpHdr/MsgId");
        rule.setExpression("true");
        engine.addRule(rule);

        // Invalid XML (missing required elements)
        String invalidXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        ISOParserResult parseResult = ISOParser.parse(invalidXml, engine);
        assertEquals(ISOParserResult.SchemaValidationStatus.FAIL, parseResult.getSchemaStatus());
        assertEquals(ISOParserResult.ModelParsingStatus.SKIPPED, parseResult.getModelStatus());
        assertNull(parseResult.getSemanticResult());

        ValidationResponse response = ValidationResponseBuilder.build(parseResult);
        assertEquals(ResponseStatus.REJECTED, response.getStatus());
    }

    @Test
    void testDecoyElementSubtreeFilteringDoesNotHijackRealDocument() throws Exception {
        // XML containing an outer decoy Document element with a fake namespace
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root xmlns:decoy=\"urn:decoy:fake\">\n"
                + "    <decoy:Document><decoy:Fake/></decoy:Document>\n"
                + "    <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "        <FIToFIPmtStsRpt>\n"
                + "            <GrpHdr>\n"
                + "                <MsgId>MSG123</MsgId>\n"
                + "                <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "            </GrpHdr>\n"
                + "            <TxInfAndSts>\n"
                + "                <OrgnlInstrId>INSTR123</OrgnlInstrId>\n"
                + "                <TxSts>ACCP</TxSts>\n"
                + "            </TxInfAndSts>\n"
                + "        </FIToFIPmtStsRpt>\n"
                + "    </Document>\n"
                + "</root>";

        ISOMessageIdentifier docId =
                ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12");
        ValidationResult result = ISOValidator.validate(xml, docId, null);
        assertNotNull(result);
        assertTrue(result.isValid(), "Real Document should match while decoy Document is ignored");
    }
}
