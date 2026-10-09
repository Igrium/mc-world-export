package com.igrium.worldexport.timeline;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Versioned optional server companion protocol. No sound bytes, credentials or arbitrary chat. */
public record StudioEventPayload(int version, String session, long sequence, String kind, String json)
        implements CustomPacketPayload {
    public static final Type<StudioEventPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("worldexport_studio", "event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StudioEventPayload> CODEC = CustomPacketPayload.codec(
        (p,b) -> {b.writeVarInt(p.version);b.writeUtf(p.session,64);b.writeVarLong(p.sequence);b.writeUtf(p.kind,128);b.writeUtf(p.json,262144);},
        b -> new StudioEventPayload(b.readVarInt(),b.readUtf(64),b.readVarLong(),b.readUtf(128),b.readUtf(262144)));
    @Override public Type<StudioEventPayload> type(){return TYPE;}
}
