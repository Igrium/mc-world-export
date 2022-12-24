package org.scaffoldeditor.worldexport.replaymod.render;

import java.util.Map;
import java.util.Optional;

import org.scaffoldeditor.worldexport.replaymod.AnimatedCameraEntity;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.AbstractCameraAnimation;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule.CameraPathFrame;
import org.scaffoldeditor.worldexport.util.RenderUtils;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.events.ReplayClosedCallback;
import com.replaymod.replay.events.ReplayOpenedCallback;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import com.replaymod.simplepathing.Setting;
import com.replaymod.simplepathing.gui.GuiPathing;
import com.replaymod.simplepathing.preview.PathPreviewRenderer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;

/**
 * Renders animated cameras and their paths.
 * @see PathPreviewRenderer
 */
public class CameraPathRenderer extends EventRegistrations {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private final CameraAnimationModule module;
    private final ReplayModSimplePathing simplePathing;
    private final CameraModelRenderer modelRenderer = new CameraModelRenderer();

    // private static final float PATH_STEP
    private static final float MAX_DISTANCE_SQUARED = 6;

    private ReplayHandler replayHandler;

    public CameraPathRenderer(CameraAnimationModule module, ReplayModSimplePathing simplePathing) {
        this.module = module;
        this.simplePathing = simplePathing;

        on(ReplayOpenedCallback.EVENT, replayHandler -> {
            this.replayHandler = replayHandler;
        });

        on(ReplayClosedCallback.EVENT, replayHandler -> {
            this.replayHandler = null;
        });
    }

    public CameraModelRenderer getModelRenderer() {
        return modelRenderer;
    }

    public ReplayHandler getReplayHandler() {
        return replayHandler;
    }

    public synchronized void setReplayHandler(ReplayHandler replayHandler) {
        this.replayHandler = replayHandler;
    }

    /**
     * Render all camera paths into the world.
     * @param context World render context.
     */
    public synchronized void render(WorldRenderContext context) {
        if (!shouldRender()) return;

        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Map<Integer, AbstractCameraAnimation> animations = module.getAnimations(replayHandler.getReplayFile());
        for (AbstractCameraAnimation animation : animations.values()) {
            renderAnimPath(matrices, context.consumers(), animation);
        }
        
        // Apparently Johni doesn't understand frontend / backend separation.
        // I should *not* have to go into UI code to get this.
        GuiPathing guiPathing = simplePathing.getGuiPathing();
        if (guiPathing == null) return;
        double time = guiPathing.timeline.getCursorPosition() / 1000d;

        for (AbstractCameraAnimation animation : animations.values()) {
            Optional<AnimatedCameraEntity> ent = module.optCameraEntity(client.world, animation.getId());
            // Don't render if we're currently viewing from this camera.
            if (ent.isPresent() && ent.get().equals(client.cameraEntity)) continue;
            modelRenderer.render(animation, time, matrices, context.consumers(), 15);
        }

        matrices.pop();
    }

    private void renderAnimPath(MatrixStack matrices, VertexConsumerProvider vertexConsumers, AbstractCameraAnimation animation) {
        int color = RenderUtils.stripAlpha(animation.getColor().getColorValue());
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getLines());

        Vec3f prevPos = null;
        Vec3f pos;
        Vec3f delta = new Vec3f();
        for (CameraPathFrame frame : animation) {
            pos = new Vec3f(frame.pos());
            if (prevPos != null && vecLengthSquared(subtractVec(pos, prevPos, delta)) <= MAX_DISTANCE_SQUARED) {
                drawLine(matrices, consumer, prevPos, pos, color);
            }
            prevPos = pos;
        }
        
    }

    // Why the fuck does Vec3f not have these functions inbuilt?
    private static Vec3f subtractVec(Vec3f first, Vec3f second, Vec3f dest) {
        dest.set(
            second.getX() - first.getX(),
            second.getY() - first.getY(),
            second.getZ() - first.getZ()
        );
        return dest;
    }

    private static float vecLengthSquared(Vec3f vec) {
        float x = vec.getX();
        float y = vec.getY();
        float z = vec.getZ();
        return (x * x + y * y + z * z);
    }

    private void drawLine(MatrixStack matrices, VertexConsumer consumer, Vec3f pos1, Vec3f pos2, int color) {
        drawLine(matrices.peek(), consumer,
                pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ(), color);
    }

    private void drawLine(MatrixStack.Entry matrix, VertexConsumer consumer, float x1, float y1, float z1,
            float x2, float y2, float z2, int color) {
        
        Matrix4f model = matrix.getPositionMatrix();
        Matrix3f normal = matrix.getNormalMatrix();

        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;

        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        consumer.vertex(model, x1, y1, z1)
                .color(color)
                .normal(normal, dx / len, dy / len, dz / len)
                .next();

        consumer.vertex(model, x2, y2, z2)
                .color(color)
                .normal(normal, dx / len, dy / len, dz / len)
                .next();
    }

    protected boolean shouldRender() {
        return (replayHandler != null
                && !client.options.hudHidden
                && simplePathing.getCore().getSettingsRegistry().get(Setting.PATH_PREVIEW)
                && replayHandler.getReplaySender().isAsyncMode());
    }
}
