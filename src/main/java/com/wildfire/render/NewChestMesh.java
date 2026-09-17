/*
 * Wildfire's Female Gender Mod is a female gender mod created for Minecraft.
 * Copyright (C) 2023-present WildfireRomeo
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wildfire.render;

import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small, dependency-free OBJ loader used for the replacement chest geometry.
 * Only the two Chesttop meshes in the supplied model are imported.
 *
 * The source model is normalized into the same 4x5x3 model-space volume as
 * the original breast boxes, so the existing positioning and physics code in
 * {@link GenderLayer} continues to apply unchanged.
 */
public final class NewChestMesh {
    private static final String RESOURCE = "/assets/wildfire_gender/models/chest_new.obj";

    private static final MeshPair MESHES = load();

    private NewChestMesh() {
        throw new UnsupportedOperationException();
    }

    public static Mesh left(boolean jacket) {
        return jacket ? MESHES.leftJacket : MESHES.left;
    }

    public static Mesh right(boolean jacket) {
        return jacket ? MESHES.rightJacket : MESHES.right;
    }

    public record Mesh(Vertex[] vertices, int[] indices) {
    }

    public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        public Vertex withUV(float newU, float newV) {
            return new Vertex(x, y, z, newU, newV, nx, ny, nz);
        }
    }

    private record MeshPair(Mesh left, Mesh right, Mesh leftJacket, Mesh rightJacket) {
    }

    private record FaceCorner(int vertex, int uv, int normal) {
    }

    private record Face(List<FaceCorner> corners) {
    }

    private static MeshPair load() {
        try(InputStream input = NewChestMesh.class.getResourceAsStream(RESOURCE)) {
            if(input == null) {
                throw new IOException("Missing resource: " + RESOURCE);
            }
            return parse(input);
        } catch(Exception exception) {
            throw new ExceptionInInitializerError("Failed to load new chest model: " + exception.getMessage());
        }
    }

    private static MeshPair parse(InputStream input) throws IOException {
        List<Vector3f> positions = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        Map<String, List<Face>> groups = new HashMap<>();
        String currentGroup = "";

        try(BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while((line = reader.readLine()) != null) {
                line = line.trim();
                if(line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                switch(p[0]) {
                    case "v" -> positions.add(new Vector3f(
                            Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])));
                    case "vt" -> texCoords.add(new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2])});
                    case "vn" -> normals.add(new Vector3f(
                            Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])));
                    case "g" -> currentGroup = p.length > 1 ? p[1] : "";
                    case "f" -> {
                        if(!currentGroup.equals("Chesttop21") && !currentGroup.equals("Chesttop1")) break;
                        List<FaceCorner> corners = new ArrayList<>();
                        for(int i = 1; i < p.length; i++) {
                            String[] refs = p[i].split("/", -1);
                            corners.add(new FaceCorner(
                                    objIndex(refs[0], positions.size()),
                                    refs.length > 1 && !refs[1].isEmpty() ? objIndex(refs[1], texCoords.size()) : -1,
                                    refs.length > 2 && !refs[2].isEmpty() ? objIndex(refs[2], normals.size()) : -1));
                        }
                        groups.computeIfAbsent(currentGroup, unused -> new ArrayList<>()).add(new Face(corners));
                    }
                    default -> {
                        // mtllib/usemtl/o/s and other OBJ statements are not needed for runtime rendering.
                    }
                }
            }
        }

        if(!groups.containsKey("Chesttop21") || !groups.containsKey("Chesttop1")) {
            throw new IOException("The OBJ does not contain both Chesttop21 and Chesttop1 groups");
        }

        // Sort the two source pieces by X so the lower-X piece becomes the Minecraft left side.
        String first = groupMinX("Chesttop1", groups, positions) < groupMinX("Chesttop21", groups, positions)
                ? "Chesttop1" : "Chesttop21";
        String second = first.equals("Chesttop1") ? "Chesttop21" : "Chesttop1";

        Mesh left = buildMesh(groups.get(first), positions, normals, texCoords, true, false);
        Mesh right = buildMesh(groups.get(second), positions, normals, texCoords, false, false);
        Mesh leftJacket = withJacketUV(left, true);
        Mesh rightJacket = withJacketUV(right, false);
        return new MeshPair(left, right, leftJacket, rightJacket);
    }

    private static float groupMinX(String name, Map<String, List<Face>> groups, List<Vector3f> positions) {
        float min = Float.POSITIVE_INFINITY;
        Set<Integer> seen = new HashSet<>();
        for(Face face : groups.getOrDefault(name, List.of())) {
            for(FaceCorner corner : face.corners) seen.add(corner.vertex);
        }
        for(int index : seen) min = Math.min(min, positions.get(index).x);
        return min;
    }

    private static Mesh buildMesh(List<Face> faces, List<Vector3f> positions, List<Vector3f> normals,
                                  List<float[]> texCoords, boolean left, boolean jacket) {
        Set<Integer> used = new HashSet<>();
        for(Face face : faces) {
            for(FaceCorner corner : face.corners) used.add(corner.vertex);
        }

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for(int index : used) {
            Vector3f p = positions.get(index);
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
            minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z);
        }

        float dx = Math.max(maxX - minX, 0.000001f);
        float dy = Math.max(maxY - minY, 0.000001f);
        float dz = Math.max(maxZ - minZ, 0.000001f);
        // Keep the imported model's proportions instead of stretching it into a cube.
        float modelScale = 4f / Math.max(dx, dy);

        List<Vertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        Map<String, Integer> vertexMap = new HashMap<>();

        for(Face face : faces) {
            // OBJ can contain polygons; triangulate as a fan.
            for(int i = 1; i < face.corners.size() - 1; i++) {
                int a = addVertex(face.corners.get(0), positions, normals, texCoords,
                        minX, minY, minZ, dx, dy, dz, modelScale, left, jacket, vertices, vertexMap);
                int b = addVertex(face.corners.get(i), positions, normals, texCoords,
                        minX, minY, minZ, dx, dy, dz, modelScale, left, jacket, vertices, vertexMap);
                int c = addVertex(face.corners.get(i + 1), positions, normals, texCoords,
                        minX, minY, minZ, dx, dy, dz, modelScale, left, jacket, vertices, vertexMap);
                indices.add(a); indices.add(b); indices.add(c);
            }
        }

        Vertex[] vertexArray = vertices.toArray(Vertex[]::new);
        int[] indexArray = new int[indices.size()];
        for(int i = 0; i < indices.size(); i++) indexArray[i] = indices.get(i);
        return new Mesh(vertexArray, indexArray);
    }

    private static int addVertex(FaceCorner corner, List<Vector3f> positions, List<Vector3f> normals,
                                 List<float[]> texCoords, float minX, float minY, float minZ,
                                 float dx, float dy, float dz, float modelScale, boolean left, boolean jacket,
                                 List<Vertex> vertices, Map<String, Integer> vertexMap) {
        String key = corner.vertex + "/" + corner.uv + "/" + corner.normal + "/" + left + "/" + jacket;
        Integer cached = vertexMap.get(key);
        if(cached != null) return cached;

        Vector3f source = positions.get(corner.vertex);
        float nx = corner.normal >= 0 ? normals.get(corner.normal).x : 0f;
        float ny = corner.normal >= 0 ? normals.get(corner.normal).y : 0f;
        float nz = corner.normal >= 0 ? normals.get(corner.normal).z : 1f;
        Vector3f normal = new Vector3f(nx, ny, nz);
        if(normal.lengthSquared() < 0.000001f) normal.set(0, 0, 1);
        else normal.normalize();

        float x01 = (source.x - minX) / dx;
        float y01 = (source.y - minY) / dy;
        float z01 = (source.z - minZ) / dz;

        float x = (left ? -4f : 0f) + x01 * 4f;
        float y = y01 * dy * modelScale;
        float z = z01 * dz * modelScale;
        float y01ForUv = y01;

        float u = (left ? 20f : 24f) + x01 * 4f;
        float v = (jacket ? 42f : 26f) - y01ForUv * 5f;

        Vertex value = new Vertex(x, y, z, u, v, normal.x, normal.y, normal.z);
        int index = vertices.size();
        vertices.add(value);
        vertexMap.put(key, index);
        return index;
    }

    private static Mesh withJacketUV(Mesh original, boolean left) {
        Vertex[] vertices = new Vertex[original.vertices.length];
        for(int i = 0; i < vertices.length; i++) {
            Vertex v = original.vertices[i];
            vertices[i] = v.withUV(v.u(), v.v() + 16f);
        }
        return new Mesh(vertices, original.indices.clone());
    }

    private static int objIndex(String value, int count) {
        int parsed = Integer.parseInt(value);
        return parsed < 0 ? count + parsed : parsed - 1;
    }
}
