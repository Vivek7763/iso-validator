package com.prowidesoftware.swift.model.mx.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMException;
import org.w3c.dom.ls.LSInput;

class ClasspathResourceResolverTest {

    private final ClasspathResourceResolver resolver = new ClasspathResourceResolver();

    @Test
    void blocksHttpExternalEntityAttempt() {
        DOMException ex = assertThrows(
                DOMException.class,
                () -> resolver.resolveResource(
                        null, null, null, "http://malicious.com/schema.xsd", "http://malicious.com/schema.xsd"));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void blocksHttpsExternalEntityAttempt() {
        DOMException ex = assertThrows(
                DOMException.class,
                () -> resolver.resolveResource(
                        null, null, null, "https://malicious.com/schema.xsd", "https://malicious.com/schema.xsd"));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void blocksFileExternalEntityAttempt() {
        DOMException ex = assertThrows(
                DOMException.class,
                () -> resolver.resolveResource(null, null, null, "file:///etc/passwd", "file:///etc/passwd"));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void blocksFileSingleSlashAttempt() {
        DOMException ex = assertThrows(
                DOMException.class,
                () -> resolver.resolveResource(null, null, null, "file:/etc/passwd", "file:/etc/passwd"));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void blocksJarProtocolAttempt() {
        DOMException ex = assertThrows(
                DOMException.class,
                () -> resolver.resolveResource(null, null, null, "jar:file:/evil.jar!/schema.xsd", null));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void blocksPathTraversalAttempt() {
        DOMException ex = assertThrows(
                DOMException.class, () -> resolver.resolveResource(null, null, null, "../../../etc/passwd", null));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("Path traversal blocked for security"));
    }

    @Test
    void blocksFtpExternalEntityAttempt() {
        DOMException ex = assertThrows(
                DOMException.class,
                () -> resolver.resolveResource(
                        null, null, null, "ftp://example.com/exploit.dtd", "ftp://example.com/exploit.dtd"));
        assertEquals(DOMException.NOT_SUPPORTED_ERR, ex.code);
        assertTrue(ex.getMessage().contains("External resource resolution blocked for security"));
    }

    @Test
    void resolvesExistingClasspathResource() {
        LSInput input = resolver.resolveResource(
                null,
                "urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12",
                null,
                "pacs/pacs.002.001.12/pacs.002.001.12.xsd",
                null);
        assertNotNull(input);
        assertNotNull(input.getByteStream());
    }

    @Test
    void returnsNullForNonexistentClasspathResource() {
        LSInput input = resolver.resolveResource(
                null, "urn:iso:std:iso:20022:tech:xsd:nonexistent", null, "nonexistent.xsd", null);
        assertNull(input);
    }
}
