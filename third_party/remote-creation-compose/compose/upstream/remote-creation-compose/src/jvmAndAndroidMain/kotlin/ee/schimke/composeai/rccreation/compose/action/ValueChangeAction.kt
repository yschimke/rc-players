/*
 * Copyright 2025 The Android Open Source Project
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

package ee.schimke.composeai.rccreation.compose.action

import ee.schimke.composeai.rccreation.common.RemoteActionData
import ee.schimke.composeai.rccreation.compose.state.MutableRemoteFloat
import ee.schimke.composeai.rccreation.compose.state.MutableRemoteInt
import ee.schimke.composeai.rccreation.compose.state.MutableRemoteState
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteInt
import ee.schimke.composeai.rccreation.compose.state.RemoteState
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope
import ee.schimke.composeai.rccreation.compose.state.RemoteString
import ee.schimke.composeai.rccreation.compose.state.creationState
import ee.schimke.composeai.rccreation.compose.state.isLiteral

/** Update a value on click. */
internal class ValueChangeAction<T>(
    public val remoteValue: MutableRemoteState<T>,
    public val updatedValue: RemoteState<T>,
) : RemoteAction {
    // KMP (rc-players): `RemoteWriter` takes `RemoteActionData`, where upstream builds
    // `remote-creation-core`'s `Action`.
    override fun RemoteStateScope.toRemoteActionData(): List<RemoteActionData> {
        val actualMutable = remoteValue.asEncodedMutable
        val actualValue = updatedValue.asEncoded
        val action =
            when (actualMutable) {
                is MutableRemoteInt -> {
                    actualValue as RemoteInt
                    val array = actualValue.arrayForCreationState(creationState)
                    if (array.isLiteral()) {
                        RemoteActionData.IntegerChange(actualMutable.id, array[0].toInt())
                    } else {
                        RemoteActionData.IntegerExpressionChange(
                            actualMutable.longId,
                            actualValue.longId,
                        )
                    }
                }
                is MutableRemoteFloat -> {
                    actualValue as RemoteFloat
                    RemoteActionData.FloatExpressionChange(actualMutable.id, actualValue.id)
                }
                is RemoteString -> {
                    actualValue as RemoteString
                    // KMP (rc-players): a value that is not constant is named by its id, where
                    // upstream takes `constantValue` and fails without one.
                    val constantValue = actualValue.constantValueOrNull
                    if (constantValue != null) {
                        RemoteActionData.StringChangeText(actualMutable.id, constantValue)
                    } else {
                        RemoteActionData.StringChange(actualMutable.id, actualValue.id)
                    }
                }
                else -> error("Unsupported value change type $actualMutable")
            }
        return listOf(action)
    }
}

internal class ValueFloatChangeAction(
    public val value: MutableRemoteFloat,
    public val updatedValue: Float,
) : RemoteAction {
    // KMP (rc-players): `RemoteWriter` takes `RemoteActionData`, where upstream builds
    // `remote-creation-core`'s `Action`.
    override fun RemoteStateScope.toRemoteActionData(): List<RemoteActionData> =
        listOf(RemoteActionData.FloatChange(value.id, updatedValue))
}

/**
 * Creates an [RemoteAction] that updates the value of a [MutableRemoteState] to a new
 * [RemoteState].
 *
 * Example of toggling a [MutableRemoteState] boolean value on click:
 *
 * @sample ee.schimke.composeai.rccreation.compose.samples.ClickableValueChangeSample
 * @param remoteState The mutable remote state to be updated.
 * @param updatedValue The new remote state value to apply.
 * @return An [RemoteAction] representing the value change.
 */
public fun <T> valueChangeAction(
    remoteState: MutableRemoteState<T>,
    updatedValue: RemoteState<T>,
): RemoteAction = ValueChangeAction(remoteState, updatedValue)
