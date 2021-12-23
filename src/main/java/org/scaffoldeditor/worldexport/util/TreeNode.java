package org.scaffoldeditor.worldexport.util;

import java.util.Iterator;

/**
 * An wrapper for an item that can be used in a tree iterator.
 */
public interface TreeNode<T> {
    /**
     * Get an iterator of all the child nodes of this node.
     */
    public Iterator<T> getChildren();

    /**
     * Determine whether this node has any children.
     */
    public boolean hasChildren();
}
