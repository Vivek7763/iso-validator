package com.prowidesoftware.swift.model.mx.validation.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fss.iso20022.api.ErrorCategory;
import com.fss.iso20022.api.ResponseStatus;
import com.fss.iso20022.api.ValidationResponse;
import com.prowidesoftware.swift.model.mx.MxPacs00200112;
import com.prowidesoftware.swift.model.mx.MxPacs00800107;
import com.prowidesoftware.swift.model.mx.MxPain00100111;
import com.prowidesoftware.swift.model.mx.dic.*;
import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.builder.ValidationResponseBuilder;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Individual audit of all 5 semantic rules:
 * 1. CBPR_PACS002_01 (pacs.002.001.12) - Status Reason Info mandatory when RJCT
 * 2. VR-002 (pacs.002.001.12) - Original Instruction ID mandatory
 * 3. VR-003 (pacs.002.001.12) - Group Header MsgId mandatory
 * 4. VR-004 (pacs.008.001.07) - Settlement Method INDA (Warning)
 * 5. VR-005 (pain.001.001.11) - Group Header NbOfTxs mandatory
 */
public class SemanticRulesIndividualAuditTest {

    private SemanticRuleEngine engine;
    private List<SemanticRuleDefinition> catalogRules;

    @BeforeEach
    void setUp() {
        engine = new SemanticRuleEngine(new SpELRuleEvaluator());
        try (InputStream is = getClass().getResourceAsStream("/semantic/rules.json")) {
            assertNotNull(is, "Production /semantic/rules.json must exist");
            catalogRules = SemanticRuleLoader.loadJsonRules(is);
            for (SemanticRuleDefinition rule : catalogRules) {
                engine.addRule(rule);
            }
        } catch (Exception e) {
            fail("Failed to load /semantic/rules.json: " + e.getMessage());
        }
    }

    // ─── RULE 1: CBPR_PACS002_01 ─────────────────────────────────────────────

    @Test
    void auditRule1_CBPR_PACS002_01_PassingCase() {
        MxPacs00200112 model = new MxPacs00200112();
        FIToFIPaymentStatusReportV12 rpt = new FIToFIPaymentStatusReportV12();
        PaymentTransaction130 tx = new PaymentTransaction130();
        tx.setOrgnlInstrId("INSTR-001");
        tx.setTxSts("RJCT");
        StatusReasonInformation12 rsn = new StatusReasonInformation12();
        rsn.setRsn(new StatusReason6Choice().setCd("AG01"));
        tx.addStsRsnInf(rsn);
        rpt.addTxInfAndSts(tx);
        rpt.setGrpHdr(new GroupHeader101().setMsgId("MSG-001"));
        model.setFIToFIPmtStsRpt(rpt);

        SemanticValidationResult result = engine.validate(model);
        assertFalse(result.getViolations().stream().anyMatch(v -> "CBPR_PACS002_01".equals(v.getRuleId())));
    }

    @Test
    void auditRule1_CBPR_PACS002_01_FailingCase_YieldsFatalRejection() {
        MxPacs00200112 model = new MxPacs00200112();
        FIToFIPaymentStatusReportV12 rpt = new FIToFIPaymentStatusReportV12();
        PaymentTransaction130 tx = new PaymentTransaction130();
        tx.setOrgnlInstrId("INSTR-001");
        tx.setTxSts("RJCT"); // missing StsRsnInf
        rpt.addTxInfAndSts(tx);
        rpt.setGrpHdr(new GroupHeader101().setMsgId("MSG-001"));
        model.setFIToFIPmtStsRpt(rpt);

        SemanticValidationResult result = engine.validate(model);
        assertTrue(result.hasFailures());

        SemanticViolation v = result.getViolations().stream()
                .filter(vi -> "CBPR_PACS002_01".equals(vi.getRuleId()))
                .findFirst()
                .orElseThrow();
        assertEquals("FATAL", v.getSeverity());
        assertEquals("FIToFIPmtStsRpt/TxInfAndSts/StsRsnInf", v.getErrorPath());
    }

    // ─── RULE 2: VR-002 ──────────────────────────────────────────────────────

    @Test
    void auditRule2_VR002_PassingCase() {
        MxPacs00200112 model = new MxPacs00200112();
        FIToFIPaymentStatusReportV12 rpt = new FIToFIPaymentStatusReportV12();
        PaymentTransaction130 tx = new PaymentTransaction130();
        tx.setOrgnlInstrId("INSTR-002");
        tx.setTxSts("ACCP");
        rpt.addTxInfAndSts(tx);
        rpt.setGrpHdr(new GroupHeader101().setMsgId("MSG-002"));
        model.setFIToFIPmtStsRpt(rpt);

        SemanticValidationResult result = engine.validate(model);
        assertFalse(result.getViolations().stream().anyMatch(v -> "VR-002".equals(v.getRuleId())));
    }

