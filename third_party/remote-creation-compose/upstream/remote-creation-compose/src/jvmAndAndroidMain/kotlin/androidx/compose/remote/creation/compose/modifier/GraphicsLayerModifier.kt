/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:kotlin.jvm.JvmName("GraphicsLayerModifierKt")

package androidx.compose.remote.creation.compose.modifier

import androidx.compose.remote.creation.common.RemoteLayerAttribute
import androidx.compose.remote.creation.common.RemoteModifierOperation
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.layout.remoteComponent
import androidx.compose.remote.creation.compose.shapes.RemoteCircleShape
import androidx.compose.remote.creation.compose.shapes.RemoteRectangleShape
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.shapes.RemoteShape
import androidx.compose.remote.creation.compose.shapes.toDimension
import androidx.compose.remote.creation.compose.state.RemoteCompositingStrategy
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteStateScope
import androidx.compose.remote.creation.compose.state.RemoteTileMode
import androidx.compose.remote.creation.compose.state.rf

internal class GraphicsLayerModifier(
    val scaleX: RemoteFloat,
    val scaleY: RemoteFloat,
    val rotationX: RemoteFloat,
    val rotationY: RemoteFloat,
    val rotationZ: RemoteFloat,
    val shadowElevation: RemoteFloat,
    val transformOriginX: RemoteFloat,
    val transformOriginY: RemoteFloat,
    val translationX: RemoteFloat,
    val translationY: RemoteFloat,
    val shape: RemoteShape = RemoteRectangleShape,
    val compositingStrategy: RemoteCompositingStrategy = RemoteCompositingStrategy.Auto,
    val alpha: RemoteFloat,
    val cameraDistance: RemoteFloat,
    val renderEffect: RemoteRenderEffect?,
) : RemoteModifier.Element {

    // KMP (rc-players): `RemoteWriter` takes a `RemoteModifierOperation`, where upstream builds
    // `remote-creation-core`'s `RecordingModifier.Element`.
    override fun RemoteStateScope.toRemoteModifierOperation(): RemoteModifierOperation {
        val attributes = mutableListOf<RemoteLayerAttribute>()
        fun float(id: Int, value: RemoteFloat, default: Float) {
            if (value.floatId != default) {
                attributes += RemoteLayerAttribute.FloatValue(id, value.floatId)
            }
        }
        fun int(id: Int, value: Int, default: Int) {
            if (value != default) attributes += RemoteLayerAttribute.IntValue(id, value)
        }
        float(Layer.SCALE_X, scaleX, 1f)
        float(Layer.SCALE_Y, scaleY, 1f)
        float(Layer.ROTATION_X, rotationX, 0f)
        float(Layer.ROTATION_Y, rotationY, 0f)
        float(Layer.ROTATION_Z, rotationZ, 0f)
        float(Layer.TRANSFORM_ORIGIN_X, transformOriginX, 0f)
        float(Layer.TRANSFORM_ORIGIN_Y, transformOriginY, 0f)
        float(Layer.TRANSLATION_X, translationX, 0f)
        float(Layer.TRANSLATION_Y, translationY, 0f)
        float(Layer.SHADOW_ELEVATION, shadowElevation, 0f)
        float(Layer.ALPHA, alpha, 1f)
        float(Layer.CAMERA_DISTANCE, cameraDistance, 8f)
        int(Layer.COMPOSITING_STRATEGY, compositingStrategy.value, 0)
        if (renderEffect is RemoteBlurEffect) {
            attributes +=
                RemoteLayerAttribute.FloatValue(
                    Layer.BLUR_RADIUS_X,
                    renderEffect.radiusX,
                )
            attributes +=
                RemoteLayerAttribute.FloatValue(
                    Layer.BLUR_RADIUS_Y,
                    renderEffect.radiusY,
                )
            val tileMode =
                when (renderEffect.edgeTreatment) {
                    RemoteTileMode.Clamp -> Layer.TILE_MODE_CLAMP
                    RemoteTileMode.Repeated -> Layer.TILE_MODE_REPEATED
                    RemoteTileMode.Mirror -> Layer.TILE_MODE_MIRROR
                    RemoteTileMode.Decal -> Layer.TILE_MODE_DECAL
                    else -> Layer.TILE_MODE_CLAMP
                }
            attributes +=
                RemoteLayerAttribute.IntValue(
                    Layer.BLUR_TILE_MODE,
                    tileMode,
                )
        }
        when (shape) {
            RemoteCircleShape ->
                attributes +=
                    RemoteLayerAttribute.IntValue(
                        Layer.SHAPE,
                        Layer.SHAPE_CIRCLE,
                    )
            RemoteRectangleShape ->
                attributes +=
                    RemoteLayerAttribute.IntValue(
                        Layer.SHAPE,
                        Layer.SHAPE_RECT,
                    )
            is RemoteRoundedCornerShape -> {
                val component = remoteComponent()
                val remoteSize = RemoteSize(component.width, component.height)
                attributes +=
                    RemoteLayerAttribute.IntValue(
                        Layer.SHAPE,
                        Layer.SHAPE_ROUND_RECT,
                    )
                attributes +=
                    RemoteLayerAttribute.FloatValue(
                        Layer.SHAPE_RADIUS,
                        shape.topStart
                            .toDimension(remoteSize, remoteDensity, densityBehavior)
                            .floatId,
                    )
            }
            else -> Unit
        }
        return RemoteModifierOperation.GraphicsLayer(attributes)
    }
}

