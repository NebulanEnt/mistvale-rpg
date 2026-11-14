package com.example.mistvalerpg.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * A thin wrapper around a vertex consumer that makes everything drawn through it more see-through.
 * Minecraft draws a model by feeding corner points to a consumer; this one passes them on
 * unchanged, except that every colour's alpha is multiplied by a fixed amount.
 */
final class AlphaConsumer implements VertexConsumer {
    private final VertexConsumer inner;
    private final float alpha;

    AlphaConsumer(VertexConsumer inner, float alpha) {
        this.inner = inner;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        inner.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        inner.setColor(red, green, blue, (int) (alpha * this.alpha));
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        inner.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        inner.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        inner.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        inner.setNormal(x, y, z);
        return this;
    }
}