    @Test
    void auditRule2_VR002_FailingCase_YieldsFatalRejection() {
        MxPacs00200112 model = new MxPacs00200112();
        FIToFIPaymentStatusReportV12 rpt = new FIToFIPaymentStatusReportV12();
        PaymentTransaction130 tx = new PaymentTransaction130();
        tx.setOrgnlInstrId(null); // Missing mandatory OrgnlInstrId
        tx.setTxSts("ACCP");
        rpt.addTxInfAndSts(tx);
        rpt.setGrpHdr(new GroupHeader101().setMsgId("MSG-002"));
        model.setFIToFIPmtStsRpt(rpt);

        SemanticValidationResult result = engine.validate(model);
        assertTrue(result.hasFailures());

        SemanticViolation v = result.getViolations().stream()
                .filter(vi -> "VR-002".equals(vi.getRuleId()))
                .findFirst()
                .orElseThrow();
        assertEquals("FATAL", v.getSeverity());
        assertEquals("FIToFIPmtStsRpt/TxInfAndSts/OrgnlInstrId", v.getErrorPath());
    }

    // ─── RULE 3: VR-003 ──────────────────────────────────────────────────────

    @Test
    void auditRule3_VR003_PassingCase() {
        MxPacs00200112 model = new MxPacs00200112();
        FIToFIPaymentStatusReportV12 rpt = new FIToFIPaymentStatusReportV12();
        rpt.setGrpHdr(new GroupHeader101().setMsgId("VALID-MSG-003"));
        model.setFIToFIPmtStsRpt(rpt);

        SemanticValidationResult result = engine.validate(model);
        assertFalse(result.getViolations().stream().anyMatch(v -> "VR-003".equals(v.getRuleId())));
    }

    @Test
    void auditRule3_VR003_FailingCase_YieldsFatalRejection() {
        MxPacs00200112 model = new MxPacs00200112();
        FIToFIPaymentStatusReportV12 rpt = new FIToFIPaymentStatusReportV12();
        rpt.setGrpHdr(new GroupHeader101().setMsgId("   ")); // Blank MsgId
        model.setFIToFIPmtStsRpt(rpt);

        SemanticValidationResult result = engine.validate(model);
        assertTrue(result.hasFailures());

        SemanticViolation v = result.getViolations().stream()
                .filter(vi -> "VR-003".equals(vi.getRuleId()))
                .findFirst()
                .orElseThrow();
        assertEquals("FATAL", v.getSeverity());
        assertEquals("FIToFIPmtStsRpt/GrpHdr/MsgId", v.getErrorPath());
    }

    // ─── RULE 4: VR-004 (pacs.008.001.07 with safe .value() enum call) ────────

    @Test
    void auditRule4_VR004_PassingCase_SafeEnumEvaluation() {
        MxPacs00800107 model = new MxPacs00800107();
        FIToFICustomerCreditTransferV07 trf = new FIToFICustomerCreditTransferV07();
        GroupHeader70 grpHdr = new GroupHeader70();
        SettlementInstruction4 sttlm = new SettlementInstruction4();
        sttlm.setSttlmMtd(SettlementMethod1Code.INDA); // Passes rule
        grpHdr.setSttlmInf(sttlm);
        trf.setGrpHdr(grpHdr);
        model.setFIToFICstmrCdtTrf(trf);

        SemanticValidationResult result = engine.validate(model);
        assertFalse(result.hasFailures());
        assertTrue(result.getTechnicalErrors().isEmpty(), "Safe enum method resolver must not trigger technical error");
        assertFalse(result.getViolations().stream().anyMatch(v -> "VR-004".equals(v.getRuleId())));
    }

    @Test
    void auditRule4_VR004_FailingCase_YieldsWarningAndRemainsValid() {
        MxPacs00800107 model = new MxPacs00800107();
        FIToFICustomerCreditTransferV07 trf = new FIToFICustomerCreditTransferV07();
        GroupHeader70 grpHdr = new GroupHeader70();
        SettlementInstruction4 sttlm = new SettlementInstruction4();
        sttlm.setSttlmMtd(SettlementMethod1Code.CLRG); // Violates warning rule
        grpHdr.setSttlmInf(sttlm);
        trf.setGrpHdr(grpHdr);
        model.setFIToFICstmrCdtTrf(trf);

        SemanticValidationResult result = engine.validate(model);
        assertFalse(result.hasErrors(), "WARNING must not be classified as error");
        assertTrue(result.hasWarnings(), "Must flag warning");
        assertFalse(result.hasFailures(), "hasFailures() must be false for warnings");

        SemanticViolation v = result.getViolations().stream()
                .filter(vi -> "VR-004".equals(vi.getRuleId()))
                .findFirst()
                .orElseThrow();
        assertEquals("WARNING", v.getSeverity());
        assertEquals("FIToFICstmrCdtTrf/GrpHdr/SttlmInf/SttlmMtd", v.getErrorPath());

        // Verify aggregation in ValidationResponseBuilder
        ISOParserResult parserResult = new ISOParserResult.Builder()
                .identifier(com.prowidesoftware.swift.model.mx.validation.ISOMessageIdentifier.fromNamespace(
                        "urn:iso:std:iso:20022:tech:xsd:pacs.008.001.07"))
                .schemaStatus(ISOParserResult.SchemaValidationStatus.PASS)
                .modelStatus(ISOParserResult.ModelParsingStatus.SUCCESS)
                .parsedModel(model)
                .semanticResult(result)
                .build();

        ValidationResponse response = ValidationResponseBuilder.build(parserResult);
        assertEquals(ResponseStatus.VALID, response.getStatus(), "Warning violation MUST NOT cause rejection");
        assertFalse(response.getErrors().isEmpty());
        assertEquals("WARNING", response.getErrors().get(0).getSeverity());
    }