/**
 * Applies visual effects such as scaling, rotation, translation, shadow, alpha, or clipping to
 * content.
 *
 * This overload accepts [RemoteFloat] values, which allow binding properties to dynamic expressions
 * or state.
 *
 * Note: Graphics layer clipping only supports a uniform corner radius. If an asymmetric
 * [RemoteRoundedCornerShape] is provided, [RemoteRoundedCornerShape.topStart] is used for all
 * corners.
 *
 * @param scaleX horizontal scale factor
 * @param scaleY vertical scale factor
 * @param rotationX rotation of the layer around the X axis in degrees
 * @param rotationY rotation of the layer around the Y axis in degrees
 * @param rotationZ rotation of the layer around the Z axis in degrees
 * @param shadowElevation shadow elevation of the layer
 * @param transformOriginX horizontal center of the transform, as a fraction of the layer width
 * @param transformOriginY vertical center of the transform, as a fraction of the layer height
 * @param translationX horizontal translation of the layer
 * @param translationY vertical translation of the layer
 * @param alpha alpha (opacity) of the layer
 * @param shape [RemoteShape] of the layer
 * @param compositingStrategy [RemoteCompositingStrategy] to use for the layer
 * @param cameraDistance camera distance for 3D transforms
 * @param renderEffect [RemoteRenderEffect] to apply, or null
 * @return a [RemoteModifier] that applies the graphics layer effects
 */
public fun RemoteModifier.graphicsLayer(
    scaleX: RemoteFloat = 1f.rf,
    scaleY: RemoteFloat = 1f.rf,
    rotationX: RemoteFloat = 0f.rf,
    rotationY: RemoteFloat = 0f.rf,
    rotationZ: RemoteFloat = 0f.rf,
    shadowElevation: RemoteFloat = 0f.rf,
    transformOriginX: RemoteFloat = 0.5f.rf,
    transformOriginY: RemoteFloat = 0.5f.rf,
    translationX: RemoteFloat = 0f.rf,
    translationY: RemoteFloat = 0f.rf,
    alpha: RemoteFloat = 1f.rf,
    shape: RemoteShape = RemoteRectangleShape,
    compositingStrategy: RemoteCompositingStrategy = RemoteCompositingStrategy.Auto,
    cameraDistance: RemoteFloat = 8f.rf, // Default Value for Camera Distance
    renderEffect: RemoteRenderEffect? = null,
): RemoteModifier =
    then(
        GraphicsLayerModifier(
            scaleX = scaleX,
            scaleY = scaleY,
            rotationX = rotationX,
            rotationY = rotationY,
            rotationZ = rotationZ,
            shadowElevation = shadowElevation,
            transformOriginX = transformOriginX,
            transformOriginY = transformOriginY,
            translationX = translationX,
            translationY = translationY,
            shape = shape,
            compositingStrategy = compositingStrategy,
            alpha = alpha,
            cameraDistance = cameraDistance,
            renderEffect = renderEffect,
        ),
    )

/** Scope for configuring graphics layer properties in a type-safe builder lambda. */
public interface RemoteGraphicsLayerScope {
    /** The horizontal scale factor. */
    public var scaleX: RemoteFloat

    /** The vertical scale factor. */
    public var scaleY: RemoteFloat

    /** The rotation of the layer around the X axis, in degrees. */
    public var rotationX: RemoteFloat

    /** The rotation of the layer around the Y axis, in degrees. */
    public var rotationY: RemoteFloat

    /** The rotation of the layer around the Z axis, in degrees. */
    public var rotationZ: RemoteFloat

    /** The shadow elevation of the layer. */
    public var shadowElevation: RemoteFloat

    /** The horizontal center of the transform, as a fraction of the layer width. */
    public var transformOriginX: RemoteFloat

