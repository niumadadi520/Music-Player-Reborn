package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.api.QqLoginService;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicLong;

public class QqLoginScreen extends Screen {
    private static final AtomicLong TEXTURE_IDS = new AtomicLong();
    private final Screen parent;
    private final QqLoginBackend backend;
    private final QqLoginRequestState requests = new QqLoginRequestState();
    private final ResourceLocation qrLocation = new ResourceLocation(MengSamaNetMusic.MOD_ID, "qq_login_qr/" + TEXTURE_IDS.incrementAndGet());
    private DynamicTexture qrTexture;
    private int qrWidth, qrHeight;
    private QqLoginService.LoginState state = QqLoginService.LoginState.IDLE;
    private QqLoginLayout layout;
    private AbstractButton refreshButton;
    private long serviceGeneration;

    public QqLoginScreen(Screen parent) { this(parent, QqLoginBackend.LIVE); }

    QqLoginScreen(Screen parent, QqLoginBackend backend) {
        super(Component.translatable("gui.mengsamanetmusic.qq_login.title"));
        this.parent = parent;
        this.backend = backend;
    }

    @Override protected void init() {
        layout = QqLoginLayout.fit(width, height);
        refreshButton = addRenderableWidget(QqLoginSkin.button(layout.button(0),
                Component.translatable("gui.mengsamanetmusic.qq_login.refresh"), this::fetchQr));
        addRenderableWidget(QqLoginSkin.button(layout.button(1),
                Component.translatable("gui.mengsamanetmusic.qq_login.logout"), this::logout));
        addRenderableWidget(QqLoginSkin.button(layout.button(2),
                Component.translatable("gui.mengsamanetmusic.back"), this::onClose));
         
        if (!requests.active()) {
            requests.open();
            serviceGeneration = backend.generation();
            if (backend.hasCredential()) setState(QqLoginService.LoginState.SUCCESS);
            else fetchQr();
        }
        refreshButton.active = !requests.fetching();
    }

    private boolean accepts(long request, long service) {
        return requests.accepts(request) && backend.isCurrent(service);
    }

    private void fetchQr() {
        long request = requests.beginFetch();
        if (request < 0) return;
        refreshButton.active = false;
        releaseQr();
        setState(QqLoginService.LoginState.FETCHING_QR);
        backend.reset();
        var future = backend.fetchQrCode();
        long service = serviceGeneration = backend.generation();
        backend.setStateListener(next -> Minecraft.getInstance().execute(() -> {
            if (accepts(request, service)) setState(next);
        }));
        future.whenComplete((bytes, error) -> Minecraft.getInstance().execute(() -> {
            if (!accepts(request, service)) return;
            if (error != null) {
                requests.finishFetch(request, false, Util.getMillis());
                setState(QqLoginService.LoginState.FAILED);
                refreshButton.active = true;
                return;
            }
            NativeImage image = null;
            DynamicTexture decoded = null;
            try {
                image = NativeImage.read(new ByteArrayInputStream(bytes));
                if (image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 1024 || image.getHeight() > 1024)
                    throw new IllegalArgumentException("Unsupported QR image size");
                qrWidth = image.getWidth();
                qrHeight = image.getHeight();
                decoded = new DynamicTexture(image);
                image = null;
                decoded.setFilter(false, false);
                Minecraft.getInstance().getTextureManager().register(qrLocation, decoded);
                qrTexture = decoded;
                decoded = null;
                requests.finishFetch(request, true, Util.getMillis());
                setState(QqLoginService.LoginState.WAITING_SCAN);
            } catch (Exception failure) {
                if (decoded != null) decoded.close();
                if (image != null) image.close();
                requests.finishFetch(request, false, Util.getMillis());
                MengSamaNetMusic.LOGGER.warn("Failed to decode QQ login QR image", failure);
                setState(QqLoginService.LoginState.FAILED);
            }
            refreshButton.active = true;
        }));
    }

    private void logout() {
        requests.invalidate();
        backend.reset();
        serviceGeneration = backend.generation();
        backend.setStateListener(null);
        backend.logout();
        setState(QqLoginService.LoginState.IDLE);
        refreshButton.active = true;
    }

