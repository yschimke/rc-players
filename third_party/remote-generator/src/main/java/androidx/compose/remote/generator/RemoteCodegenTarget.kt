/*
 * Copyright 2026 The Android Open Source Project
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

// LOCAL PATCH (rc-players): new file. Upstream chooses a platform by `LANGUAGE_*` constant, so a
// platform it does not ship cannot be added without editing `RemoteSerializationCodeGenerator`.
// A target is the extension point: an id and a function from the schema to files.

package androidx.compose.remote.generator

import androidx.compose.remote.generator.codegen.CppGenerator
import androidx.compose.remote.generator.codegen.PythonGenerator
import androidx.compose.remote.generator.codegen.RustGenerator
import androidx.compose.remote.generator.codegen.SwiftGenerator
import androidx.compose.remote.generator.codegen.TypeScriptGenerator

/** A code generation target: one platform's files, derived from the operation schema. */
public interface RemoteCodegenTarget {
    /** A stable id, as a command line names the target: `swift`, `rc-swift-wire-constants`. */
    public val id: String

    /** The files this target writes, keyed by path relative to the output directory. */
    public fun generate(schema: OperationSchema): Map<String, String>
}

/** A target from an id and a function, for targets that need no state of their own. */
public fun remoteCodegenTarget(
    id: String,
    generate: (OperationSchema) -> Map<String, String>,
): RemoteCodegenTarget =
    object : RemoteCodegenTarget {
        override val id: String = id

        override fun generate(schema: OperationSchema): Map<String, String> = generate(schema)
    }

/**
 * Upstream's languages as targets, at the file names `RemoteSerializationCodeGenerator.generate`
 * writes, plus the platform options this fork adds (`swift-portable`).
 */
public fun builtInTargets(): List<RemoteCodegenTarget> =
    listOf(
        remoteCodegenTarget("cpp") { schema ->
            val gen = CppGenerator(schema)
            mapOf(
                "WireBuffer.h" to gen.generateWireBufferHeader(),
                "WireBuffer.cpp" to gen.generateWireBufferSource(),
                "RemoteComposeOps.h" to gen.generateHeader(),
                "RemoteComposeOps.cpp" to gen.generateSource(),
            )
        },
        remoteCodegenTarget("rust") { schema ->
            val gen = RustGenerator(schema)
            mapOf(
                "remote_compose_operations.rs" to gen.generate(),
                "op_codes.rs" to gen.generateOpCodesModule(),
            )
        },
        remoteCodegenTarget("python") { schema ->
            mapOf("remote_compose.py" to PythonGenerator(schema).generate())
        },
        remoteCodegenTarget("swift") { schema ->
            mapOf("RemoteComposeOperations.swift" to SwiftGenerator(schema).generate())
        },
        remoteCodegenTarget("swift-portable") { schema ->
            mapOf(
                "RemoteComposeOperations.swift" to
                    SwiftGenerator(schema, SwiftGenerator.Options.PORTABLE).generate(),
            )
        },
        remoteCodegenTarget("typescript") { schema ->
            mapOf("remoteComposeOperations.ts" to TypeScriptGenerator(schema).generate())
        },
    )
