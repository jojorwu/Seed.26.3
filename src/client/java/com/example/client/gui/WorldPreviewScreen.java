package com.example.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

import java.util.Random;
import java.util.function.Consumer;

public class WorldPreviewScreen extends Screen {
    private final CreateWorldScreen parent;
    private final Consumer<String> seedConsumer;
    private EditBox seedInput;
    private String currentSeed;

    // Viewport camera parameters
    private double offsetX = 0;
    private double offsetZ = 0;
    private double zoom = 1.0; // scale factor
    private boolean isDragging = false;
    private double lastMouseX;
    private double lastMouseY;

    // Rendering terrain parameters
    private static final int MAP_SIZE = 48; // grid radius/size
    private int[][] heightMap = new int[MAP_SIZE][MAP_SIZE];
    private int[][] colorMap = new int[MAP_SIZE][MAP_SIZE];

    public WorldPreviewScreen(CreateWorldScreen parent, String initialSeed, Consumer<String> seedConsumer) {
        super(Component.literal("3D World Preview"));
        this.parent = parent;
        this.currentSeed = initialSeed != null ? initialSeed : "";
        this.seedConsumer = seedConsumer;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 100;
        int buttonHeight = 20;

        // Seed input box at top
        this.seedInput = new EditBox(this.font, this.width / 2 - 120, 15, 160, 20, Component.literal("Seed"));
        this.seedInput.setValue(this.currentSeed);
        this.addRenderableWidget(this.seedInput);

        // Regenerate Button
        this.addRenderableWidget(Button.builder(Component.literal("Regenerate"), button -> {
            this.currentSeed = this.seedInput.getValue();
            generateTerrainData();
        }).bounds(this.width / 2 + 45, 15, 75, 20).build());

        // Bottom control buttons: Apply & Cancel
        this.addRenderableWidget(Button.builder(Component.literal("Apply Seed & Return"), button -> {
            applySeedAndClose();
        }).bounds(this.width / 2 - 155, this.height - 30, 150, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
        }).bounds(this.width / 2 + 5, this.height - 30, 150, buttonHeight).build());

        generateTerrainData();
    }

    private void applySeedAndClose() {
        this.currentSeed = this.seedInput.getValue();
        if (this.seedConsumer != null) {
            this.seedConsumer.accept(this.currentSeed);
        }
        // Return to parent screen
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    public String getCurrentSeed() {
        return currentSeed;
    }

    private void generateTerrainData() {
        long seedValue;
        try {
            seedValue = Long.parseLong(currentSeed);
        } catch (NumberFormatException e) {
            seedValue = currentSeed.hashCode();
        }

        Random random = new Random(seedValue);
        double freq1 = 0.05;
        double freq2 = 0.02;

        for (int x = 0; x < MAP_SIZE; x++) {
            for (int z = 0; z < MAP_SIZE; z++) {
                double worldX = (x - MAP_SIZE / 2.0);
                double worldZ = (z - MAP_SIZE / 2.0);

                double n1 = Math.sin(worldX * freq1 + seedValue % 100) * Math.cos(worldZ * freq1 + seedValue % 100);
                double n2 = Math.sin(worldX * freq2) * Math.sin(worldZ * freq2);

                int height = (int) (12 + n1 * 8 + n2 * 12);
                if (height < 2) height = 2;
                heightMap[x][z] = height;

                // Color based on height and noise (representing biomes / blocks)
                if (height < 7) {
                    colorMap[x][z] = 0x3366BB; // Water (Blue)
                } else if (height < 9) {
                    colorMap[x][z] = 0xDDCC88; // Sand (Yellowish)
                } else if (height < 22) {
                    colorMap[x][z] = 0x55AA44; // Grass (Green)
                } else if (height < 27) {
                    colorMap[x][z] = 0x888888; // Mountain Stone (Gray)
                } else {
                    colorMap[x][z] = 0xFFFFFF; // Snow (White)
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY > 45 && mouseY < this.height - 35) {
            this.isDragging = true;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging) {
            this.offsetX += (mouseX - this.lastMouseX);
            this.offsetZ += (mouseY - this.lastMouseY);
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            if (scrollY > 0) {
                this.zoom = Math.min(this.zoom * 1.15, 3.0);
            } else {
                this.zoom = Math.max(this.zoom / 1.15, 0.4);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Render 3D Isometric Map
        render3DMap(guiGraphics);

        // Render UI overlay and widgets
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Overlay text instructions
        guiGraphics.drawCenteredString(this.font, "3D World Terrain Preview", this.width / 2, 40, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, "Left Click + Drag: Pan | Mouse Wheel: Zoom", this.width / 2, this.height - 45, 0xAAAAAA);
    }

    private void render3DMap(GuiGraphics guiGraphics) {
        int centerX = (int) (this.width / 2 + offsetX);
        int centerY = (int) (this.height / 2 + offsetZ + 20);

        double tileWidth = 10 * zoom;
        double tileHeight = 5 * zoom;
        double blockHeightScale = 3 * zoom;

        // Render back to front for proper isometric depth sorting
        for (int sum = 0; sum < MAP_SIZE * 2; sum++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int z = sum - x;
                if (z < 0 || z >= MAP_SIZE) continue;

                int h = heightMap[x][z];
                int baseColor = colorMap[x][z];

                // Isometric projection coordinates
                double isoX = centerX + (x - z) * (tileWidth / 2.0);
                double isoY = centerY + (x + z) * (tileHeight / 2.0) - (h * blockHeightScale);

                int drawX = (int) isoX;
                int drawY = (int) isoY;
                int sizeX = Math.max(1, (int) tileWidth);
                int sizeY = Math.max(1, (int) tileHeight);

                // Top Face
                guiGraphics.fill(drawX - sizeX / 2, drawY, drawX + sizeX / 2, drawY + sizeY, 0xFF000000 | baseColor);

                // Front/Side shading
                if (h > 1) {
                    int sideColor = shadeColor(baseColor, 0.7f);
                    int sideHeight = (int) (h * blockHeightScale);
                    guiGraphics.fill(drawX - sizeX / 2, drawY + sizeY, drawX + sizeX / 2, drawY + sizeY + sideHeight, 0xFF000000 | sideColor);
                }
            }
        }
    }

    private int shadeColor(int rgb, float factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }
}
