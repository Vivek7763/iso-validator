package com.prowidesoftware.swift.model.mx.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fss.iso20022.api.ErrorCategory;
import com.fss.iso20022.api.ResponseStatus;
import com.fss.iso20022.api.ValidationResponse;
import com.prowidesoftware.swift.model.mx.validation.builder.ValidationResponseBuilder;
import org.junit.jupiter.api.Test;

/**
 * Audit tests verifying the four schema/model coverage gaps (Audit Item 6).
 *
 * Verifies that each response accurately communicates whether:
 * 1. XSD validation completed (PASS vs SCHEMA_NOT_FOUND)
 * 2. Typed model parsing completed (SUCCESS vs MODEL_UNAVAILABLE)
 * 3. Semantic validation completed (executed vs skipped)
 */
public class ModelCoverageGapAuditTest {

    // ─── GAP 1: SCHEMA PRESENT, MODEL ABSENT (pacs.008.001.14) ──────────────

    @Test
    void testCoverageGap1_Pacs00800114_SchemaPresent_ModelAbsent() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.14\">\n"
                + "    <FIToFICstmrCdtTrf>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSG-PACS008-14-001</MsgId>\n"
                + "            <CreDtTm>2024-01-15T10:00:00</CreDtTm>\n"
                + "            <NbOfTxs>1</NbOfTxs>\n"
                + "            <SttlmInf>\n"
                + "                <SttlmMtd>INDA</SttlmMtd>\n"
                + "            </SttlmInf>\n"
                + "        </GrpHdr>\n"
                + "        <CdtTrfTxInf>\n"
                + "            <PmtId>\n"
                + "                <InstrId>INSTR-001</InstrId>\n"
                + "                <EndToEndId>E2E-001</EndToEndId>\n"
                + "                <TxId>TXN-001</TxId>\n"
                + "            </PmtId>\n"
                + "            <IntrBkSttlmAmt Ccy=\"USD\">10000.00</IntrBkSttlmAmt>\n"
                + "            <ChrgBr>SHAR</ChrgBr>\n"
                + "            <Dbtr><Nm>John Doe</Nm></Dbtr>\n"
                + "            <DbtrAgt><FinInstnId><BICFI>BOFAUS3NXXX</BICFI></FinInstnId></DbtrAgt>\n"
                + "            <CdtrAgt><FinInstnId><BICFI>CHASUS33XXX</BICFI></FinInstnId></CdtrAgt>\n"
                + "            <Cdtr><Nm>Jane Smith</Nm></Cdtr>\n"
                + "        </CdtTrfTxInf>\n"
                + "    </FIToFICstmrCdtTrf>\n"
                + "</Document>";

        ISOParserResult parseResult = ISOParser.parse(xml);

        // 1. XSD validation: Completed and Passed
        assertEquals(ISOParserResult.SchemaValidationStatus.PASS, parseResult.getSchemaStatus());
        assertTrue(parseResult.isSchemaValid(), "XSD validation completed and passed");

        // 2. Typed model parsing: Detected as missing in runtime
        assertEquals(ISOParserResult.ModelParsingDetail.MODEL_UNAVAILABLE, parseResult.getModelDetail());
        assertEquals(
                ISOParserResult.ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR,
                parseResult.getModelStatus());
        assertNull(parseResult.getParsedModel(), "Typed model must be null");

        // 3. Semantic validation: Skipped because model was absent
        assertNull(parseResult.getSemanticResult(), "Semantic validation must not execute when model is absent");

        // 4. Response builder: Communicates SYSTEM_ERROR with MODEL_UNAVAILABLE
        ValidationResponse response = ValidationResponseBuilder.build(parseResult);
        assertEquals(ResponseStatus.SYSTEM_ERROR, response.getStatus());
        assertEquals("pacs.008.001.14", response.getMessageType());
        assertEquals("MSG-PACS008-14-001", response.getBusinessMessageId());