    // ─── RULE 5: VR-005 (pain.001.001.11) ────────────────────────────────────

    @Test
    void auditRule5_VR005_PassingCase() {
        MxPain00100111 model = new MxPain00100111();
        CustomerCreditTransferInitiationV11 initn = new CustomerCreditTransferInitiationV11();
        GroupHeader95 grpHdr = new GroupHeader95();
        grpHdr.setNbOfTxs("5");
        initn.setGrpHdr(grpHdr);
        model.setCstmrCdtTrfInitn(initn);

        SemanticValidationResult result = engine.validate(model);
        assertFalse(result.getViolations().stream().anyMatch(v -> "VR-005".equals(v.getRuleId())));
    }

    @Test
    void auditRule5_VR005_FailingCase_YieldsFatalRejection() {
        MxPain00100111 model = new MxPain00100111();
        CustomerCreditTransferInitiationV11 initn = new CustomerCreditTransferInitiationV11();
        GroupHeader95 grpHdr = new GroupHeader95();
        grpHdr.setNbOfTxs(null); // Missing NbOfTxs
        initn.setGrpHdr(grpHdr);
        model.setCstmrCdtTrfInitn(initn);

        SemanticValidationResult result = engine.validate(model);
        assertTrue(result.hasFailures());

        SemanticViolation v = result.getViolations().stream()
                .filter(vi -> "VR-005".equals(vi.getRuleId()))
                .findFirst()
                .orElseThrow();
        assertEquals("FATAL", v.getSeverity());
        assertEquals("CstmrCdtTrfInitn/GrpHdr/NbOfTxs", v.getErrorPath());
    }

    // ─── RULE APPLICABILITY & VERSION ISOLATION ──────────────────────────────

    @Test
    void auditRuleApplicability_Pacs002RulesDoNotExecuteOnPain001() {
        MxPain00100111 model = new MxPain00100111();
        CustomerCreditTransferInitiationV11 initn = new CustomerCreditTransferInitiationV11();
        GroupHeader95 grpHdr = new GroupHeader95();
        grpHdr.setNbOfTxs("10");
        initn.setGrpHdr(grpHdr);
        model.setCstmrCdtTrfInitn(initn);

        SemanticValidationResult result = engine.validate(model);
        // Only VR-005 (pain.001.001.11) applies; none of the pacs.002 or pacs.008 rules should run
        assertEquals(1, result.getEvaluatedRuleCount());
        assertFalse(result.hasFailures());
    }

    // ─── DEFAULT PRODUCTION INBOUND PATH VERIFICATION ────────────────────────

    @Test
    void auditDefaultInboundPipeline_ExecutesRulesWithoutManualEngineInjection() {
        // XML with pacs.002.001.12 with TxSts=RJCT but missing StsRsnInf
        String failingXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSG-AUTO-01</MsgId>\n"
                + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "        </GrpHdr>\n"
                + "        <TxInfAndSts>\n"
                + "            <OrgnlInstrId>INSTR-01</OrgnlInstrId>\n"
                + "            <TxSts>RJCT</TxSts>\n"
                + "        </TxInfAndSts>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        // Run through standard production entry point ISOParser.parse(xml) - NO engine argument passed!
        ISOParserResult parseResult = ISOParser.parse(failingXml);

        assertNotNull(parseResult.getSemanticResult(), "Default inbound path MUST populate semanticResult");
        assertTrue(parseResult.getSemanticResult().hasFailures(), "CBPR_PACS002_01 must fail automatically");
        assertTrue(parseResult.getSemanticResult().getViolations().stream()
                .anyMatch(v -> "CBPR_PACS002_01".equals(v.getRuleId())));

        ValidationResponse response = ValidationResponseBuilder.build(parseResult);
        assertEquals(ResponseStatus.REJECTED, response.getStatus());
        assertThat(response.getErrors().get(0).getCategory()).isEqualTo(ErrorCategory.SEMANTIC);
        assertThat(response.getErrors().get(0).getCode()).isEqualTo("CBPR_PACS002_01");
    }
}
