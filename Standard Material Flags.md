The replay exporter exports the following custom properties with various materials. The importer should read them and apply custom material attributes accordingly.

- `vertexTint: bool` If set, a global tint should be applied to the material based on its vertex color.

- `glint: bool` Signals the importer to apply an enchantment glint shader.

- `renderMode: 'dithered' | 'blended'` Specifies whether to use a dithered blending mode or an alpha blend. Default: 'dithered'

- `grassOverlayX: float`, `grassOverlayY: float` If both are set, a second, tinted layer will be overlaid on the first, using the color channel with its UVs offset by the specified amounts.

- 
