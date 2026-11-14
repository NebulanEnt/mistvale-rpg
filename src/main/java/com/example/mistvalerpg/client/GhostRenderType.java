package com.example.mistvalerpg.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * My own paint for the Soul Crystal's cubes: the same see-through block paint the game uses for moving
 * blocks, with one change. It never writes to the depth buffer.
 * <p>
 * Why that matters: the depth buffer remembers how close the nearest thing at each pixel is, and
 * anything behind it is thrown away. With three cubes inside each other, the faint outer cubes used to
 * "claim" their pixels, and the solid block inside was then cut off wherever a ghost face was in front of it.
 * Without depth writes, every cube only blends over what is already on screen, so nothing is thrown away.
 * <p>
 * It extends {@link RenderType} only to reach the protected building blocks (the "shards") it is made from.
 */
public final class GhostRenderType extends RenderType {
    public static final RenderType CUBES = create(
            "mistvale_rpg_soul_cubes",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            1536,
            true,   // affects crumbling (unused here)
            true,   // sort the quads from far to near each frame
            CompositeState.builder()
                    .setLightmapState(LIGHTMAP)
                    .setShaderState(RENDERTYPE_TRANSLUCENT_MOVING_BLOCK_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, true))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE) // colour only: no depth writes
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .createCompositeState(true));

    private GhostRenderType() {
        super("unused", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 0, false, false, () -> { }, () -> { });
    }
}