package com.sun.xml.ws.server;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.istack.SAXParseException2;
import com.sun.xml.bind.unmarshaller.DOMScanner;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.NextAction;
import com.sun.xml.ws.api.pipe.Tube;
import com.sun.xml.ws.api.pipe.TubeCloner;
import com.sun.xml.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.ws.api.pipe.helper.AbstractTubeImpl;
import com.sun.xml.ws.api.server.DocumentAddressResolver;
import com.sun.xml.ws.api.server.SDDocument;
import com.sun.xml.ws.api.server.ServiceDefinition;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.util.ByteArrayBuffer;
import com.sun.xml.ws.util.xml.XmlUtil;
import com.sun.xml.ws.wsdl.parser.WSDLConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.*;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Tube} that does the schema validation on the server side.
 *
 * @author Jitendra Kotamraju
 */
public class SchemaValidationTube extends AbstractFilterTubeImpl {

    private static final Logger LOGGER = Logger.getLogger(SchemaValidationTube.class.getName());

    private final WSBinding binding;
    private final ServiceDefinition docs;
    private final Schema schema;
    private final Validator validator;
    private final DocumentAddressResolver resolver = new ValidationDocumentAddressResolver();

    public SchemaValidationTube(WSEndpoint endpoint, WSBinding binding, Tube next) {
        super(next);
        this.binding = binding;
        docs = endpoint.getServiceDefinition();
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        sf.setResourceResolver(new ResourceResolver());
        Source[] sources = getSchemaSources();
        for(Source source : sources) {
            LOGGER.fine("Constructing validation Schema from = "+source.getSystemId());
            //printDOM((DOMSource)source);
        }
        try {
            schema = sf.newSchema(sources);
        } catch(SAXException e) {
            throw new WebServiceException(e);
        }
        validator = schema.newValidator();
    }

    private Source[] getSchemaSources() {
        List<Source> list = new ArrayList<Source>();
        for(SDDocument doc : docs) {
            // Add all xsd:schema fragments from all WSDLs. That should form a closure of schemas.
            if (doc.isWSDL()) {
                Document dom = createDOM(doc);
                // Get xsd:schema node from WSDL's DOM
                addSchemaFragmentSource(dom, doc, list);
            }
        }
        // If there are multiple schemas with the same targetnamespace,
        // JAXP works only with the first one. Above, all schema fragments may have the same targetnamespace,
        // and that means it will not include all the schemas. Since we have a list of schemas, just add them.
        addSchemaSource(list);
        return list.toArray(new Source[list.size()]) ;
    }

    private Document createDOM(SDDocument doc) {
        ByteArrayBuffer bab = new ByteArrayBuffer();
        try {
            doc.writeTo(null, resolver, bab);
        } catch (IOException ioe) {
            throw new WebServiceException(ioe);
        }

        // Get WSDL infoset as DOM
        Transformer trans = XmlUtil.newTransformer();
        Source source = new StreamSource(bab.newInputStream(), null); //doc.getURL().toExternalForm());
        DOMResult result = new DOMResult();
        try {
            trans.transform(source, result);
        } catch(TransformerException te) {
            throw new WebServiceException(te);
        }
        return (Document)result.getNode();
    }

    /**
     * All schema documents are turned to Source and are added to the list
     *
     * @param list of schema sources
     */
    private void addSchemaSource(List<Source> list) {
        for(SDDocument doc : docs) {
            if (doc.isSchema()) {
                Document dom = createDOM(doc);
                list.add(new DOMSource(dom, doc.getURL().toExternalForm()));
            }
        }
    }

    private class ResourceResolver implements LSResourceResolver {

