package com.igrium.worldexport.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.joml.Quaterniond;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.scaffoldeditor.worldexport.util.MathUtils;

public class QuaternionTest {

    @Test
    void testIdentical() {
        Quaterniond src = new Quaterniond(0.011248, 0.216742, 0.493966, 0.841958);
        Quaterniond target = new Quaterniond(src);
        assertEquals(src, target);
        
        MathUtils.makeQuatsCompatible(src, target, src);
        assertEquals(target, src, "makeQuatsCompatible fucked up identical quats");
    }
    
    @Test
    void testOpposite() {
        Quaterniond src = new Quaterniond(-0.277425, -0.138388, 0.277953, 0.90919);
        Quaterniond target = new Quaterniond(0.277425, 0.138388, -0.277953, -0.90919);
        
        MathUtils.makeQuatsCompatible(src, target, src);

        assertEquals(target, src, "These opposite quats should be made identical.");
    }

    @Test
    void testSimilar() {
        Quaterniond src = new Quaterniond(-0.458383, -0.538409, -0.538409, -0.458383);
        Quaterniond target = new Quaterniond(0.458383, 0.538409, 0.538409, 0.458383);
        MathUtils.makeQuatsCompatible(src, target, src);

        assertEquals(new Quaterniond(0.458383, 0.538409, 0.538409, 0.458383), src);
    }

    @Test 
    void testDifferent() {
        Quaterniond src = new Quaterniond(0.075258, 0.050387, 0.050387, 0.92997);
        Quaterniond target = new Quaterniond(0.462445, -0.242946, -0.850623, 0.05969);
        Quaterniond dest = MathUtils.makeQuatsCompatible(src, target, new Quaterniond());
        assertEquals(src, dest, "Quats "+src+" and "+target+" are  different. Src should not be touched.");
    }

    @RepeatedTest(100)
    void testRandom() {
        Random random = new Random();
        double circle = Math.PI * 2;
        double maxRot = Math.toRadians(10);
        float flipProb = .666f;

        for (int i = 0; i < 100; i++) {
           
        }

        Quaterniond target = new Quaterniond();
        target.rotateXYZ(random.nextDouble() * circle, random.nextDouble() * circle, random.nextDouble() * circle);

        Quaterniond src = new Quaterniond(target);
        src.rotateXYZ(random.nextDouble() * maxRot, random.nextDouble() * maxRot, random.nextDouble() * maxRot);
        
        Quaterniond subject = new Quaterniond(src);
        if (random.nextFloat() < flipProb) {
            subject.w = -subject.w;
            subject.x = -subject.x;
            subject.y = -subject.y;
            subject.z = -subject.z;
        }

        MathUtils.makeQuatsCompatible(subject, target, subject);

        assertEquals(src, subject, "Target: "+target);
    }

}
