package com.dsysm.compat;

import com.dsysm.compat.DsYsmConfig.RenderMode;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = DsYsmCompat.MOD_ID)
public final class DsYsmRenderSync {
    private static final int REMOVE = -1;
    // The registrar runs handlers on the main thread of the receiving side.
    private static final Map<UUID, Integer> SERVER_MODES = new HashMap<>();
    private static final Map<UUID, RenderMode> CLIENT_MODES = new HashMap<>();

    private DsYsmRenderSync() {
    }

    static void register(final RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional()
                .playToServer(SetMode.TYPE, SetMode.CODEC, DsYsmRenderSync::receivePreference)
                .playToClient(PlayerMode.TYPE, PlayerMode.CODEC, DsYsmRenderSync::receivePlayerMode);
    }

    public static RenderMode modeFor(final Player player) {
        final RenderMode synced = CLIENT_MODES.get(player.getUUID());
        return synced == null ? DsYsmConfig.mode() : synced;
    }

    static void clearClientModes() {
        CLIENT_MODES.clear();
    }

    static boolean canSendPreference(final ICommonPacketListener listener) {
        return NetworkRegistry.hasChannel(listener, SetMode.TYPE.id())
                && NetworkRegistry.hasChannel(listener, PlayerMode.TYPE.id());
    }

    static void sendPreference(final RenderMode mode) {
        PacketDistributor.sendToServer(new SetMode(encodeMode(mode)));
    }

    private static void receivePreference(final SetMode payload, final IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || decodeMode(payload.mode()) == null
                || !canSendPlayerMode(player)) {
            return;
        }

        final UUID id = player.getUUID();
        final Integer previous = SERVER_MODES.put(id, payload.mode());
        if (previous == null) {
            SERVER_MODES.forEach((otherId, mode) -> {
                if (!otherId.equals(id)) {
                    PacketDistributor.sendToPlayer(player, new PlayerMode(otherId, mode));
                }
            });
        }
        if (previous == null || previous != payload.mode()) {
            broadcast(player, new PlayerMode(id, payload.mode()));
        }
    }

    private static void receivePlayerMode(final PlayerMode payload, final IPayloadContext context) {
        if (payload.mode() == REMOVE) {
            CLIENT_MODES.remove(payload.playerId());
            return;
        }
        final RenderMode mode = decodeMode(payload.mode());
        if (mode != null) {
            CLIENT_MODES.put(payload.playerId(), mode);
        }
    }

    @SubscribeEvent
    public static void onLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SERVER_MODES.remove(player.getUUID()) != null) {
            broadcast(player, new PlayerMode(player.getUUID(), REMOVE));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(final ServerStoppedEvent event) {
        SERVER_MODES.clear();
    }

    private static void broadcast(final ServerPlayer source, final PlayerMode payload) {
        for (final ServerPlayer recipient : source.serverLevel().getServer().getPlayerList().getPlayers()) {
            if (recipient != source && canSendPlayerMode(recipient)) {
                PacketDistributor.sendToPlayer(recipient, payload);
            }
        }
    }

    private static boolean canSendPlayerMode(final ServerPlayer player) {
        return NetworkRegistry.hasChannel((ICommonPacketListener) player.connection, PlayerMode.TYPE.id());
    }

    private static int encodeMode(final RenderMode mode) {
        return switch (mode) {
            case AUTO -> 0;
            case YSM_PRIORITY -> 1;
            case DS_PRIORITY -> 2;
        };
    }

    private static RenderMode decodeMode(final int mode) {
        return switch (mode) {
            case 0 -> RenderMode.AUTO;
            case 1 -> RenderMode.YSM_PRIORITY;
            case 2 -> RenderMode.DS_PRIORITY;
            default -> null;
        };
    }

    private record SetMode(int mode) implements CustomPacketPayload {
        private static final Type<SetMode> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(DsYsmCompat.MOD_ID, "set_render_mode"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SetMode> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetMode::mode, SetMode::new);

        @Override
        public Type<SetMode> type() {
            return TYPE;
        }
    }

    private record PlayerMode(UUID playerId, int mode) implements CustomPacketPayload {
        private static final Type<PlayerMode> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(DsYsmCompat.MOD_ID, "player_render_mode"));
        private static final StreamCodec<RegistryFriendlyByteBuf, PlayerMode> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, PlayerMode::playerId,
                ByteBufCodecs.VAR_INT, PlayerMode::mode,
                PlayerMode::new);

        @Override
        public Type<PlayerMode> type() {
            return TYPE;
        }
    }
}
