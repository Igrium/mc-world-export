package org.scaffoldeditor.worldexport.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An wrapper for an item that can be used in a tree iterator.
 */
public interface TreeNode<T extends TreeNode<T>> extends Iterable<T> {
    /**
     * Get an iterator of all the immediate child nodes of this node.
     */
    public Iterator<T> getChildren();

    /**
     * Determine whether this node has any children.
     */
    public boolean hasChildren();

    /**
     * Get a tree iterator that iterates over ALL the children of this tree.
     * @return Iterator of children. Does not include this node.
     */
    @Override
    default Iterator<T> iterator() {
        return new TreeIterator<T>(getChildren());
    }
    
    /**
     * A basic tree node that can wrap an object that doesn't implement TreeNode.
     */
    public static class BasicTreeNode<T> implements TreeNode<BasicTreeNode<T>> {

        public final List<BasicTreeNode<T>> children = new ArrayList<>();
        public T item;

        BasicTreeNode(T item) {
            this.item = item;
        }

        @Override
        public Iterator<BasicTreeNode<T>> getChildren() {
            return children.iterator();
        }

        @Override
        public boolean hasChildren() {
            return children.size() > 0;
        }
    }
}
