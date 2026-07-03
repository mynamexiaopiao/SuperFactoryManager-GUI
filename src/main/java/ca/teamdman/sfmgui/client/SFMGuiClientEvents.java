package ca.teamdman.sfmgui.client;

import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfmgui.SFMGui;
import ca.teamdman.sfmgui.net.OpenEditorHelper;
import ca.teamdman.sfmgui.net.PullLabelsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Injects addon buttons ("Visual Edit" and "Pull Labels") onto SFM's manager
 * screen without modifying SFM. Buttons are drawn and click-handled entirely
 * from screen events, above the manager GUI's top-left corner.
 */
@EventBusSubscriber(modid = SFMGui.MOD_ID, value = Dist.CLIENT)
public final class SFMGuiClientEvents {
    public static final Loc VISUAL_EDIT = new Loc("gui.sfmgui.manager.visual_edit", "Visual Edit");
    public static final Loc PULL_LABELS = new Loc("gui.sfmgui.manager.pull_labels", "Pull Labels");

    private static final int BTN_W = 70;
    private static final int BTN_H = 14;
    private static final int GAP = 2;

    private SFMGuiClientEvents() {
    }

    private static int rowY(AbstractContainerScreen<?> s) {
        return s.getGuiTop() - BTN_H - 2;
    }

    private static int visualX(AbstractContainerScreen<?> s) {
        return s.getGuiLeft();
    }

    private static int pullX(AbstractContainerScreen<?> s) {
        return s.getGuiLeft() + BTN_W + GAP;
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ManagerScreen ms)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int y = rowY(ms);
        drawButton(g, visualX(ms), y, VISUAL_EDIT.getComponent().getString(),
                inside(event.getMouseX(), event.getMouseY(), visualX(ms), y));
        drawButton(g, pullX(ms), y, PULL_LABELS.getComponent().getString(),
                inside(event.getMouseX(), event.getMouseY(), pullX(ms), y));
    }

    private static void drawButton(GuiGraphics g, int x, int y, String text, boolean hover) {
        g.fill(x, y, x + BTN_W, y + BTN_H, 0xFF000000);
        g.fill(x + 1, y + 1, x + BTN_W - 1, y + BTN_H - 1, hover ? 0xFF5A5A5A : 0xFF3A3A3A);
        g.drawCenteredString(Minecraft.getInstance().font, text, x + BTN_W / 2, y + 3, 0xFFFFFFFF);
    }

    @SubscribeEvent
    public static void onMousePressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof ManagerScreen ms)) {
            return;
        }
        if (event.getButton() != 0) {
            return;
        }
        double mx = event.getMouseX(), my = event.getMouseY();
        int y = rowY(ms);
        if (inside(mx, my, visualX(ms), y)) {
            try {
                OpenEditorHelper.open(ms);
            } catch (Throwable t) {
                SFMGui.LOGGER.error("Failed to open visual editor", t);
            }
            event.setCanceled(true);
        } else if (inside(mx, my, pullX(ms), y)) {
            try {
                PacketDistributor.sendToServer(new PullLabelsPayload(ms.getMenu().MANAGER_POSITION));
            } catch (Throwable t) {
                SFMGui.LOGGER.error("Failed to pull labels", t);
            }
            event.setCanceled(true);
        }
    }

    private static boolean inside(double mx, double my, int x, int y) {
        return mx >= x && mx <= x + BTN_W && my >= y && my <= y + BTN_H;
    }
}
