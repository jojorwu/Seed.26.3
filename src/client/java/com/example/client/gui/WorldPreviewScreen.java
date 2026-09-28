package com.example.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

import java.util.Random;

public class WorldPreviewScreen extends Screen {
    private final CreateWorldScreen parent;
    private final WorldCreationUiState uiState;
    private EditBox seedInput;
    private String currentSeed;

    // Customization Settings
    private int seaLevel = 7;           // Range: 2 - 15
    private int mountainScale = 12;     // Range: 4 - 25
    private float terrainFrequency = 0.05f; // Range: 0.02 - 0.10
    private WorldCreationUiState.WorldTypeEntry selectedWorldType;

    // Viewport camera parameters
    private double offsetX = -60;
    private double offsetZ = 0;
    private double zoom = 1.0; // scale factor
    private boolean isDragging = false;
    private double lastMouseX;
    private double lastMouseY;

    // Rendering terrain parameters
    private static final int MAP_SIZE = 48; // grid radius/size
    private int[][] heightMap = new int[MAP_SIZE][MAP_SIZE];
    private int[][] colorMap = new int[MAP_SIZE][MAP_SIZE];

    public WorldPreviewScreen(CreateWorldScreen parent, WorldCreationUiState uiState) {
        super(Component.literal("3D World Preview & Customization"));
        this.parent = parent;
        this.uiState = uiState;
        this.currentSeed = (uiState != null && uiState.getSeed() != null) ? uiState.getSeed() : "";
        if (uiState != null) {
            this.selectedWorldType = uiState.getWorldType();
        }
    }

    @Override
    protected void init() {
        super.init();

        int buttonHeight = 20;

        // Top bar: Seed input & Regenerate
        this.seedInput = new EditBox(this.font, 10, 15, 140, 20, Component.literal("Seed"));
        this.seedInput.setValue(this.currentSeed);
        this.addRenderableWidget(this.seedInput);

        this.addRenderableWidget(Button.builder(Component.literal("Regenerate"), button -> {
            this.currentSeed = this.seedInput.getValue();
            generateTerrainData();
        }).bounds(155, 15, 80, 20).build());

        // Settings Panel Controls (Left Sidebar)
        int panelY = 50;

        // World Type Button
        String typeName = selectedWorldType != null ? selectedWorldType.describePreset().getString() : "Normal";
        this.addRenderableWidget(Button.builder(Component.literal("World Type: " + typeName), button -> {
            if (uiState != null && !uiState.isFlatWorld()) {
                // Toggle between World Types
                var altType = uiState.getAltPreset();
                if (altType != null) {
                    uiState.setWorldType(altType);
                    selectedWorldType = altType;
                    button.setMessage(Component.literal("World Type: " + selectedWorldType.describePreset().getString()));
                }
            }
            generateTerrainData();
        }).bounds(10, panelY, 150, buttonHeight).build());

        panelY += 35;
        // Sea Level Adjust Button
        this.addRenderableWidget(Button.builder(Component.literal("Sea Level: " + seaLevel), button -> {
            seaLevel = (seaLevel >= 14) ? 3 : seaLevel + 2;
            button.setMessage(Component.literal("Sea Level: " + seaLevel));
            generateTerrainData();
        }).bounds(10, panelY, 150, buttonHeight).build());

        panelY += 35;
        // Mountain Height Button
        this.addRenderableWidget(Button.builder(Component.literal("Mountains: " + mountainScale), button -> {
            mountainScale = (mountainScale >= 22) ? 6 : mountainScale + 4;
            button.setMessage(Component.literal("Mountains: " + mountainScale));
            generateTerrainData();
        }).bounds(10, panelY, 150, buttonHeight).build());

        panelY += 35;
        // Terrain Scale Button
        this.addRenderableWidget(Button.builder(Component.literal("Terrain Scale: " + String.format("%.2f", terrainFrequency)), button -> {
            terrainFrequency = (terrainFrequency >= 0.09f) ? 0.03f : terrainFrequency + 0.02f;
            button.setMessage(Component.literal("Terrain Scale: " + String.format("%.2f", terrainFrequency)));
            generateTerrainData();
        }).bounds(10, panelY, 150, buttonHeight).build());

        // Bottom control buttons: Apply & Cancel
        this.addRenderableWidget(Button.builder(Component.literal("Apply & Return"), button -> {
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
        if (this.uiState != null) {
            this.uiState.setSeed(this.currentSeed);
            if (this.selectedWorldType != null) {
                this.uiState.setWorldType(this.selectedWorldType);
            }
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
        boolean isFlat = uiState != null && uiState.isFlatWorld();

        for (int x = 0; x < MAP_SIZE; x++) {
            for (int z = 0; z < MAP_SIZE; z++) {
                double worldX = (x - MAP_SIZE / 2.0);
                double worldZ = (z - MAP_SIZE / 2.0);

                if (isFlat) {
                    int height = 5;
                    heightMap[x][z] = height;
                    colorMap[x][z] = 0x55AA44; // Grass
                    continue;
                }

                double n1 = Math.sin(worldX * terrainFrequency + seedValue % 100) * Math.cos(worldZ * terrainFrequency + seedValue % 100);
                double n2 = Math.sin(worldX * (terrainFrequency * 0.5)) * Math.sin(worldZ * (terrainFrequency * 0.5));

                int height = (int) (seaLevel + 2 + n1 * (mountainScale * 0.6) + n2 * mountainScale);
                if (height < 2) height = 2;
                heightMap[x][z] = height;

                // Color based on height and seaLevel
                if (height <= seaLevel) {
                    colorMap[x][z] = 0x3366BB; // Water
                } else if (height <= seaLevel + 2) {
                    colorMap[x][z] = 0xDDCC88; // Sand / Coast
                } else if (height <= seaLevel + 15) {
                    colorMap[x][z] = 0x55AA44; // Grass
                } else if (height <= seaLevel + 22) {
                    colorMap[x][z] = 0x888888; // Stone
                } else {
                    colorMap[x][z] = 0xFFFFFF; // Snow
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX > 170 && mouseY > 45 && mouseY < this.height - 35) {
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

        // Sidebar background panel
        guiGraphics.fill(0, 0, 165, this.height, 0xAA000000);

        // Render UI overlay and widgets
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Sidebar Header
        guiGraphics.drawString(this.font, "World Settings", 10, 40, 0xFFFF55);

        // Overlay text instructions
        guiGraphics.drawCenteredString(this.font, "3D World Terrain Preview & Customization", this.width / 2 + 80, 15, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, "Left Click + Drag: Pan | Mouse Wheel: Zoom", this.width / 2 + 80, this.height - 45, 0xAAAAAA);
    }

    private void render3DMap(GuiGraphics guiGraphics) {
        int centerX = (int) (this.width / 2 + 50 + offsetX);
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
