package com.evirapo.diversityofcritters.client.locomotion;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bind-time queries over a baked model hierarchy, using only public {@link ModelPart} API.
 *
 * <p>{@link ModelPart#visit} reports every cube together with the slash-separated path of the part
 * that owns it ({@code "/Body/Head"}), which is enough to rebuild the chain from the root to any
 * part that has geometry below it. Allocates freely: call from model constructors, never per frame.
 */
@OnlyIn(Dist.CLIENT)
public final class ModelParts {

    /** Model units per block at scale 1. */
    public static final float UNITS_PER_BLOCK = 16.0f;

    private ModelParts() {
    }

    /**
     * Parts from {@code root} down to {@code target}, both included; null if the target is not
     * reachable (or has no geometry anywhere under it).
     */
    public static @Nullable List<ModelPart> chain(ModelPart root, ModelPart target) {
        final List<ModelPart> chain = new ArrayList<>();
        chain.add(root);
        if (root == target) {
            return chain;
        }

        for (String path : partPaths(root)) {
            ModelPart current = root;
            chain.subList(1, chain.size()).clear();
            for (String name : path.split("/")) {
                if (name.isEmpty()) {
                    continue;
                }
                current = current.getChild(name);
                chain.add(current);
                if (current == target) {
                    return chain;
                }
            }
        }
        return null;
    }

    /** Distinct paths of every part under {@code root} that owns at least one cube. */
    private static Set<String> partPaths(ModelPart root) {
        final Set<String> paths = new LinkedHashSet<>();
        root.visit(new PoseStack(), (pose, path, index, cube) -> paths.add(path));
        return paths;
    }

    /**
     * Bounds of all geometry under {@code part} (its own cubes and its descendants'), in the part's
     * own frame and model units, as {minX, maxX, minY, maxY, minZ, maxZ}. Null if there is none.
     *
     * <p>Model space points +Y down, so {@code maxY} is how far below the pivot the lowest point
     * hangs: for a leg, the distance from hip to sole.
     */
    public static float @Nullable [] localBounds(ModelPart part) {
        // visit() applies the part's own transform first; undo it to land in the part's frame.
        final PoseStack own = new PoseStack();
        part.translateAndRotate(own);
        final Matrix4f toLocal = new Matrix4f(own.last().pose()).invert();

        final float[] bounds = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
                Float.MAX_VALUE, -Float.MAX_VALUE};
        final Matrix4f local = new Matrix4f();
        final Vector3f corner = new Vector3f();

        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            toLocal.mul(pose.pose(), local);
            for (int i = 0; i < 8; i++) {
                corner.set(((i & 1) == 0 ? cube.minX : cube.maxX) / UNITS_PER_BLOCK,
                        ((i & 2) == 0 ? cube.minY : cube.maxY) / UNITS_PER_BLOCK,
                        ((i & 4) == 0 ? cube.minZ : cube.maxZ) / UNITS_PER_BLOCK);
                local.transformPosition(corner);
                corner.mul(UNITS_PER_BLOCK);
                bounds[0] = Math.min(bounds[0], corner.x);
                bounds[1] = Math.max(bounds[1], corner.x);
                bounds[2] = Math.min(bounds[2], corner.y);
                bounds[3] = Math.max(bounds[3], corner.y);
                bounds[4] = Math.min(bounds[4], corner.z);
                bounds[5] = Math.max(bounds[5], corner.z);
            }
        });
        return bounds[0] == Float.MAX_VALUE ? null : bounds;
    }
}
