package com.prowidesoftware.swift.model.mx.validation;

import com.prowidesoftware.swift.utils.SafeXmlUtils;
import java.io.StringReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Safely parses XML to extract structural metadata such as Document namespace and AppHdr MsgDefIdr.
 */
public class MessageStructureInfo {
    private final ISOMessageIdentifier documentIdentifier;
    private final ISOMessageIdentifier appHdrIdentifier;
    private final String msgDefIdr;
    private final String businessMessageId;

    public MessageStructureInfo(
            ISOMessageIdentifier documentIdentifier, ISOMessageIdentifier appHdrIdentifier, String msgDefIdr) {
        this(documentIdentifier, appHdrIdentifier, msgDefIdr, null);
    }

    public MessageStructureInfo(
            ISOMessageIdentifier documentIdentifier,
            ISOMessageIdentifier appHdrIdentifier,
            String msgDefIdr,
            String businessMessageId) {
        this.documentIdentifier = documentIdentifier;
        this.appHdrIdentifier = appHdrIdentifier;
        this.msgDefIdr = msgDefIdr;
        this.businessMessageId = businessMessageId;
    }

    public ISOMessageIdentifier getDocumentIdentifier() {
        return documentIdentifier;
    }

    public ISOMessageIdentifier getAppHdrIdentifier() {
        return appHdrIdentifier;
    }

    public String getMsgDefIdr() {
        return msgDefIdr;
    }

    public String getBusinessMessageId() {
        return businessMessageId;
    }

    public static MessageStructureInfo parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return new MessageStructureInfo(null, null, null, null);
        }
        try {
            XMLReader reader = SafeXmlUtils.reader(true, null);
            StructureHandler handler = new StructureHandler();
            reader.setContentHandler(handler);
            try {
                reader.parse(new InputSource(new StringReader(xml)));
            } catch (EarlyExitException e) {
                // Expected when we have found everything
            }
            String bizId = handler.bizMsgIdr != null ? handler.bizMsgIdr : handler.docMsgId;
            return new MessageStructureInfo(handler.docId, handler.appHdrId, handler.msgDefIdr, bizId);
        } catch (Exception e) {
            return new MessageStructureInfo(null, null, null, null);
        }
    }

    private static class EarlyExitException extends SAXException {
        // Exception to stop parsing once we found Document and AppHdr
    }

    private static class StructureHandler extends DefaultHandler {
        ISOMessageIdentifier docId = null;
        ISOMessageIdentifier appHdrId = null;
        String msgDefIdr = null;
        String bizMsgIdr = null;
        String docMsgId = null;

        boolean inMsgDefIdr = false;
        boolean inBizMsgIdr = false;
        boolean inDocMsgId = false;
        StringBuilder textBuilder = new StringBuilder();

        boolean foundDoc = false;
        boolean foundAppHdr = false;
        int docDepth = 0;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if ("Document".equals(localName)) {
                if (uri != null && !uri.isEmpty()) {
                    docId = ISOMessageIdentifier.fromNamespace(uri);
                }
                foundDoc = true;
            } else if ("AppHdr".equals(localName)) {
                if (uri != null && !uri.isEmpty()) {
                    appHdrId = ISOMessageIdentifier.fromNamespace(uri);
                }
                foundAppHdr = true;
            } else if (foundAppHdr && "MsgDefIdr".equals(localName)) {
                inMsgDefIdr = true;
                textBuilder.setLength(0);
            } else if (foundAppHdr && "BizMsgIdr".equals(localName)) {
                inBizMsgIdr = true;
                textBuilder.setLength(0);
            } else if (foundDoc && docMsgId == null && "MsgId".equals(localName)) {
                inDocMsgId = true;
                textBuilder.setLength(0);
            }

            if (foundDoc) {
                docDepth++;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (inMsgDefIdr || inBizMsgIdr || inDocMsgId) {
                textBuilder.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (inMsgDefIdr && "MsgDefIdr".equals(localName)) {
                msgDefIdr = textBuilder.toString().trim();
                inMsgDefIdr = false;
            } else if (inBizMsgIdr && "BizMsgIdr".equals(localName)) {
                bizMsgIdr = textBuilder.toString().trim();
                inBizMsgIdr = false;
            } else if (inDocMsgId && "MsgId".equals(localName)) {
                docMsgId = textBuilder.toString().trim();
                inDocMsgId = false;
                checkExit();
            } else if ("Document".equals(localName)) {
                foundDoc = false;
                checkExit();
            }

            if (foundDoc && docDepth > 0) {
                docDepth--;
            }
            if (docDepth > 10) {
                // Safety bound if MsgId is deep or not present
                checkExit();
            }
        }

        private void checkExit() throws EarlyExitException {
            if (docId != null && (appHdrId == null || (msgDefIdr != null && bizMsgIdr != null))) {
                if (docMsgId != null || !foundDoc || docDepth > 5) {
                    throw new EarlyExitException();
                }
            }
        }
    }
}
