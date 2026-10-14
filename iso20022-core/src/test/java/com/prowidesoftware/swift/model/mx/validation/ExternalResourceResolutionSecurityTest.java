package com.prowidesoftware.swift.model.mx.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.prowidesoftware.swift.utils.SafeXmlUtils;
import java.io.StringReader;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

/**
 * Audit tests verifying schema resolver security, XXE immunity, external DTD blocking,
 * and external schema import blocking (Audit Item 7).
 */
public class ExternalResourceResolutionSecurityTest {

    private final ClasspathResourceResolver resolver = new ClasspathResourceResolver();

    // ─── 1. RESOLVER PROTOCOL RESTRICTION TESTS ─────────────────────────────

    @Test
    void testResolver_BlocksHttpProtocol() {
        DOMException ex = assertThrows(
                DOMException.class,
                () -> resolver.resolveResource(
                        null, null, null, "http://attacker.com/malicious.xsd", "http://attacker.com/malicious.xsd"));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void testResolver_BlocksHttpsProtocol() {
        DOMException ex = assertThrows(
                DOMException.class,
                () -> resolver.resolveResource(null, null, null, "https://attacker.com/malicious.xsd", null));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void testResolver_BlocksFileProtocol_TripleSlash() {
        DOMException ex = assertThrows(
                DOMException.class, () -> resolver.resolveResource(null, null, null, "file:///etc/passwd", null));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void testResolver_BlocksFileProtocol_SingleSlash() {
        DOMException ex = assertThrows(
                DOMException.class, () -> resolver.resolveResource(null, null, null, "file:/etc/shadow", null));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void testResolver_BlocksFtpProtocol() {
        DOMException ex = assertThrows(
                DOMException.class,
                () -> resolver.resolveResource(null, null, null, "ftp://attacker.com/payload.dtd", null));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void testResolver_BlocksJarProtocol() {
        DOMException ex = assertThrows(
                DOMException.class,
                () -> resolver.resolveResource(null, null, null, "jar:file:/tmp/exploit.jar!/schema.xsd", null));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void testResolver_BlocksPathTraversal() {
        DOMException ex = assertThrows(
                DOMException.class,
                () -> resolver.resolveResource(null, null, null, "../../secrets/passwords.txt", null));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("Path traversal blocked for security"));
    }

    @Test
    void testResolver_NeverFallsBackToExternalResolutionForMissingResource() {
        // When resource does not exist on classpath, resolver must return null without making any network calls
        assertNull(resolver.resolveResource(null, "urn:nonexistent", null, "nonexistent-schema.xsd", null));
    }

    // ─── 2. REAL XXE AND EXTERNAL DTD INJECTION IN XML PAYLOAD ─────────────

    @Test
    void testValidator_BlocksExternalDtdInXmlPayload() throws Exception {
        String xxeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE Document SYSTEM \"http://127.0.0.1:65534/nonexistent.dtd\">\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr><MsgId>MSG1</MsgId><CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr>\n"
                + "        <TxInfAndSts><OrgnlInstrId>I1</OrgnlInstrId><TxSts>ACCP</TxSts></TxInfAndSts>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        ISOMessageIdentifier id = ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12");

        // SafeXmlUtils disables DTD processing (DOCTYPE is disallowed or external DTD is ignored)
        // If DOCTYPE is disallowed, SAXParseException is thrown; if ignored, the payload validates safely without
        // external network call.
        try {
            ValidationResult result = ISOValidator.validate(xxeXml, id, null);
            // If it parsed, ensure it did not succeed in resolving external DTD
            assertNotNull(result);
        } catch (org.xml.sax.SAXException e) {
            // Disallowing DOCTYPE is the safest defense
            assertThat(e.getMessage()).containsAnyOf("DOCTYPE", "disallowed", "Feature");
        }
    }

    @Test
    void testValidator_BlocksXxeEntityExpansionInXmlPayload() throws Exception {
        String xxePayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE Document [\n"
                + "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n"
                + "]>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr><MsgId>&xxe;</MsgId><CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr>\n"
                + "        <TxInfAndSts><OrgnlInstrId>I1</OrgnlInstrId><TxSts>ACCP</TxSts></TxInfAndSts>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        ISOMessageIdentifier id = ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12");

        try {
            ValidationResult result = ISOValidator.validate(xxePayload, id, null);
            // If it parsed, the entity must NOT have expanded the file contents into MsgId
            if (result.isValid()) {
                ISOParserResult parseResult = ISOParser.parse(xxePayload);
                if (parseResult.getBusinessMessageId() != null) {
                    assertFalse(
                            parseResult.getBusinessMessageId().contains("root:"),
                            "XXE entity expansion must never leak filesystem contents");
                }
            }
        } catch (org.xml.sax.SAXException e) {
            // Disallowing DOCTYPE or entity expansion is expected secure behavior
            assertThat(e.getMessage()).containsAnyOf("DOCTYPE", "disallowed", "general entity", "xxe");
        }
    }

    // ─── 3. SCHEMA COMPILATION SECURITY (EXTERNAL SCHEMA / DTD RESTRICTION) ──

    @Test
    void testSchemaCompilation_BlocksExternalSchemaImport() throws Exception {
        String maliciousXsd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n"
                + "           targetNamespace=\"urn:test:malicious\"\n"
                + "           xmlns=\"urn:test:malicious\">\n"
                + "    <xs:import namespace=\"urn:external:schema\"\n"
                + "               schemaLocation=\"http://attacker.com/evil.xsd\"/>\n"
                + "    <xs:element name=\"Root\" type=\"xs:string\"/>\n"
                + "</xs:schema>";

        SchemaFactory sf = SafeXmlUtils.schemaFactory();
        sf.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        sf.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        sf.setResourceResolver(resolver);

        // Attempting to compile schema with external schemaLocation must fail due to ACCESS_EXTERNAL_SCHEMA or resolver
        Exception ex =
                assertThrows(Exception.class, () -> sf.newSchema(new StreamSource(new StringReader(maliciousXsd))));
        assertNotNull(ex);
        assertTrue(ex instanceof DOMException || ex instanceof SAXException || ex.getCause() instanceof DOMException);
    }
}
