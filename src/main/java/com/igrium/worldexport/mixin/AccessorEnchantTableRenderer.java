package com.igrium.worldexport.mixin;

import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnchantTableRenderer.class)
public interface AccessorEnchantTableRenderer {
    @Accessor("bookModel")
    BookModel getBookModel();
}
