package org.scaffoldeditor.worldexport.replaymod;

import net.minecraft.network.packet.Packet;
import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation;
import org.scaffoldeditor.worldexport.replaymod.util.FovProvider;
import org.scaffoldeditor.worldexport.replaymod.util.RollProvider;

import com.replaymod.replaystudio.util.Location;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class AnimatedCameraEntity extends Entity implements RollProvider, FovProvider {

    public static final Identifier ID = new Identifier("worldexport", "camera");

    public float roll;
    public double fov;

    private int color = 0xFFFFFFFF;

    public AnimatedCameraEntity(EntityType<? extends AnimatedCameraEntity> type, World world) {
        super(type, world);
        if (!world.isClient) {
            throw new IllegalStateException("Animated camera entity should never be spawned on the server!");
        }
    }

    @Override
    protected void initDataTracker() {        
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound var1) {        
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound var1) {        
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        throw new IllegalStateException("This entity is client-side only.");
    }

    @Override
    public ClientWorld getWorld() {
        return (ClientWorld) super.getWorld();
    }

    @Override
    public final float getRoll() {
        return roll;
    }

    public void setRoll(float roll) {
        this.roll = roll;
    }

    public final double getFov() {
        return fov;
    }

    public void setFov(double fov) {
        this.fov = fov;
    }

    @Override
    protected float getEyeHeight(EntityPose pose, EntityDimensions dimensions) {
        return 0;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    /**
     * Moves the camera by the specified delta.
     * @param x Delta in X direction
     * @param y Delta in Y direction
     * @param z Delta in Z direction
     */
    public void moveCamera(double x, double y, double z) {
        setCameraPosition(this.getX() + x, this.getY() + y, this.getZ() + z);
    }
    
    /**
     * Set the camera position.
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public void setCameraPosition(double x, double y, double z) {
        this.lastRenderX = this.prevX = x;
        this.lastRenderY = this.prevY = y;
        this.lastRenderZ = this.prevZ = z;
        this.setPosition(x, y, z);
    }

    /**
     * Sets the camera rotation.
     * @param yaw Yaw in degrees
     * @param pitch Pitch in degrees
     * @param roll Roll in degrees
     */
    public void setCameraRotation(float yaw, float pitch, float roll) {
        if (yaw != yaw || pitch != pitch) {
            LogManager.getLogger().error("Cannot set camera to NaN rotation. Yaw: {}, Pitch: {}", yaw, pitch);
            return;
        }

        this.prevYaw = yaw;
        this.prevPitch = pitch;
        setPitch(pitch);
        setYaw(yaw);
        setRoll(roll);
    }

    /**
     * Sets the camera rotation.
     * @param rotation Abstracted rotation object.
     */
    public void setCameraRotation(Rotation rotation) {
        float pitch = (float) Math.toDegrees(rotation.pitch());
        float yaw = (float) Math.toDegrees(rotation.yaw());
        float roll = (float) Math.toDegrees(rotation.roll());

        // TODO: verify this isn't fixing a mistake in the Blender addon
        yaw = -MathHelper.wrapDegrees(yaw + 180);
        pitch = 90 - pitch; // Why is Minecraft's rotation system so weird?

        setCameraRotation(yaw, pitch, roll);
    }

    public void setCameraPosRot(Location loc) {
        setCameraRotation(loc.getPitch(), loc.getYaw(), roll);
        setCameraPosition(loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false; // We are in full control of ourselves
    }

    @Override
    protected void spawnSprintingParticles() {
        // We do not produce any particles, we are a camera
    }

    @Override
    public boolean shouldSave() {
        return false;
    }
    
    @Override
    public boolean canHit() {
        return true; // Allows player to spectate
    }

}
