The replay exporter exports the following custom properties with various materials. The importer should read them and apply custom material attributes accordingly.

- `vertexTint: bool` If set, a global tint should be applied to the material based on its vertex color.

- `glint: bool` Signals the importer to apply an enchantment glint shader.

- `renderMode: 'dithered' | 'blended'` Specifies whether to use a dithered blending mode or an alpha blend. If unset, decision is left to the importer.


