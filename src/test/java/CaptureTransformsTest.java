import com.igrium.worldexport.entity.CaptureTransforms;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptureTransformsTest {
    private void check(Matrix4f original) {
        var pose=CaptureTransforms.decompose(original);
        var rebuilt=new Matrix4f().translate(pose.position()).rotate(pose.rotation()).scale(pose.scale());
        for (Vector3f point : new Vector3f[]{new Vector3f(),new Vector3f(1,2,3),new Vector3f(-2,1,-1)}) {
            assertTrue(original.transformPosition(point,new Vector3f()).distance(rebuilt.transformPosition(point,new Vector3f()))<1e-5);
        }
        assertFalse(pose.sheared());
        assertEquals(1,pose.rotation().lengthSquared(),1e-5);
    }
    @Test void scaledHeadItem() { check(new Matrix4f().translate(0,-.25f,0).rotateY((float)Math.PI).scale(.625f,-.625f,-.625f)); }
    @Test void nonuniformRotatedItem() { check(new Matrix4f().translate(2,3,4).rotateYXZ(.4f,.7f,.1f).scale(.8f,1.2f,.6f)); }
    @Test void reflectedItem() { check(new Matrix4f().rotateY(.7f).scale(-1,2,3)); }
    @Test void shearIsReported() { assertTrue(CaptureTransforms.decompose(new Matrix4f().scale(2,1,1).rotateY(.7f)).sheared()); }
    @Test void zeroScaleRemainsFinite() { var pose=CaptureTransforms.decompose(new Matrix4f().scale(0,1,2)); assertTrue(Float.isFinite(pose.rotation().w)); assertEquals(0,pose.scale().x); }
}
