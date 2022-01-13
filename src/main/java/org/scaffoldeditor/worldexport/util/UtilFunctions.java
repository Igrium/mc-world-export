package org.scaffoldeditor.worldexport.util;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.scaffoldeditor.worldexport.replay.ReplayEntity;

public final class UtilFunctions {
    private UtilFunctions() {}

    /**
     * Make sure a name has no duplicates in a list of names. Add ".[num]" at the
     * end if duplicates are found.
     * 
     * @param name     Name to check.
     * @param existing Collection to check against.
     * @return A version of the name with no duplicates.
     */
    public static String validateName(String name, Collection<String> existing) {
        while (existing.contains(name)) {
            int dot_index = name.lastIndexOf('.');
            if (dot_index < 0 || dot_index == name.length() - 1) {
                name = name+".1";
            } else {
                try {
                    int num = Integer.parseInt(name.substring(dot_index + 1));
                    name = name.substring(0, dot_index + 1) + String.valueOf(num + 1);
                } catch (NumberFormatException e) {
                    name = name+".1";
                }
            }
        }

        return name;
    }

    /**
     * Create a view of a set of entities that contains the names of said entities.
     * @param ents The set of entities.
     * @return The generated view.
     */
    public static Set<String> nameView(Set<ReplayEntity<?>> ents) {
        return new AbstractSet<String>() {

            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {
                    Iterator<ReplayEntity<?>> base = ents.iterator();

                    @Override
                    public boolean hasNext() {
                        return base.hasNext();
                    }

                    @Override
                    public String next() {
                        return base.next().getName();
                    }
                };
            }

            @Override
            public int size() {
                return ents.size();
            }
            
        };
    }
}