        assertThat(response.getErrors().get(0).getCategory()).isEqualTo(ErrorCategory.TECHNICAL);
        assertThat(response.getErrors().get(0).getCode()).isEqualTo("MODEL_UNAVAILABLE");
    }

    // ─── GAP 2: SCHEMA PRESENT, MODEL ABSENT (acmt.015.001.05) ──────────────

    @Test
    void testCoverageGap2_Acmt01500105_SchemaPresent_ModelAbsent() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.015.001.05\">\n"
                + "    <AcctExcldMndtMntncReq>\n"
                + "        <Refs>\n"
                + "            <MsgId>\n"
                + "                <Id>ID123</Id>\n"
                + "                <CreDtTm>2023-10-06T09:00:00Z</CreDtTm>\n"
                + "            </MsgId>\n"
                + "            <PrcId>\n"
                + "                <Id>PRC123</Id>\n"
                + "                <CreDtTm>2023-10-06T09:00:00Z</CreDtTm>\n"
                + "            </PrcId>\n"
                + "        </Refs>\n"
                + "        <Acct>\n"
                + "            <Id><Othr><Id>ACCT123</Id></Othr></Id>\n"
                + "            <Ccy>USD</Ccy>\n"
                + "        </Acct>\n"
                + "        <AcctSvcrId><FinInstnId><BICFI>BBBBUS33XXX</BICFI></FinInstnId></AcctSvcrId>\n"
                + "    </AcctExcldMndtMntncReq>\n"
                + "</Document>";

        ISOParserResult parseResult = ISOParser.parse(xml);

        // 1. XSD validation: Completed and Passed
        assertEquals(ISOParserResult.SchemaValidationStatus.PASS, parseResult.getSchemaStatus());
        assertTrue(parseResult.isSchemaValid());

        // 2. Typed model parsing: Model unavailable
        assertEquals(ISOParserResult.ModelParsingDetail.MODEL_UNAVAILABLE, parseResult.getModelDetail());
        assertNull(parseResult.getParsedModel());

        // 3. Semantic validation: Skipped
        assertNull(parseResult.getSemanticResult());

        // 4. Response builder: Communicates SYSTEM_ERROR with MODEL_UNAVAILABLE
        ValidationResponse response = ValidationResponseBuilder.build(parseResult);
        assertEquals(ResponseStatus.SYSTEM_ERROR, response.getStatus());
        assertThat(response.getErrors().get(0).getCode()).isEqualTo("MODEL_UNAVAILABLE");
    }

    // ─── GAP 3: SCHEMA ABSENT, MODEL PRESENT (acmt.001.001.08) ──────────────

    @Test
    void testCoverageGap3_Acmt00100108_SchemaAbsent_ModelPresent() {
        String xml = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.001.001.08\">"
                + "<AcctOpngInstr><Refs><MsgId><Id>MSG-ACMT001-08</Id></MsgId>"
                + "<PrcgId><Id>PRC-01</Id></PrcgId></Refs>"
                + "<Acct><Id><Othr><Id>ACC-01</Id></Othr></Id></Acct></AcctOpngInstr></Document>";

        ISOParserResult parseResult = ISOParser.parse(xml);

        // 1. XSD validation: Did NOT complete (Schema not found)
        assertEquals(ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND, parseResult.getSchemaStatus());
        assertFalse(parseResult.isSchemaValid(), "isSchemaValid() must be false when schema is absent");

        // 2. Typed model parsing: Prowide model MxAcmt00100108 exists and parsed successfully
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, parseResult.getModelStatus());
        assertNotNull(parseResult.getParsedModel(), "Prowide JAXB model MxAcmt00100108 parsed successfully");

        // 3. Response builder: Status is SCHEMA_UNAVAILABLE, NEVER VALID
        ValidationResponse response = ValidationResponseBuilder.build(parseResult);
        assertNotEquals(ResponseStatus.VALID, response.getStatus(), "Missing schema must never produce VALID");
        assertEquals(ResponseStatus.SCHEMA_UNAVAILABLE, response.getStatus());
        assertThat(response.getErrors().get(0).getCode()).isEqualTo("SCHEMA_NOT_FOUND");
    }

    // ─── GAP 4: BOTH SCHEMA AND MODEL ABSENT (pacs.002.001.16) ──────────────

    @Test
    void testCoverageGap4_Pacs00200116_BothSchemaAndModelAbsent() {
        String xml = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.16\">"
                + "<FIToFIPmtStsRpt><GrpHdr><MsgId>MSG-PACS002-16</MsgId></GrpHdr></FIToFIPmtStsRpt></Document>";

        ISOParserResult parseResult = ISOParser.parse(xml);

        // 1. XSD validation: Schema absent
        assertEquals(ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND, parseResult.getSchemaStatus());
        assertFalse(parseResult.isSchemaValid());

        // 2. Typed model parsing: Model absent
        assertEquals(ISOParserResult.ModelParsingDetail.MODEL_UNAVAILABLE, parseResult.getModelDetail());
        assertNull(parseResult.getParsedModel());

        // 3. Response builder: Communicates SCHEMA_UNAVAILABLE
        ValidationResponse response = ValidationResponseBuilder.build(parseResult);
        assertNotEquals(ResponseStatus.VALID, response.getStatus());
        assertEquals(ResponseStatus.SCHEMA_UNAVAILABLE, response.getStatus());
        assertThat(response.getErrors().get(0).getCode()).isEqualTo("SCHEMA_NOT_FOUND");
    }
}
