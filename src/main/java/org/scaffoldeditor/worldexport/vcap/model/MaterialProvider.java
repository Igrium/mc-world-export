package org.scaffoldeditor.worldexport.vcap.model;

import org.scaffoldeditor.worldexport.mat.Material;

/**
 * A "prototype" material that will be generated at a later time during export.
 */
public interface MaterialProvider {
    Material writeMaterial();
}
