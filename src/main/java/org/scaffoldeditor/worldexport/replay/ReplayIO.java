package org.scaffoldeditor.worldexport.replay;

import java.io.Writer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

public final class ReplayIO {
    private ReplayIO() {}

    /**
     * Serialize a replay entity into XML.
     * @param entity Entity to serialize.
     * @param target Writer to write the XML into.
     */
    public static void serializeEntity(BaseReplayEntity entity, Writer target) {
        DocumentBuilderFactory dFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder;
        try {
            dBuilder = dFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }

        Document doc = dBuilder.newDocument();
        doc.appendChild(ReplayEntity.writeToXML(entity, doc));

        try {
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            DOMSource dSource = new DOMSource(doc);

            StreamResult result = new StreamResult(target);

            transformer.transform(dSource, result);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }
}
