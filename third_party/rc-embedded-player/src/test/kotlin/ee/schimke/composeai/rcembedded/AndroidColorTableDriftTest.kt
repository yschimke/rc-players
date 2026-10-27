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

package ee.schimke.composeai.rcembedded

import androidx.compose.remote.creation.compose.state.AndroidSystemColorMap
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `ColorTheme` index → `android.R.color` name table exists twice, and the two copies must
 * agree.
 *
 * A document records an *index*, so the table is how a player decides which resource that index
 * meant. The embedded player reads `remote-creation-compose`'s `AndroidSystemColorMap` and the CMP
 * player carries the table in `:rc-player-protocol`'s `RcAndroidSystemColors`; the KMP module has
 * no Android target and cannot depend on the AndroidX one, so the duplication is structural. What
 * must not be structural is *drift*: two players reading the same index as different resources
 * would show up as a colour difference between render lanes, with nothing in either file looking
 * wrong.
 *
 * AndroidX's table is read from the class this module resolves, each id named through the
 * `android.R.color` field that holds it. The CMP table is read as source text, because that module
 * is not on this one's classpath.
 */
class AndroidColorTableDriftTest {

    @Test
    fun bothPlayersReadAnIndexAsTheSameResource() {
        val colorNames = android.R.color::class.java.fields.associate { it.getInt(null) to it.name }
        val embedded =
            (0 until AndroidSystemColorMap.size).map { index ->
                val id = AndroidSystemColorMap.getResourceId(index)
                colorNames[id] ?: error("index $index maps to $id, which is no android.R.color")
            }
        val cmp =
            names(
                repoFile(
                    "rc-player/protocol/src/commonMain/kotlin/ee/schimke/composeai/rcplayer/protocol/" +
                        "RcAndroidSystemColors.kt"
                ),
                marker = "public val NAMES",
                opener = "listOf(",
                entry = Regex("\"([a-z0-9_]+)\""),
            )

        assertTrue(
            "AndroidX table looks empty — did AndroidSystemColorMap move?",
            embedded.size > 100,
        )
        assertEquals("the two tables disagree on their length", embedded.size, cmp.size)
        embedded.indices.forEach { index ->
            assertEquals(
                "index $index names a different resource in each player",
                embedded[index],
                cmp[index],
            )
        }
    }

    /**
     * The [entry] matches inside the `[opener]...)` that follows [marker]. Deliberately dumb: it
     * reads the same characters a reviewer would, so a table edited in one file and not the other
     * fails here rather than in a render.
     */
    private fun names(file: File, marker: String, opener: String, entry: Regex): List<String> {
        val text = file.readText()
        val start = text.indexOf(marker)
        require(start >= 0) { "no `$marker` in ${file.path}" }
        val open = text.indexOf(opener, start)
        require(open >= 0) { "no $opener...) after `$marker` in ${file.path}" }
        // Balanced scan rather than a closing-brace pattern: the two files indent their tables
        // differently, and a test that only works at one indentation is a test that stops running
        // the
        // first time someone reformats.
        var depth = 0
        var close = -1
        for (i in text.indices.drop(open + opener.length - 1)) {
            when (text[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) close = i
            }
            if (close >= 0) break
        }
        require(close > open) { "unbalanced $opener...) after `$marker` in ${file.path}" }
        return entry.findAll(text.substring(open, close)).map { it.groupValues[1] }.toList()
    }

    /**
     * Walk up from the working directory to the repository root. Gradle runs unit tests with the
     * *module* directory as the working directory, and this test reads a file from a sibling
     * module.
     */
    private fun repoFile(relative: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relative from ${File("").absolutePath}")
    }
}
