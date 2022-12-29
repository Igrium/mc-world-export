package org.scaffoldeditor.worldexport.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.minecraft.util.math.Vec3d;

public final class XMLUtils {
    private XMLUtils() {}

    /**
     * A wrapper for {@link NodeList} that actually implements <code>List</code>
     */
    public static class JavaNodeList extends AbstractList<Node> {

        private NodeList base;

        public JavaNodeList(NodeList base) {
            this.base = base;
        }

        @Override
        public Node get(int index) {
            return base.item(index);
        }

        @Override
        public int size() {
            return base.getLength();
        }

    }

    /**
     * Get a list of all the <i>direct</i> children of this element with a given tag name.
     * @param element Element to search through.
     * @param name Name to search for.
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

    /**
     * Get the first direct child element with a given tag name.
     * @param element The element to search.
     * @param name Name to search for.
     * @return The child element, or <code>null</code> if none was found.
     */
    public static Element getFirstElementWithName(Element element, String name) {
        NodeList childeren = element.getChildNodes();
        for (int i = 0; i < childeren.getLength(); i++) {
            Node child = childeren.item(i);
            if (!(child instanceof Element)) continue;
            Element cElement = (Element) child;
            if (cElement.getTagName().equals(name)) {
                return cElement;
            }
        }
        return null;
    }

    /**
     * Write a set of strings into a stringified list.
     * @param vals The strings to write.
     * @return The stringified list.
     * @see XMLUtils#readList
     */
    public static String writeList(Iterable<? extends CharSequence> vals) {
        return '[' + String.join(", ", vals) + ']';
    }

    /**
     * Write a set of strings into a stringified list.
     * @param vals The strings to write.
     * @return The stringified list.
     * @see XMLUtils#readList
     */
    public static String writeList(CharSequence... vals) {
        return '[' + String.join(", ", vals) + ']';
    }
    
    /**
     * Deserialize a stringified list.
     * @param list The list.
     * @return An array with all the list values.
     * @throws IllegalArgumentException If the passed string is improperly formatted.
     * @see XMLUtils#writeList
     */
    public static String[] readList(String list) throws IllegalArgumentException {
        list = list.strip();
        if (list.charAt(0) != '[' || list.charAt(list.length() - 1) != ']') {
            throw new IllegalArgumentException("Unbalenced list brackets");
        }
        list = list.substring(1, list.length() - 1);
        String[] vals = list.split(",");
        for (int i = 0; i < vals.length; i++) {
            vals[i] = vals[i].strip();
        }
        return vals;
    }
    
    /**
     * Write a vector into a stringified list.
     * @param vec The vector.
     * @return The stringified list.
     */
    public static String writeVector(Vec3d vec) {
        return writeList(new String[] {
            String.valueOf(vec.x),
            String.valueOf(vec.y),
            String.valueOf(vec.z)
        });
    }

    /**
     * Read a vector from a stringified list. List must have a length of exactly <code>3</code>.
     * @param str The stringified list.
     * @return The parsed vector.
     * @throws IllegalArgumentException If the stringified list is improperly formatted.
     */
    public static Vec3d parseVector(String str) throws IllegalArgumentException {
        String[] list = readList(str);
        if (list.length != 3) {
            throw new IllegalArgumentException("List must contain 3 values.");
        }
        return new Vec3d(
                Double.parseDouble(list[0]),
                Double.parseDouble(list[1]),
                Double.parseDouble(list[2]));
    }
}