        public LSInput resolveResource(String type, String namespaceURI, String publicId, final String systemId, final String baseURI) {
            LOGGER.fine("type="+type+ " namespaceURI="+namespaceURI+" publicId="+publicId+" systemId="+systemId+" baseURI="+baseURI);
            try {
                URL base = baseURI == null ? null : new URL(baseURI);
                final URL rel = new URL(base, systemId);
                for(final SDDocument doc : docs) {
                    if (doc.getURL().toExternalForm().equals(rel.toExternalForm())) {
                        return new LSInput() {

                            public Reader getCharacterStream() {
                                return null;
                            }

                            public void setCharacterStream(Reader characterStream) {
                                throw new UnsupportedOperationException();
                            }

                            public InputStream getByteStream() {
                                ByteArrayBuffer bab = new ByteArrayBuffer();
                                try {
                                    doc.writeTo(null, resolver, bab);
                                } catch (IOException ioe) {
                                    throw new WebServiceException(ioe);
                                }
                                return bab.newInputStream();
                            }

                            public void setByteStream(InputStream byteStream) {
                                throw new UnsupportedOperationException();
                            }

                            public String getStringData() {
                                return null;
                            }

                            public void setStringData(String stringData) {
                                throw new UnsupportedOperationException();
                            }

                            public String getSystemId() {
                                return rel.toExternalForm();
                            }

                            public void setSystemId(String systemId) {
                                throw new UnsupportedOperationException();
                            }

                            public String getPublicId() {
                                return null;
                            }

                            public void setPublicId(String publicId) {
                                throw new UnsupportedOperationException();
                            }

                            public String getBaseURI() {
                                return rel.toExternalForm();
                            }

                            public void setBaseURI(String baseURI) {
                                throw new UnsupportedOperationException();
                            }

                            public String getEncoding() {
                                return null;
                            }

                            public void setEncoding(String encoding) {
                                throw new UnsupportedOperationException();
                            }

                            public boolean getCertifiedText() {
                                return false;
                            }

                            public void setCertifiedText(boolean certifiedText) {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }
                }

            } catch(Exception e) {
                LOGGER.log(Level.WARNING, "Exception in LSResourceResolver impl", e);
            }
            LOGGER.fine("Don't know about systemId="+systemId+" baseURI="+baseURI);
            return null;
        }

    }

    /**
     * Locates xsd:schema elements in the WSDL and creates DOMSource and adds them to the list
     *
     * @param doc WSDL document
     * @param sddoc SDDocument for WSDL document
     * @param list xsd:schema DOMSource list
     */
    private @Nullable void addSchemaFragmentSource(Document doc, SDDocument sddoc, List<Source> list) {

        Element e = doc.getDocumentElement();
        assert e.getNamespaceURI().equals(WSDLConstants.NS_WSDL);
        assert e.getLocalName().equals("definitions");

        NodeList typesList = e.getElementsByTagNameNS(WSDLConstants.NS_WSDL, "types");
        for(int i=0; i < typesList.getLength(); i++) {
            if (typesList.item(i) instanceof Element) {
                NodeList schemaList = ((Element)typesList.item(i)).getElementsByTagNameNS(WSDLConstants.NS_XMLNS, "schema");
                for(int j=0; j < schemaList.getLength(); j++) {
                    Element elem = (Element)schemaList.item(j);
                    Source source = new MySource(elem);
                    source.setSystemId(sddoc.getURL().toExternalForm()+"#schema"+j);
                    source = this.toDOMSource(source);
                    list.add(source);

                    //list.add(new DOMSource(elem, sddoc.getURL().toExternalForm()+"#schema"+j));
                }
            }
        }
    }

    private static class MySource extends SAXSource {

        private final Element elem;


        // this object will pretend as an XMLReader.
        // no matter what parameter is specified to the parse method,
        // it will just read from the DOM element
        private final XMLReader pseudoParser = new XMLReader() {
            public boolean getFeature(String name) throws SAXNotRecognizedException {
                throw new SAXNotRecognizedException(name);
            }

            public void setFeature(String name, boolean value) throws SAXNotRecognizedException {
                // Should support these two features according to XMLReader javadoc.
                if (name.equals("http://xml.org/sax/features/namespaces") && value) {
                    // Ignore for now
                } else if (name.equals("http://xml.org/sax/features/namespace-prefixes") && !value) {
                    // Ignore for now
                } else {
                    throw new SAXNotRecognizedException(name);
                }
            }

            public Object getProperty(String name) throws SAXNotRecognizedException {
                if ("http://xml.org/sax/properties/lexical-handler".equals(name)) {
                    return lexicalHandler;
                }
                throw new SAXNotRecognizedException(name);
            }

            public void setProperty(String name, Object value) throws SAXNotRecognizedException {
                if ("http://xml.org/sax/properties/lexical-handler".equals(name)) {
                    this.lexicalHandler = (LexicalHandler) value;
                    return;
                }
                throw new SAXNotRecognizedException(name);
            }

            private LexicalHandler lexicalHandler;

            // we will store this value but never use it by ourselves.
            private EntityResolver entityResolver;

            public void setEntityResolver(EntityResolver resolver) {
                this.entityResolver = resolver;
            }

            public EntityResolver getEntityResolver() {
                return entityResolver;
            }

            private DTDHandler dtdHandler;

            public void setDTDHandler(DTDHandler handler) {
                this.dtdHandler = handler;
            }

            public DTDHandler getDTDHandler() {
                return dtdHandler;
            }

            private ContentHandler contentHandler;

            public void setContentHandler(ContentHandler handler) {
                this.contentHandler = handler;
            }

            public ContentHandler getContentHandler() {
                return contentHandler;
            }

            private ErrorHandler errorHandler;

            public void setErrorHandler(ErrorHandler handler) {
                this.errorHandler = handler;
            }

            public ErrorHandler getErrorHandler() {
                return errorHandler;
            }

            public void parse(InputSource input) throws SAXException {
                parse();
            }

            public void parse(String systemId) throws SAXException {
                parse();
            }

            public void parse() throws SAXException {
                // parses from a StAX reader and generates SAX events which
                // go through the repeater and are forwarded to the appropriate
                // component
                try {
                    DOMScanner scanner = new DOMScanner();
                    scanner.setContentHandler(contentHandler);
                    scanner.scan(elem);
                } catch (Exception e) {
                    // wrap it in a SAXException
                    SAXParseException se =
                            new SAXParseException2(e.getMessage(), null, null, -1, -1, e);

                    // if the consumer sets an error handler, it is our responsibility
                    // to notify it.
                    if (errorHandler != null)
                        errorHandler.fatalError(se);

                    // this is a fatal error. Even if the error handler
                    // returns, we will abort anyway.
                    throw se;

                }
            }
        };


        public MySource(Element element) {
            this.elem = element;

            super.setXMLReader(pseudoParser);
            // pass a dummy InputSource. We don't care
            super.setInputSource(new InputSource());
        }

    }

    /*
    private @Nullable void patchDOMFragment(Element elem) {
        Element dest = elem;
        while(elem.getParentNode().getNodeType() != Node.DOCUMENT_NODE) {
            Element parent = (Element)elem.getParentNode();

            if (parent.hasAttributes()) {
                NamedNodeMap attrs = parent.getAttributes();
                int numOfAttributes = attrs.getLength();

                for (int i = 0; i < numOfAttributes; i++) {
                    Node attr = attrs.item(i);
                    String nsUri = attr.getNamespaceURI();
                    if (nsUri != null && nsUri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                        // handle default ns declarations
                        String local = attr.getLocalName().equals(XMLConstants.XMLNS_ATTRIBUTE) ? "" : attr.getLocalName();
                        
                        //if (local.equals(nodePrefix) && attr.getNodeValue().equals(nodeNS)) {
                        //    prefixDecl = true;
                        //}
                        if (local.equals("")) {
                            //writer.writeDefaultNamespace(attr.getNodeValue());
                        } else {
                            dest.
                            // this is a namespace declaration, not an attribute
                            LOGGER.info("prefix="+local+" nsuri="+dest.lookupNamespaceURI(local));

                            //writer.setPrefix(attr.getLocalName(), attr.getNodeValue());
                            //writer.writeNamespace(attr.getLocalName(), attr.getNodeValue());
                        }
                    }
                }
            }
            elem = parent;
        }
    }
    */


    private static class ValidationDocumentAddressResolver implements DocumentAddressResolver {

        @Nullable
        public String getRelativeAddressFor(@NotNull SDDocument current, @NotNull SDDocument referenced) {
            LOGGER.fine("Current = "+current.getURL()+" resolved relative="+referenced.getURL());
            return referenced.getURL().toExternalForm();
        }
    }

    protected SchemaValidationTube(SchemaValidationTube that, TubeCloner cloner) {
        super(that,cloner);
        this.binding = that.binding;
        this.docs = that.docs;
        this.schema = that.schema;
        this.validator = schema.newValidator();
    }

    @Override
    public NextAction processRequest(Packet request) {
        if (!request.getMessage().hasPayload() || request.getMessage().isFault()) {
            return super.processRequest(request);
        }
        validator.reset();
        Message msg = request.getMessage().copy();
        Source source = msg.readPayloadAsSource();
        try {
            // Validator javadoc allows ONLY SAX, and DOM Sources
            // But the impl seems to handle all kinds.
            validator.validate(source);
        } catch(IOException e) {
            throw new WebServiceException(e);
        } catch(SAXException e) {
            throw new WebServiceException(e);
        }
        return super.processRequest(request);
    }

    @Override
    public NextAction processResponse(Packet response) {
        if (response.getMessage() == null || !response.getMessage().hasPayload() || response.getMessage().isFault()) {
            return super.processResponse(response);
        }
        validator.reset();
        Message msg = response.getMessage().copy();
        Source source = msg.readPayloadAsSource();
        try {
            // Validator javadoc allows ONLY SAX, and DOM Sources
            // But the impl seems to handle all kinds.
            validator.validate(source);
        } catch(IOException e) {
            throw new WebServiceException(e);
        } catch(SAXException e) {
            throw new WebServiceException(e);
        }
        return super.processResponse(response);
    }

    private DOMSource toDOMSource(Source source) {
        if (source instanceof DOMSource) {
            return (DOMSource)source;
        }
        Transformer trans = XmlUtil.newTransformer();
        DOMResult result = new DOMResult();
        try {
            trans.transform(source, result);
        } catch(TransformerException te) {
            throw new WebServiceException(te);
        }
        return new DOMSource(result.getNode());
    }

    private static void printDOM(DOMSource src) {
        try {
            ByteArrayBuffer bos = new ByteArrayBuffer();
            StreamResult sr = new StreamResult(bos );
            Transformer trans = TransformerFactory.newInstance().newTransformer();
            trans.transform(src, sr);
            LOGGER.info("**** src ******"+bos.toString());
            bos.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public AbstractTubeImpl copy(TubeCloner cloner) {
        return new SchemaValidationTube(this,cloner);
    }

}
