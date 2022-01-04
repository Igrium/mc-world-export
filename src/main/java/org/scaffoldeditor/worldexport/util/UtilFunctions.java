package org.scaffoldeditor.worldexport.util;

import java.util.Collection;

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
                    name = name.substring(0, dot_index + 1) + String.valueOf(num);
                } catch (NumberFormatException e) {
                    name = name+".1";
                }
            }
        }

        return name;
    }
}