    /** The vertical center of the transform, as a fraction of the layer height. */
    public var transformOriginY: RemoteFloat

    /** The horizontal translation of the layer. */
    public var translationX: RemoteFloat

    /** The vertical translation of the layer. */
    public var translationY: RemoteFloat

    /** The alpha (opacity) of the layer, from 0f (transparent) to 1f (opaque). */
    public var alpha: RemoteFloat

    /** The camera distance for 3D transforms. */
    public var cameraDistance: RemoteFloat

    /** The shape of the layer. Used for clipping and outline shadows. */
    public var shape: RemoteShape

    /** The compositing strategy to use for the layer. */
    public var compositingStrategy: RemoteCompositingStrategy

    /** The [RemoteRenderEffect] to apply to this layer, or null. */
    public var renderEffect: RemoteRenderEffect?
}

internal class GraphicsLayerScopeImpl : RemoteGraphicsLayerScope {
    override var scaleX: RemoteFloat = 1f.rf
    override var scaleY: RemoteFloat = 1f.rf
    override var rotationX: RemoteFloat = 0f.rf
    override var rotationY: RemoteFloat = 0f.rf
    override var rotationZ: RemoteFloat = 0f.rf
    override var shadowElevation: RemoteFloat = 0f.rf
    override var transformOriginX: RemoteFloat = 0.5f.rf
    override var transformOriginY: RemoteFloat = 0.5f.rf
    override var translationX: RemoteFloat = 0f.rf
    override var translationY: RemoteFloat = 0f.rf
    override var alpha: RemoteFloat = 1f.rf
    override var cameraDistance: RemoteFloat = 8f.rf
    override var shape: RemoteShape = RemoteRectangleShape
    override var compositingStrategy: RemoteCompositingStrategy = RemoteCompositingStrategy.Auto
    override var renderEffect: RemoteRenderEffect? = null
}

/**
 * Applies a graphics layer modifier to the [RemoteModifier] using a type-safe builder lambda.
 *
 * This overload allows configuring layer properties within a [RemoteGraphicsLayerScope] block,
 * which is similar to standard Compose's `graphicsLayer { ... }`.
 *
 * Example usage:
 * ```
 * modifier.graphicsLayer {
 *     alpha = 0.5f.rf
 *     rotationZ = 45f.rf
 * }
 * ```
 *
 * @param block The lambda block to configure the [RemoteGraphicsLayerScope].
 * @return a [RemoteModifier] that applies the graphics layer configured by [block]
 */
public fun RemoteModifier.graphicsLayer(
    block: RemoteGraphicsLayerScope.() -> Unit,
): RemoteModifier {
    val scope = GraphicsLayerScopeImpl().apply(block)
    return graphicsLayer(
        scaleX = scope.scaleX,
        scaleY = scope.scaleY,
        rotationX = scope.rotationX,
        rotationY = scope.rotationY,
        rotationZ = scope.rotationZ,
        shadowElevation = scope.shadowElevation,
        transformOriginX = scope.transformOriginX,
        transformOriginY = scope.transformOriginY,
        translationX = scope.translationX,
        translationY = scope.translationY,
        alpha = scope.alpha,
        shape = scope.shape,
        compositingStrategy = scope.compositingStrategy,
        cameraDistance = scope.cameraDistance,
        renderEffect = scope.renderEffect,
    )
}

// KMP (rc-players): the attribute ids and values of `remote-core`'s
// `GraphicsLayerModifierOperation`, which upstream reads from that class.
private object Layer {
    const val SCALE_X = 0
    const val SCALE_Y = 1
    const val ROTATION_X = 2
    const val ROTATION_Y = 3
    const val ROTATION_Z = 4
    const val TRANSFORM_ORIGIN_X = 5
    const val TRANSFORM_ORIGIN_Y = 6
    const val TRANSLATION_X = 7
    const val TRANSLATION_Y = 8
    const val SHADOW_ELEVATION = 10
    const val ALPHA = 11
    const val CAMERA_DISTANCE = 12
    const val COMPOSITING_STRATEGY = 13
    const val BLUR_RADIUS_X = 17
    const val BLUR_RADIUS_Y = 18
    const val BLUR_TILE_MODE = 19
    const val SHAPE = 20
    const val SHAPE_RADIUS = 21
    const val SHAPE_RECT = 0
    const val SHAPE_ROUND_RECT = 1
    const val SHAPE_CIRCLE = 2
    const val TILE_MODE_CLAMP = 0
    const val TILE_MODE_REPEATED = 1
    const val TILE_MODE_MIRROR = 2
    const val TILE_MODE_DECAL = 3
}