    @Override public void tick() {
        long request = requests.beginPoll(Util.getMillis());
        if (request < 0) return;
        long service = serviceGeneration;
        backend.pollLogin().whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
            if (!accepts(request, service)) return;
            var next = error == null && result != null ? result : QqLoginService.LoginState.FAILED;
            boolean retry = next == QqLoginService.LoginState.WAITING_SCAN || next == QqLoginService.LoginState.VERIFYING
                    || next == QqLoginService.LoginState.AUTHORIZING || next == QqLoginService.LoginState.LOGGING_IN;
            requests.finishPoll(request, retry, Util.getMillis());
            setState(next);
        }));
    }

    private void setState(QqLoginService.LoginState next) {
        state = next;
        if (next == QqLoginService.LoginState.SUCCESS || next == QqLoginService.LoginState.QR_EXPIRED
                || next == QqLoginService.LoginState.FAILED || next == QqLoginService.LoginState.IDLE) releaseQr();
    }

    private void releaseQr() {
        if (qrTexture == null) return;
        qrTexture = null;
        qrWidth = qrHeight = 0;
        Minecraft.getInstance().getTextureManager().release(qrLocation);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        QqLoginSkin.renderPanel(graphics, layout);
        var titleBounds = layout.title();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(title.getString(), titleBounds.width()),
                titleBounds.x() + titleBounds.width() / 2, titleBounds.y(), QqLoginSkin.title());
        var subtitle = layout.subtitle();
        graphics.drawCenteredString(font, "使用手机 QQ 扫码登录", subtitle.x() + subtitle.width() / 2, subtitle.y(), QqLoginSkin.secondary());
        if (qrTexture != null) {
            var target = QqLoginLayout.fitImage(layout.qrImageArea(), qrWidth, qrHeight);
            graphics.setColor(1, 1, 1, 1);
            graphics.blit(qrLocation, target.x(), target.y(), target.width(), target.height(), 0, 0, qrWidth, qrHeight, qrWidth, qrHeight);
        } else {
            var blank = layout.qrWhite();
            String text = state == QqLoginService.LoginState.SUCCESS ? "已登录" :
                    state == QqLoginService.LoginState.FETCHING_QR ? "正在获取二维码…" : "点击刷新获取二维码";
            graphics.drawCenteredString(font, font.plainSubstrByWidth(text, blank.width() - 8),
                    blank.x() + blank.width() / 2, blank.y() + (blank.height() - 8) / 2, QqLoginSkin.qrText());
        }
        var status = layout.status();
        String label = font.plainSubstrByWidth(stateText().getString(), status.width());
        graphics.drawCenteredString(font, label, status.x() + status.width() / 2, status.y(),
                com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.custom() ? QqLoginSkin.text() : (state == QqLoginService.LoginState.SUCCESS ? 0xFF477452 : state == QqLoginService.LoginState.FAILED ? 0xFFA0494A : QqLoginSkin.text()));
        if (status.height() >= 23) {
            graphics.drawCenteredString(font, "悬停状态查看详情", status.x() + status.width() / 2, status.y() + 12, QqLoginSkin.secondary());
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (status.contains(mouseX, mouseY)) {
            String detail = stateText().getString() + "\n" + backend.failureDetail() + "\n" + backend.safeSummary();
            graphics.renderTooltip(font, font.split(Component.literal(detail), Math.max(100, Math.min(260, width - 24))), mouseX, mouseY);
        }
    }

    private Component stateText() {
        if (state == QqLoginService.LoginState.FAILED) return Component.literal(backend.failureDetail());
        String suffix = switch (state) {
            case FETCHING_QR -> "loading";
            case WAITING_SCAN -> "waiting";
            case VERIFYING -> "verifying";
            case AUTHORIZING -> "oauth";
            case LOGGING_IN -> "music_login";
            case SUCCESS -> "success";
            case QR_EXPIRED -> "expired";
            case FAILED -> "failed";
            default -> "idle";
        };
        return Component.translatable("gui.mengsamanetmusic.qq_login." + suffix);
    }

    @Override public void onClose() { PlayerScreenNavigation.returnTo(parent); }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void removed() {
        requests.close();
        if (backend.isCurrent(serviceGeneration)) {
            backend.setStateListener(null);
            backend.reset();
        }
        releaseQr();
        super.removed();
    }
}
