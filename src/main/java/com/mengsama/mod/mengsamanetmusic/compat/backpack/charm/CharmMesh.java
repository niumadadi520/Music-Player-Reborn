package com.mengsama.mod.mengsamanetmusic.compat.backpack.charm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

 
final class CharmMesh {
    private static final ResourceLocation MODEL = new ResourceLocation("mengsamanetmusic", "models/charm/pink_walkman_charm.json");
    final ResourceLocation texture;
    private final Map<String, Part> parts;

    private CharmMesh(ResourceLocation texture, Map<String, Part> parts) {
        this.texture = texture;
        this.parts = parts;
    }

    static CharmMesh load(ResourceManager resources) throws IOException {
        JsonObject root;
        try (Reader reader = resources.getResourceOrThrow(MODEL).openAsReader()) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        ResourceLocation texture = new ResourceLocation(root.get("texture").getAsString());
        JsonArray size = root.getAsJsonArray("texture_size");
        float width = size.get(0).getAsFloat(), height = size.get(1).getAsFloat();
        if (width <= 0 || height <= 0) {
            throw new IOException("Charm texture dimensions must be positive");
        }
        Map<String, Part> parts = new LinkedHashMap<>();
        for (JsonElement element : root.getAsJsonArray("parts")) {
            JsonObject part = element.getAsJsonObject();
            String name = part.get("name").getAsString();
            String parent = part.has("parent") && !part.get("parent").isJsonNull() ? part.get("parent").getAsString() : null;
            Vector3f pivot = vector(part, "pivot");
            List<Quad> quads = new ArrayList<>();
            for (JsonElement cube : part.getAsJsonArray("cubes")) {
                bakeCube(cube.getAsJsonObject(), width, height, quads);
            }
            if (parts.put(name, new Part(name, parent, pivot, List.copyOf(quads))) != null) {
                throw new IOException("Duplicate charm part: " + name);
            }
        }
        for (Part part : parts.values()) {
            String parent = part.parent;
            int depth = 0;
            while (parent != null) {
                if (++depth > parts.size() || !parts.containsKey(parent)) {
                    throw new IOException("Invalid charm part hierarchy at " + part.name);
                }
                parent = parts.get(parent).parent;
            }
        }
        return new CharmMesh(texture, parts);
    }

    void render(PoseStack stack, VertexConsumer vertices, int light, CharmDynamics.Pose pose) {
        for (Part part : parts.values()) {
            if (part.parent == null) {
                renderPart(part, stack, vertices, light, pose);
            }
        }
    }

    private void renderPart(Part part, PoseStack stack, VertexConsumer vertices, int light, CharmDynamics.Pose angles) {
        stack.pushPose();
        double pitch = 0, yaw = 0, roll = 0;
        if ("chain".equals(part.name)) {
            pitch = angles.chainPitch();
            roll = angles.chainRoll();
        } else if ("pendant".equals(part.name)) {
            pitch = angles.pendantPitch();
            yaw = angles.pendantYaw();
            roll = angles.pendantRoll();
        }
        stack.translate(part.pivot.x, part.pivot.y, part.pivot.z);
        stack.mulPose(rotation((float) pitch, (float) yaw, (float) roll));
        stack.translate(-part.pivot.x, -part.pivot.y, -part.pivot.z);
        PoseStack.Pose matrix = stack.last();
        for (Quad quad : part.quads) {
            for (int i = 0; i < 4; i++) {
                Vector3f position = quad.positions[i];
                vertices.vertex(matrix.pose(), position.x, position.y, position.z)
                        .color(255, 255, 255, 255).uv(quad.uv[i * 2], quad.uv[i * 2 + 1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(matrix.normal(), quad.normal.x, quad.normal.y, quad.normal.z).endVertex();
            }
        }
        for (Part child : parts.values()) {
            if (part.name.equals(child.parent)) {
                renderPart(child, stack, vertices, light, angles);
            }
        }
        stack.popPose();
    }

    private static void bakeCube(JsonObject cube, float width, float height, List<Quad> output) {
        Vector3f from = vector(cube, "from"), to = vector(cube, "to");
        Vector3f pivot = vector(cube, "pivot"), angles = vector(cube, "rotation");
        Quaternionf rotation = rotation(angles.x, angles.y, angles.z);
        float x0 = from.x, y0 = from.y, z0 = from.z, x1 = to.x, y1 = to.y, z1 = to.z;
        JsonObject faces = cube.getAsJsonObject("faces");
        if (faces == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
            float[] positions;
            Vector3f normal;
             
            switch (face.getKey()) {
                case "north" -> { positions = new float[]{x1,y1,z0, x1,y0,z0, x0,y0,z0, x0,y1,z0}; normal = new Vector3f(0,0,-1); }
                case "south" -> { positions = new float[]{x0,y1,z1, x0,y0,z1, x1,y0,z1, x1,y1,z1}; normal = new Vector3f(0,0,1); }
                case "east" -> { positions = new float[]{x1,y1,z1, x1,y0,z1, x1,y0,z0, x1,y1,z0}; normal = new Vector3f(1,0,0); }
                case "west" -> { positions = new float[]{x0,y1,z0, x0,y0,z0, x0,y0,z1, x0,y1,z1}; normal = new Vector3f(-1,0,0); }
                case "up" -> { positions = new float[]{x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0}; normal = new Vector3f(0,1,0); }
                case "down" -> { positions = new float[]{x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1}; normal = new Vector3f(0,-1,0); }
                default -> throw new IllegalArgumentException("Unknown charm face: " + face.getKey());
            }
            JsonArray uv = face.getValue().getAsJsonArray();
            float u0 = uv.get(0).getAsFloat() / width, v0 = uv.get(1).getAsFloat() / height;
            float u1 = uv.get(2).getAsFloat() / width, v1 = uv.get(3).getAsFloat() / height;
            Vector3f[] corners = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                corners[i] = new Vector3f(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
                corners[i].sub(pivot).rotate(rotation).add(pivot);
            }
            normal.rotate(rotation);
            output.add(new Quad(corners, normal, new float[]{u0,v0, u0,v1, u1,v1, u1,v0}));
        }
    }

    private static Quaternionf rotation(float x, float y, float z) {
        float radians = (float) (Math.PI / 180.0);
        return new Quaternionf().rotationZYX(z * radians, y * radians, x * radians);
    }

    private static Vector3f vector(JsonObject object, String key) {
        if (!object.has(key)) {
            return new Vector3f();
        }
        JsonArray array = object.getAsJsonArray(key);
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    private record Part(String name, String parent, Vector3f pivot, List<Quad> quads) {}
    private record Quad(Vector3f[] positions, Vector3f normal, float[] uv) {}
}
