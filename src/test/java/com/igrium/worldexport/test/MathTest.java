package com.igrium.worldexport.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;
import org.scaffoldeditor.worldexport.util.MathUtils;

public class MathTest {
    
    @Test
    void testQuats() {
        System.out.println("Testing");
        Quaterniond src = new Quaterniond(0.011248, 0.216742, 0.493966, 0.841958);
        Quaterniond target = new Quaterniond(src);
        assertEquals(src, target);
        
        MathUtils.makeQuatsCompatible(src, target, src);
        assertEquals(target, src, "makeQuatsCompatible fucked up identical quats");

        src = new Quaterniond(-0.277425, -0.138388, 0.277953, 0.90919);
        target = new Quaterniond(0.277425, 0.138388, -0.277953, -0.90919);
        
        MathUtils.makeQuatsCompatible(src, target, src);

        assertEquals(target, src, "These opposite quats should be made identical.");

        src = new Quaterniond(-0.458383, -0.538409, -0.538409, -0.458383);
        target = new Quaterniond(0.458383, 0.538409, 0.538409, 0.458383);
        MathUtils.makeQuatsCompatible(src, target, src);

        assertEquals(new Quaterniond(0.458383, 0.538409, 0.538409, 0.458383), src);

        src = new Quaterniond(0.075258, 0.050387, 0.050387, 0.92997);
        target = new Quaterniond(0.462445, -0.242946, -0.850623, 0.05969);
        Quaterniond dest = MathUtils.makeQuatsCompatible(src, target, new Quaterniond());
        assertEquals(src, dest, "Quats "+src+" and "+target+" are  different. Src should not be touched.");

    }
}
