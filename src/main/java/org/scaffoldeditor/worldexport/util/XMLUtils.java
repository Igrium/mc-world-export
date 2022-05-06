package org.scaffoldeditor.worldexport.util;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class XMLUtils {
    private XMLUtils() {}

    /**
     * Get a list of all the <i>direct</i> children of this element with a given tag name.
     * @param element Element to search through.
     * @param name Name to search through.
     * @return Child elements.
     */
    public static List<Element> getChildrenByTagName(Element element, String name) {
        NodeList children = element.getChildNodes();
        List<Element> childElements = new ArrayList<>();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) continue;
            Element cElement = (Element) child;
            if (cElement.getTagName().equals(name)) {
                childElements.add(cElement);
            }
        }

        return childElements;
    }
}
