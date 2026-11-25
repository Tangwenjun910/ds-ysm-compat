package com.dsysm.compat;

/**
 * 记录当前是否在世界 {@code LevelRenderer#renderLevel} 调用栈内。
 *
 * <p>DS 的第一人称龙身是在 renderLevel 里手动 {@code renderEntity(player)} 画出来的；
 * 而纸娃娃/HUD 预览也会渲染玩家，但发生在 renderLevel 之外。
 * 两者相机类型都是第一人称，只能靠这个标志区分。</p>
 */
public final class DsYsmRenderState {
    private static boolean inWorldRender;

    private DsYsmRenderState() {
    }

    public static void setInWorldRender(final boolean value) {
        inWorldRender = value;
    }

    public static boolean isInWorldRender() {
        return inWorldRender;
    }
}
