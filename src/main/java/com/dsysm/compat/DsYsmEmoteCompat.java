package com.dsysm.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DsYsmEmoteCompat {
    private static final String DS = "by.dragonsurvivalteam.dragonsurvival.";
    private static final String YSM = "com.elfmcys.yesstevemodel.";
    private static final String DRAGON_SCREEN = DS + "client.gui.screens.DragonEmoteScreen";
    private static final String WHEEL = YSM + "Oo0Oo0Oooo00OoOOOO0O00o0";
    private static final String DRAGON_RENDERER = DS + "client.render.ClientDragonRenderer";
    private static final String STOP = "ds_ysm_compat.stop";
    private static boolean failureLogged;

    private DsYsmEmoteCompat() {
    }

    public static Map<String, String> installDragonEntries(final Screen screen) {
        final Player player = Minecraft.getInstance().player;
        if (player == null || !DsYsmHooks.isDragon(player)) {
            return Map.of();
        }

        try {
            final Class<?> wheelClass = Class.forName(WHEEL);
            final Object modelOwner = field(wheelClass, "O0O0o0Oo0Oo00O0OooO00oOo").get(screen);
            if (modelOwner.getClass().getMethod("ooo00OoO00OOOO0oOooOo0Oo").invoke(modelOwner) != player) {
                return Map.of();
            }

            final Object body = dragonBody(player);
            if (!useDragonEmotes(player, body)) {
                return Map.of();
            }

            final Map<String, String> keys = new LinkedHashMap<>();
            final Map<String, String> labels = new LinkedHashMap<>();
            for (final Object emote : emotes(body)) {
                final String slot = "ds_ysm_compat." + keys.size();
                keys.put(slot, emoteKey(emote));
                labels.put(slot, ((Component) emote.getClass().getMethod("name").invoke(emote)).getString());
            }
            keys.put(STOP, "");
            labels.put(STOP, Component.translatable("gui.yes_steve_model.roulette.stop").getString());

            final Field slots = field(wheelClass, "O000OOo000oOOo0OO0oo0o0O");
            final Field page = field(wheelClass, "O0oo0O0O0O0oO0oooOOo00o0");
            final Object dragonSlots = slots.getType().getConstructor(Object[].class, Object[].class)
                    .newInstance(labels.keySet().toArray(), labels.values().toArray());
            final Object firstPage = Class.forName("org.apache.commons.lang3.tuple.MutablePair")
                    .getMethod("of", Object.class, Object.class).invoke(null, "", 0);

            // Replace only this screen's slots and page, since YSM shares its own map and navigation.
            slots.set(screen, dragonSlots);
            page.set(screen, firstPage);
            return keys;
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure(exception);
            return Map.of();
        }
    }

    public static void openDragonMenuIfUnchanged(final Screen previous) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (minecraft.screen != previous || player == null || !DsYsmHooks.isDragon(player)) {
            return;
        }
        try {
            if (useDragonEmotes(player, dragonBody(player))) {
                minecraft.setScreen((Screen) Class.forName(DRAGON_SCREEN).getConstructor().newInstance());
            }
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure(exception);
        }
    }

    public static void select(final String slot, final String emoteKey) {
        if (STOP.equals(slot)) {
            stopDragonEmotes();
            return;
        }

        final Player player = Minecraft.getInstance().player;
        if (player == null || !DsYsmHooks.isDragon(player)) {
            return;
        }
        try {
            final Object body = dragonBody(player);
            if (!useDragonEmotes(player, body)) {
                return;
            }
            for (final Object emote : emotes(body)) {
                if (emoteKey.equals(emoteKey(emote))) {
                    final Object dragon = getDragon(player);
                    if (stopPlayingEmote(dragon, emoteKey)) {
                        final Object packet = Class.forName(DS + "network.emotes.SyncEmote")
                                .getConstructor(int.class, emote.getClass(), boolean.class)
                                .newInstance(player.getId(), emote, true);
                        PacketDistributor.sendToServer((CustomPacketPayload) packet);
                        return;
                    }
                    Class.forName(DRAGON_RENDERER).getMethod("getOrCreateDragon", Player.class).invoke(null, player);
                    Class.forName(DRAGON_SCREEN).getMethod("addEmote", String.class).invoke(null, emoteKey);
                    return;
                }
            }
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure(exception);
        }
    }

    private static void stopDragonEmotes() {
        final Player player = Minecraft.getInstance().player;
        if (player == null || !DsYsmHooks.isDragon(player)) {
            return;
        }
        try {
            final Object dragon = getDragon(player);
            if (dragon == null) {
                return;
            }
            dragon.getClass().getMethod("stopAllEmotes").invoke(dragon);
            final Object packet = Class.forName(DS + "network.emotes.StopAllEmotes")
                    .getConstructor(int.class).newInstance(player.getId());
            PacketDistributor.sendToServer((CustomPacketPayload) packet);
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure(exception);
        }
    }

    public static Set<String> playingDragonEmoteKeys() {
        final Player player = Minecraft.getInstance().player;
        if (player == null) {
            return Set.of();
        }
        try {
            final Set<String> keys = new HashSet<>();
            for (final Object emote : playingEmotes(getDragon(player))) {
                if (emote != null) {
                    keys.add(emoteKey(emote));
                }
            }
            return keys;
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure(exception);
            return Set.of();
        }
    }

    public static boolean stopSyncedEmote(final Player player, final Object emote) {
        if (emote == null) {
            return false;
        }
        try {
            // Dragon Survival compares object identity, but a network packet reconstructs the emote.
            return stopPlayingEmote(getDragon(player), emoteKey(emote));
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure(exception);
            return false;
        }
    }

    private static boolean stopPlayingEmote(final Object dragon, final String key)
            throws ReflectiveOperationException {
        final Object[] playing = playingEmotes(dragon);
        for (int slot = 0; slot < playing.length; slot++) {
            if (playing[slot] != null && key.equals(emoteKey(playing[slot]))) {
                dragon.getClass().getMethod("stopEmote", int.class).invoke(dragon, slot);
                return true;
            }
        }
        return false;
    }

    private static Object getDragon(final Player player) throws ReflectiveOperationException {
        return Class.forName(DRAGON_RENDERER).getMethod("getDragon", Player.class).invoke(null, player);
    }

    private static Object[] playingEmotes(final Object dragon) throws ReflectiveOperationException {
        return dragon == null ? new Object[0]
                : (Object[]) dragon.getClass().getMethod("getCurrentlyPlayingEmotes").invoke(dragon);
    }

    private static String emoteKey(final Object emote) throws ReflectiveOperationException {
        return (String) emote.getClass().getMethod("key").invoke(emote);
    }

    private static boolean useDragonEmotes(final Player player, final Object body)
            throws ReflectiveOperationException {
        final DsYsmConfig.RenderMode mode = DsYsmConfig.mode();
        if (mode == DsYsmConfig.RenderMode.YSM_PRIORITY || player.isSpectator()
                || (boolean) body.getClass().getMethod("noDragonModelRendering").invoke(body)) {
            return false;
        }
        if (mode == DsYsmConfig.RenderMode.DS_PRIORITY) {
            return true;
        }

        final Class<?> config = Class.forName(YSM + "oOOoO0O0ooOo00O0o0OOOo0O");
        final ModConfigSpec.BooleanValue disableSelf = (ModConfigSpec.BooleanValue)
                config.getField("OO000o0ooOooooOOOOO0Ooo0").get(null);
        if (disableSelf.get()) {
            return true;
        }

        final Class<?> modelClass = Class.forName(YSM + "OoooO0OO0000O00oo0Oo00OO");
        final Optional<?> model = (Optional<?>) modelClass
                .getMethod("oOoo00O0o0oO0o0oO00OO0O0", Player.class).invoke(null, player);
        return model.isEmpty()
                || !(boolean) modelClass.getMethod("oOooO00OOooOO0ooo000o0o0").invoke(model.get())
                || Boolean.FALSE.equals(DsYsmHooks.lastYsmOwnership(player));
    }

    private static Object dragonBody(final Player player) throws ReflectiveOperationException {
        final Object data = Class.forName(DS + "common.capability.DragonStateProvider")
                .getMethod("getData", Player.class).invoke(null, player);
        return ((Holder<?>) data.getClass().getMethod("body").invoke(data)).value();
    }

    private static List<?> emotes(final Object body) throws ReflectiveOperationException {
        final Object emoteSet = ((Holder<?>) body.getClass().getMethod("emotes").invoke(body)).value();
        return (List<?>) emoteSet.getClass().getMethod("emotes").invoke(emoteSet);
    }

    private static Field field(final Class<?> type, final String name) throws NoSuchFieldException {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void logFailure(final Throwable exception) {
        if (!failureLogged) {
            failureLogged = true;
            DsYsmCompat.LOGGER.warn("[ds_ysm_compat] Dragon emote wheel integration failed", exception);
        }
    }
}
