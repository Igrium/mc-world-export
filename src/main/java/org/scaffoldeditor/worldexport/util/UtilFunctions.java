package org.scaffoldeditor.worldexport.util;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.scaffoldeditor.worldexport.replay.ReplayEntity;

public final class UtilFunctions {
    private UtilFunctions() {}

    /**
     * Make sure a name has no duplicates in a collection of names. Add ".[num]" at the
     * end if duplicates are found.
     * 
     * @param name     Name to check.
     * @param existing Collection to check against.
     * @return A version of the name with no duplicates.
     */
    public static String validateName(String name, Collection<String> existing) {
        return validateName(name, existing::contains);
    }

    /**
     * Make sure a name has no duplicates in a collection of names. Add ".[num]" at
     * the end if duplicates are found.
     * 
     * @param name       Name to check.
     * @param nameExists Function to check if the collection already has a given
     *                   name.
     * @return A version of the name with no duplicates.
     */
    public static String validateName(String name, Predicate<String> nameExists) {
        while (nameExists.test(name)) {
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex < 0 || dotIndex == name.length() - 1) {
                name = name + ".1";
            } else {
                try {
                    int num = Integer.parseInt(name.substring(dotIndex + 1));
                    name = name.substring(0, dotIndex + 1) + String.valueOf(num + 1);
                } catch (NumberFormatException e) {
                    name = name + ".1";
                }
            }
        }

        return name;
    }

    /**
     * A wrapper around a set that contains values from the objects of said set.
     */
    public static class SetView<T, U> extends AbstractSet<T> {
        private Function<U, T> getterFunction;
        private Set<U> base;

        /**
         * Create a wrapper around a set that contains a value from the objects inside the base set.
         * @param base The base set.
         * @param getterFunction A function witch will retrieve (or calculate) the target value.
         */
        public SetView(Set<U> base, Function<U, T> getterFunction) {
            this.base = base;
            this.getterFunction = getterFunction;
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<>() {
                Iterator<U> baseIterator = base.iterator();

                @Override
                public boolean hasNext() {
                    return baseIterator.hasNext();
                }

                @Override
                public T next() {
                    return getterFunction.apply(baseIterator.next());
                }
                
            };
        }

        @Override
        public int size() {
            return base.size();
        }

    }

    /**
     * Create a view of a set of entities that contains the names of said entities.
     * @param ents The set of entities.
     * @return The generated view.
     */
    public static Set<String> nameView(Set<ReplayEntity<?>> ents) {
        return new SetView<>(ents, (ent) -> ent.getName());
    }

}
