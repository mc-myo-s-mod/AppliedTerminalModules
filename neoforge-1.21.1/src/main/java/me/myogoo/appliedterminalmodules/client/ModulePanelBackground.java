package me.myogoo.appliedterminalmodules.client;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.TextureTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

final class ModulePanelBackground {
    private static final Blitter PANEL = Blitter.texture("guis/extra_panels.png", 128, 128);

    private ModulePanelBackground() {}

    static void draw(GuiGraphics graphics, Rect2i bounds, Component title) {
        int left = bounds.getX(), top = bounds.getY();
        int width = bounds.getWidth(), height = bounds.getHeight();
        PANEL.src(2, 2, 1, 1).dest(left + 5, top + 5, width - 10, height - 10).blit(graphics);
        PANEL.src(1, 0, 16, 5).dest(left + 5, top, width - 10, 5).blit(graphics);
        PANEL.src(1, 22, 16, 5).dest(left + 5, top + height - 5, width - 10, 5).blit(graphics);
        PANEL.src(17, 5, 5, 18).dest(left + width - 5, top + 5, 5, height - 10).blit(graphics);
        PANEL.copy().transform(TextureTransform.MIRROR_H).dest(left, top + 5, 5, height - 10).blit(graphics);
        PANEL.src(17, 0, 5, 5).dest(left + width - 5, top).blit(graphics);
        PANEL.copy().transform(TextureTransform.MIRROR_H).dest(left, top).blit(graphics);
        PANEL.src(17, 22, 5, 5).dest(left + width - 5, top + height - 5).blit(graphics);
        PANEL.copy().transform(TextureTransform.MIRROR_H).dest(left, top + height - 5).blit(graphics);
        graphics.fill(left + 9, top + 2, left + width - 9, top + 3, 0xff777787);
        graphics.fill(left + 9, top + 3, left + width - 9, top + 4, 0xffeeeeff);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, font.substrByWidth(title, width - 12).getString(), left + 6, top + 7, 0xff404040, false);
    }
}
