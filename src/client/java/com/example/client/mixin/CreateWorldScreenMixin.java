package com.example.client.mixin;

import com.example.client.gui.WorldPreviewScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {

    @Shadow
    public abstract WorldCreationUiState getUiState();

    protected CreateWorldScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addWorldPreviewButton(CallbackInfo ci) {
        // Add "3D World Preview" button in top-right area of CreateWorldScreen
        int buttonWidth = 120;
        int buttonHeight = 20;
        int x = this.width - buttonWidth - 10;
        int y = 6;

        this.addRenderableWidget(Button.builder(Component.literal("3D World Preview"), button -> {
            if (this.minecraft != null) {
                CreateWorldScreen currentScreen = (CreateWorldScreen) (Object) this;
                WorldCreationUiState uiState = this.getUiState();

                this.minecraft.setScreen(new WorldPreviewScreen(currentScreen, uiState));
            }
        }).bounds(x, y, buttonWidth, buttonHeight).build());
    }
}
