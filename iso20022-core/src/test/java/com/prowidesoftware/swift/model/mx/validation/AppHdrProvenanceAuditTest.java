package com.prowidesoftware.swift.model.mx.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fss.iso20022.api.ErrorCategory;
import com.fss.iso20022.api.ResponseStatus;
import com.fss.iso20022.api.ValidationResponse;
import com.prowidesoftware.swift.model.mx.validation.builder.ValidationResponseBuilder;
import org.junit.jupiter.api.Test;

/**
 * Audit tests verifying resolution of the AppHdr schema provenance issue (Audit Item 5).
 *
 * head.001.001.01.xsd is an explicit test fixture with header comment:
 * "TEST FIXTURE - NOT AN OFFICIAL ISO SCHEMA" and synthetic type "AppHdr_Test".
 *
 * It must be explicitly classified as SchemaProvenance.TEST_FIXTURE_UNVERIFIED,
 * and its validation must emit a structural WARNING so that test fixtures cannot
 * be mistaken for authoritative production schemas.
 */
public class AppHdrProvenanceAuditTest {

    @Test
    void testProvenanceClassification() {
        assertEquals(
                SchemaRegistry.SchemaProvenance.TEST_FIXTURE_UNVERIFIED,
                SchemaRegistry.getProvenance("head.001.001.01"),
                "head.001.001.01 must be classified as TEST_FIXTURE_UNVERIFIED");
        assertEquals(
                SchemaRegistry.SchemaProvenance.TEST_FIXTURE_UNVERIFIED,
                SchemaRegistry.getProvenance("urn:iso:std:iso:20022:tech:xsd:head.001.001.01"),
                "Full namespace must also map to TEST_FIXTURE_UNVERIFIED");

        assertEquals(
                SchemaRegistry.SchemaProvenance.VERIFIED_OFFICIAL_ISO,
                SchemaRegistry.getProvenance("pacs.002.001.12"),
                "Production message schemas must be classified as VERIFIED_OFFICIAL_ISO");
        assertEquals(
                SchemaRegistry.SchemaProvenance.VERIFIED_OFFICIAL_ISO,
                SchemaRegistry.getProvenance("pain.001.001.11"),
                "pain.001.001.11 must be classified as VERIFIED_OFFICIAL_ISO");
    }

    @Test
    void testAppHdrValidation_EmitsProvenanceWarning_AndDoesNotGiveAuthoritativeBlessing() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Message xmlns=\"urn:iso:std:iso:20022:tech:xsd:head.001.001.01\">\n"
                + "    <AppHdr>\n"
                + "        <Fr><FIId><FinInstnId><BICFI>FOOBUS33XXX</BICFI></FinInstnId></FIId></Fr>\n"
                + "        <To><FIId><FinInstnId><BICFI>BARBUS33XXX</BICFI></FinInstnId></FIId></To>\n"
                + "        <BizMsgIdr>MSG-BAH-001</BizMsgIdr>\n"
                + "        <MsgDefIdr>pacs.002.001.12</MsgDefIdr>\n"
                + "        <CreDt>2023-10-01T12:00:00Z</CreDt>\n"
                + "    </AppHdr>\n"
                + "    <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "        <FIToFIPmtStsRpt>\n"
                + "            <GrpHdr>\n"
                + "                <MsgId>MSG-BAH-001</MsgId>\n"
                + "                <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "            </GrpHdr>\n"
                + "            <TxInfAndSts>\n"
                + "                <OrgnlInstrId>INSTR123</OrgnlInstrId>\n"
                + "                <TxSts>ACCP</TxSts>\n"
                + "            </TxInfAndSts>\n"
                + "        </FIToFIPmtStsRpt>\n"
                + "    </Document>\n"
                + "</Message>";

        ISOMessageIdentifier docId =
                ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12");
        ISOMessageIdentifier appHdrId =
                ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:head.001.001.01");

        ValidationResult result = ISOValidator.validate(xml, docId, appHdrId);
        assertNotNull(result);
        assertTrue(result.isValid(), "Message structure itself is valid");

        // Must contain provenance warning
        assertThat(result.getErrors()).anySatisfy(err -> {
            assertThat(err.getSeverity()).isEqualTo("WARNING");
            assertThat(err.getMessage()).contains("unverified test fixture schema");
        });

        // Test through ISOParser & ValidationResponseBuilder
        ISOParserResult parseResult = ISOParser.parse(xml);
        MessageStructureInfo info = MessageStructureInfo.parse(xml);
        assertNotNull(info.getAppHdrIdentifier());
        assertEquals("head.001.001.01", info.getAppHdrIdentifier().getFullMessageType());
        assertTrue(parseResult.isSchemaValid());

        ValidationResponse response = ValidationResponseBuilder.build(parseResult);
        assertEquals(ResponseStatus.VALID, response.getStatus());
        assertFalse(response.getErrors().isEmpty());

        assertThat(response.getErrors()).anySatisfy(err -> {
            assertThat(err.getCategory()).isEqualTo(ErrorCategory.STRUCTURAL);
            assertThat(err.getCode()).isEqualTo("XSD_WARNING");
            assertThat(err.getSeverity()).isEqualTo("WARNING");
            assertThat(err.getMessage()).contains("unverified test fixture schema");
        });
    }
}
