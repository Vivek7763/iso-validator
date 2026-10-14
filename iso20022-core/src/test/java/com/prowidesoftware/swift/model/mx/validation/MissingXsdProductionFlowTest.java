package com.prowidesoftware.swift.model.mx.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fss.iso20022.api.ErrorCategory;
import com.fss.iso20022.api.ResponseStatus;
import com.fss.iso20022.api.ValidationResponse;
import com.prowidesoftware.swift.model.mx.validation.builder.ValidationResponseBuilder;
import org.junit.jupiter.api.Test;

/**
 * Proof that a missing XSD can NEVER produce VALID or full-validation success,
 * even when Prowide JAXB parses the XML into a typed model successfully.
 */
public class MissingXsdProductionFlowTest {

    @Test
    void testMissingXsd_EvenWithProwideParseSuccess_YieldsSchemaUnavailableNeverValid() {
        // camt.053.001.08: JAXB model MxCamt05300108 exists in runtime, but camt.053.001.08.xsd is deliberately NOT in
        // SchemaRegistry
        String xml = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.08\">"
                + "<BkToCstmrStmt><GrpHdr><MsgId>MSG-CAMT-001</MsgId>"
                + "<CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr></BkToCstmrStmt></Document>";

        // Step 1: Real production entry point
        ISOParserResult parseResult = ISOParser.parse(xml);

        // Verification of parser result state
        assertNotNull(parseResult);
        assertEquals(ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND, parseResult.getSchemaStatus());
        assertFalse(parseResult.isSchemaValid(), "isSchemaValid() must be false when schema is not found");
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, parseResult.getModelStatus());
        assertNotNull(parseResult.getParsedModel(), "Prowide model parsed successfully");

        // Step 2: Production response builder
        ValidationResponse response = ValidationResponseBuilder.build(parseResult);

        // Crucial verification: Status must be SCHEMA_UNAVAILABLE, NEVER VALID
        assertNotEquals(
                ResponseStatus.VALID, response.getStatus(), "Missing XSD must NEVER result in ResponseStatus.VALID");
        assertEquals(
                ResponseStatus.SCHEMA_UNAVAILABLE,
                response.getStatus(),
                "Status must be explicitly SCHEMA_UNAVAILABLE");

        // Verification of diagnostics
        assertEquals("camt.053.001.08", response.getMessageType());
        assertEquals("MSG-CAMT-001", response.getBusinessMessageId());
        assertFalse(response.getErrors().isEmpty());

        assertThat(response.getErrors().get(0).getCategory()).isEqualTo(ErrorCategory.TECHNICAL);
        assertThat(response.getErrors().get(0).getCode()).isEqualTo("SCHEMA_NOT_FOUND");
        assertThat(response.getErrors().get(0).getMessage())
                .contains("No XSD registered for message type: camt.053.001.08");
    }
}
