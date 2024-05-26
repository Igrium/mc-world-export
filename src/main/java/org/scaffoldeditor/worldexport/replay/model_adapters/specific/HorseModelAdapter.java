package org.scaffoldeditor.worldexport.replay.model_adapters.specific;

import java.util.Map;

import org.scaffoldeditor.worldexport.replay.model_adapters.AnimalModelAdapter;

import com.google.common.collect.Maps;

import net.minecraft.entity.passive.HorseColor;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class HorseModelAdapter extends AnimalModelAdapter<HorseEntity> {

    private static final Map<HorseColor, Identifier> TEXTURES = Util.make(Maps.newEnumMap(HorseColor.class),
            enumMap -> {
                enumMap.put(HorseColor.WHITE, new Identifier("textures/entity/horse/horse_white.png"));
                enumMap.put(HorseColor.CREAMY, new Identifier("textures/entity/horse/horse_creamy.png"));
                enumMap.put(HorseColor.CHESTNUT, new Identifier("textures/entity/horse/horse_chestnut.png"));
                enumMap.put(HorseColor.BROWN, new Identifier("textures/entity/horse/horse_brown.png"));
                enumMap.put(HorseColor.BLACK, new Identifier("textures/entity/horse/horse_black.png"));
                enumMap.put(HorseColor.GRAY, new Identifier("textures/entity/horse/horse_gray.png"));
                enumMap.put(HorseColor.DARK_BROWN, new Identifier("textures/entity/horse/horse_darkbrown.png"));
            });

    public HorseModelAdapter(HorseEntity entity) throws IllegalArgumentException {
        super(entity, TEXTURES.get(entity.getVariant()));
        
    }
}
