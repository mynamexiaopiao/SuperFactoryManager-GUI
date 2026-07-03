package ca.teamdman.sfmgui.client;

import ca.teamdman.sfmgui.SFMGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Injects a "resource picker + copy" bar onto SFM's program text-editor screens
 * (SFMTextEditScreenV1 / V2) without modifying SFM. Drawn and click-handled purely
 * from screen events:
 * <pre>
 *   [ icon ] [ resource name .......... ] [ Copy ]
 * </pre>
 * <ul>
 *   <li>clicking the icon (or the name field) opens the shared {@link ResourcePickerScreen}
 *       — the same searchable picker used by the visual editor;</li>
 *   <li>selecting a resource stores it (its icon + name show in the bar);</li>
 *   <li>clicking Copy writes the selected resource's SFML id to the system clipboard.</li>
 * </ul>
 * The selection persists across screen rebuilds via static fields.
 */
@EventBusSubscriber(modid = SFMGui.MOD_ID, value = Dist.CLIENT)
public final class SfmlEditorResourceBar {
    public static final Loc COPY = new Loc("gui.sfmgui.editor_bar.copy", "Copy");
    public static final Loc PICK_HINT = new Loc("gui.sfmgui.editor_bar.pick_hint", "Click to pick a resource");
    public static final Loc COPIED = new Loc("gui.sfmgui.editor_bar.copied", "Copied: %s");

    // SFM text-editor screen class names (matched reflectively; SFM stays compileOnly-free here).
    private static final String V1 = "ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1";
    private static final String V2 = "ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2";

    // Persisted selection (survives screen rebuilds / reopen while the game runs).
    private static @Nullable String selectedId = null;

    // Bar geometry.
    private static final int BAR_H = 20;
    private static final int ICON = 16;
    private static final int COPY_W = 44;
    private static final int GAP = 4;
    private static final int BAR_W = 360;

    // Toast shown briefly after copying.
    private static long toastUntil = 0L;
    private static @Nullable String toastText = null;

    private SfmlEditorResourceBar() {
    }

    /** True when the screen is one of SFM's program text editors. */
    private static boolean isEditorScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            String n = c.getName();
            if (n.equals(V1) || n.equals(V2)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isV2(Screen screen) {
        for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().equals(V2)) {
                return true;
            }
        }
        return false;
    }

    /** Screen-space [x, y] top-left of the bar for the given editor screen. */
    private static int[] barPos(Screen screen) {
        int x = screen.width / 2 - BAR_W / 2;
        // V1's text area starts at height/2 - 110 (room above); V2 draws text from y=0.
        // Put the bar just above V1's text area, or at the very top for V2 (overlay).
        int y = isV2(screen) ? 2 : (screen.height / 2 - 110 - BAR_H - 4);
        if (y < 2) {
            y = 2;
        }
        return new int[]{x, y};
    }

    // Sub-rects within the bar (all screen-space).
    private static int iconX(int bx) {
        return bx;
    }

    private static int nameX(int bx) {
        return bx + ICON + GAP;
    }

    private static int nameW() {
        return BAR_W - ICON - GAP - GAP - COPY_W;
    }

    private static int copyX(int bx) {
        return bx + BAR_W - COPY_W;
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!isEditorScreen(screen)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int[] p = barPos(screen);
        int bx = p[0], by = p[1];
        int mx = event.getMouseX(), my = event.getMouseY();

        // background strip
        // Fully opaque strip so it cleanly overlays whatever is behind it (V2 draws
        // its code text from y=0, so an opaque bar avoids visual bleed-through).
        g.fill(bx - 2, by - 2, bx + BAR_W + 2, by + BAR_H + 2, 0xFF101014);

        ResourceIndex.Entry entry = selectedId == null ? null : ResourceIndex.lookup(selectedId);

        // icon box
        int ix = iconX(bx);
        boolean iconHover = inRect(mx, my, ix, by + 2, ICON, ICON);
        g.fill(ix, by + 2, ix + ICON, by + 2 + ICON, iconHover ? 0xFF3A3A44 : 0xFF26262E);
        if (entry != null) {
            ResourceIndex.renderIcon(g, font, entry, ix, by + 2);
        }

        // name field
        int nx = nameX(bx), nw = nameW();
        boolean nameHover = inRect(mx, my, nx, by + 2, nw, ICON);
        g.fill(nx, by + 2, nx + nw, by + 2 + ICON, nameHover ? 0xFF2E2E38 : 0xFF1E1E24);
        String label;
        int labelColor;
        if (entry != null) {
            label = entry.displayName();
            labelColor = 0xFFE0E0E0;
        } else {
            label = PICK_HINT.getComponent().getString();
            labelColor = 0xFF808088;
        }
        g.drawString(font, font.plainSubstrByWidth(label, nw - 6), nx + 4, by + 6, labelColor, false);

        // copy button
        int cxp = copyX(bx);
        boolean copyEnabled = selectedId != null;
        boolean copyHover = copyEnabled && inRect(mx, my, cxp, by + 2, COPY_W, ICON);
        g.fill(cxp, by + 2, cxp + COPY_W, by + 2 + ICON, copyHover ? 0xFF3A5A9A : (copyEnabled ? 0xFF2A3A6A : 0xFF2A2A30));
        g.drawCenteredString(font, COPY.getComponent(), cxp + COPY_W / 2, by + 6,
                copyEnabled ? 0xFFFFFFFF : 0xFF707078);

        // tooltip: show full SFML id on hovering the name/icon
        if ((iconHover || nameHover) && entry != null) {
            g.renderTooltip(font,
                    java.util.List.of(Component.literal(entry.displayName()), Component.literal(entry.sfmlId())),
                    java.util.Optional.empty(), mx, my);
        }

        // transient "copied" toast
        if (toastText != null && System.currentTimeMillis() < toastUntil) {
            g.drawString(font, toastText, bx, by + BAR_H + 4, 0xFF7CFC7C, false);
        } else {
            toastText = null;
        }
    }

    @SubscribeEvent
    public static void onMousePressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!isEditorScreen(screen) || event.getButton() != 0) {
            return;
        }
        double mx = event.getMouseX(), my = event.getMouseY();
        int[] p = barPos(screen);
        int bx = p[0], by = p[1];

        // icon or name field -> open the shared resource picker
        if (inRect(mx, my, iconX(bx), by + 2, ICON, ICON)
                || inRect(mx, my, nameX(bx), by + 2, nameW(), ICON)) {
            ResourcePickerScreen picker = new ResourcePickerScreen(screen, id -> {
                selectedId = id;
            });
            Minecraft.getInstance().setScreen(picker);
            event.setCanceled(true);
            return;
        }

        // copy button -> write selected id to clipboard
        if (selectedId != null && inRect(mx, my, copyX(bx), by + 2, COPY_W, ICON)) {
            try {
                Minecraft.getInstance().keyboardHandler.setClipboard(selectedId);
                toastText = Loc.tr(COPIED.key(), selectedId);
                toastUntil = System.currentTimeMillis() + 1600L;
            } catch (Throwable t) {
                SFMGui.LOGGER.error("Failed to copy resource id to clipboard", t);
            }
            event.setCanceled(true);
        }
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
