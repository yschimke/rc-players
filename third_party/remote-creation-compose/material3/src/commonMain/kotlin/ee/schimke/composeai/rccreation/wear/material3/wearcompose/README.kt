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

/**
 * KMP (rc-players): the part of Wear Compose Material 3 (`androidx.wear.compose:compose-material3`
 * 1.5.5) that AndroidX's `remote-material3` reads, carried here because Wear Compose is Android's
 * alone. `remote-material3` takes Wear Compose's text configuration and default colour scheme as
 * values, its button scrim colours, its typography tokens, and `android.R.color`'s system colour
 * ids; the fork's sources keep upstream's imports, which `scripts/upstream-packages.tsv` maps to
 * this package. The `*Tokens` files are Wear Compose's generated tokens, unchanged apart from their
 * package; the rest is the subset of Wear Compose's classes that `remote-material3` uses.
 */
package ee.schimke.composeai.rccreation.wear.material3.wearcompose
