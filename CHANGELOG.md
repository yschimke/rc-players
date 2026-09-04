# Changelog

## [1.57.0](https://github.com/yschimke/rc-players/compare/v1.56.1...v1.57.0) (2026-09-04)


### Features

* **compose:** animate StateLayout and FitBox alternatives, with shared elements ([#28](https://github.com/yschimke/rc-players/issues/28)) ([fb9efdb](https://github.com/yschimke/rc-players/commit/fb9efdb6522ec56bac9067696293fd78b691d5df))
* **compose:** draw SupportSpannableString, and demo the host half of a custom component ([#31](https://github.com/yschimke/rc-players/issues/31)) ([13f75dd](https://github.com/yschimke/rc-players/commit/13f75dd7b699b753e588c690cd76ad4dfd4f01ad))


### Bug Fixes

* **rc-embedded:** enable the AndroidX embedded player in its render harness ([#30](https://github.com/yschimke/rc-players/issues/30)) ([9d565fe](https://github.com/yschimke/rc-players/commit/9d565fe49a5782e7515b62691a45f349d96846a2))

## [1.56.1](https://github.com/yschimke/rc-players/compare/v1.56.0...v1.56.1) (2026-09-02)


### Bug Fixes

* **compose:** honor FILTER_BITMAP so an image container painter draws ([#26](https://github.com/yschimke/rc-players/issues/26)) ([e4190c7](https://github.com/yschimke/rc-players/commit/e4190c7140b4a055140b1428ff2aea97630a23aa))

## [1.56.0](https://github.com/yschimke/rc-players/compare/v1.55.1...v1.56.0) (2026-09-01)


### Features

* **compose:** support host custom components ([#23](https://github.com/yschimke/rc-players/issues/23)) ([f76a078](https://github.com/yschimke/rc-players/commit/f76a0784c2f1932c91eb3be02c34ef0cb5523382))

## [1.55.1](https://github.com/yschimke/rc-players/compare/v1.55.0...v1.55.1) (2026-09-01)


### Bug Fixes

* render image-background documents in browser players ([#21](https://github.com/yschimke/rc-players/issues/21)) ([b02aa07](https://github.com/yschimke/rc-players/commit/b02aa075b431908146eaac8c1796fe3df65ce8c5))

## [1.55.0](https://github.com/yschimke/rc-players/compare/v1.54.0...v1.55.0) (2026-08-30)


### Features

* consume the wire contracts instead of building them ([#4771](https://github.com/yschimke/rc-players/issues/4771)) ([565752c](https://github.com/yschimke/rc-players/commit/565752cd4c59a5b7271ef704e6ec7ab47acabcc5))
* decode alpha16 modifier draw content ([#3245](https://github.com/yschimke/rc-players/issues/3245)) ([e9e175c](https://github.com/yschimke/rc-players/commit/e9e175c12dcf1dac8a6d65d3f294f9d9af16e3fb))
* **design-catalog-remote-m3:** declare Roboto Flex and Google Sans Flex typeface themes ([#3516](https://github.com/yschimke/rc-players/issues/3516)) ([3d79d45](https://github.com/yschimke/rc-players/commit/3d79d45ceaaaf714d82bc6ce7bedf19bac898cb8))
* **figma-svg:** export a node's own imperative draw by re-invoking it offscreen ([#2944](https://github.com/yschimke/rc-players/issues/2944)) ([50004f3](https://github.com/yschimke/rc-players/commit/50004f365af78aa611e3a69612239ff62c906a58))
* **fonts:** fetch a family's real variable font file, not a baked instance ([#3339](https://github.com/yschimke/rc-players/issues/3339)) ([fe0c309](https://github.com/yschimke/rc-players/commit/fe0c309b72c63a961baf518dbc7feb7f2faed591))
* implement alpha16 ComponentValue in CMP player ([#3244](https://github.com/yschimke/rc-players/issues/3244)) ([d8912bc](https://github.com/yschimke/rc-players/commit/d8912bc3d976b07deeb15db396d665d0f646ad88))
* publish the two artifacts the CLI consumes ([0a2aa20](https://github.com/yschimke/rc-players/commit/0a2aa20646982c5afe5392291f5b6b4a6bd602a5))
* **rc-compare:** compare both AndroidX embedded players ([#4490](https://github.com/yschimke/rc-players/issues/4490)) ([a0523d3](https://github.com/yschimke/rc-players/commit/a0523d3c9ee06eac68c653ede12ca99e6240ec74))
* **rc-embedded-jvm:** add JvmRemoteContext with a skiko bitmap decode ([#3012](https://github.com/yschimke/rc-players/issues/3012)) ([ac788ce](https://github.com/yschimke/rc-players/commit/ac788ce99a433febc98b6cbb8776f24b7f318943))
* **rc-embedded-jvm:** render Remote Compose documents to PNG on the desktop JVM ([#3025](https://github.com/yschimke/rc-players/issues/3025)) ([492b495](https://github.com/yschimke/rc-players/commit/492b49542500f2aeaae24f7725c2c2b280af9fa1))
* **rc-embedded:** implement the canvas text seam's jvm half over skiko ([#2982](https://github.com/yschimke/rc-players/issues/2982)) ([a69f795](https://github.com/yschimke/rc-players/commit/a69f7951b08359756b010d8b41f87b52a07ccdf4))
* **rc-embedded:** run the player's value layer on the desktop JVM ([#2943](https://github.com/yschimke/rc-players/issues/2943)) ([761e5af](https://github.com/yschimke/rc-players/commit/761e5af4ea2c1a5b980135a8a8c20a247781a09c))
* **rc-embedded:** vendor AndroidX's embedded RC player as a third rc-compare lane ([#2929](https://github.com/yschimke/rc-players/issues/2929)) ([9f91f4d](https://github.com/yschimke/rc-players/commit/9f91f4d16e60389150542a033b18df9169ec3047))
* **rc-js-player:** implement FontData opcode 189 ([#3656](https://github.com/yschimke/rc-players/issues/3656)) ([394ad76](https://github.com/yschimke/rc-players/commit/394ad764067fc90546b36706c9c5ef41cd60ed91))
* **rc-metrics:** text-metric fixtures that draw the player's own metrics as guide lines ([#3599](https://github.com/yschimke/rc-players/issues/3599)) ([001d554](https://github.com/yschimke/rc-players/commit/001d55434d53dcea29cdfc1f7511030edb13565b))
* **rc-player-compose:** resolve the default face by name and apply font axes ([#3334](https://github.com/yschimke/rc-players/issues/3334)) ([df08d91](https://github.com/yschimke/rc-players/commit/df08d91c39ed79df3dce8c903580690b5d12d5ac))
* **rc-player-jvm:** apply a document's font-variation axes ([#3336](https://github.com/yschimke/rc-players/issues/3336)) ([bebdb0f](https://github.com/yschimke/rc-players/commit/bebdb0fed5ead410840088b850aab4ec9c3fbbb7))
* **rc-player-jvm:** download google: font families for the cmp-jvm lane ([#3327](https://github.com/yschimke/rc-players/issues/3327)) ([4204ef0](https://github.com/yschimke/rc-players/commit/4204ef01c60985010f010aff6edd3d64027972ba))
* **rc-player:** apply a document's font-variation axes in the js lane ([#3348](https://github.com/yschimke/rc-players/issues/3348)) ([558e1ad](https://github.com/yschimke/rc-players/commit/558e1ade97b38b65857ba57fcc2a5ef6f1ad1a46))
* **rc-player:** distribute the Wasm player as a consumable npm bundle ([#4194](https://github.com/yschimke/rc-players/issues/4194)) ([c4db398](https://github.com/yschimke/rc-players/commit/c4db3983dce0ad65d93e9e9e453796178ddd9efa))
* **rc-player:** implement extended CoreText properties ([#3667](https://github.com/yschimke/rc-players/issues/3667)) ([e2f8d2a](https://github.com/yschimke/rc-players/commit/e2f8d2a62ddf99c2d1d912cfa01e87d7d232826a))
* **rc-player:** read a document's override surface from its own operations ([#3947](https://github.com/yschimke/rc-players/issues/3947)) ([948fde4](https://github.com/yschimke/rc-players/commit/948fde408a21cf6617c6e08744cc019a0785abfb))
* **rc-player:** render canvas-operations draw blocks (opcode 173) ([#2740](https://github.com/yschimke/rc-players/issues/2740)) ([0dcd00a](https://github.com/yschimke/rc-players/commit/0dcd00a89ffd8bd8032f2e2834dce9be2f8da34d))
* **rc-player:** render Remote Compose with the renderer's own typefaces ([#2908](https://github.com/yschimke/rc-players/issues/2908)) ([ae99bec](https://github.com/yschimke/rc-players/commit/ae99becbc092ec193375c1aba6a9746028a23f6b))
* **rc-player:** replace the host font map with an RcTypefaceLoader interface ([#4178](https://github.com/yschimke/rc-players/issues/4178)) ([1133950](https://github.com/yschimke/rc-players/commit/1133950f396c16db16cf48de1c6b57464e99b981))
* **rc-player:** resolve named font families and serve them from Google Fonts ([#2919](https://github.com/yschimke/rc-players/issues/2919)) ([ccebc6b](https://github.com/yschimke/rc-players/commit/ccebc6bce0a2fbb6ce63c3f91d01e843eb4787a3))
* **rc-player:** ship a real default RcTypefaceLoader and move manifest loading into shared code ([#4185](https://github.com/yschimke/rc-players/issues/4185)) ([7bf4fae](https://github.com/yschimke/rc-players/commit/7bf4fae781abb5c4d5774ac3ee6526f54324f16e))
* **rc-player:** ship the iOS player as an XCFramework consumable from Swift ([#4193](https://github.com/yschimke/rc-players/issues/4193)) ([81593cd](https://github.com/yschimke/rc-players/commit/81593cdcb6f1d09816ee357889d379cf2da2670e))
* **rc-player:** support state layout operation ([#3634](https://github.com/yschimke/rc-players/issues/3634)) ([aacc04d](https://github.com/yschimke/rc-players/commit/aacc04d864d13eaeed4f6f1659b99a0fad5fe713))
* **rc-player:** trace the CMP players with androidx.tracing 2 and profile four documents ([#3341](https://github.com/yschimke/rc-players/issues/3341)) ([7f54d80](https://github.com/yschimke/rc-players/commit/7f54d80f7b09f95254aa6a7873e37a0bf0dbbfcb))
* **rc:** Add CMP Wasm Remote Compose player ([#3201](https://github.com/yschimke/rc-players/issues/3201)) ([581695a](https://github.com/yschimke/rc-players/commit/581695a3c73004fc346ac102a867d898e2f4f42d))
* **rc:** default to the embedded player, not the AOSP view player ([#4455](https://github.com/yschimke/rc-players/issues/4455)) ([f5cf9c6](https://github.com/yschimke/rc-players/commit/f5cf9c6f55ebb40eaa8282fab80f7be2dc5e8012))
* **remotecompose:** add animated Material 3 previews ([#3146](https://github.com/yschimke/rc-players/issues/3146)) ([25f1ce6](https://github.com/yschimke/rc-players/commit/25f1ce6c92c7e5c8c20084eb1ae5a3ad8625a1f0))
* **remotecompose:** serve google: font families to the view player ([#3335](https://github.com/yschimke/rc-players/issues/3335)) ([eff12b6](https://github.com/yschimke/rc-players/commit/eff12b6e2399e77d8b10232dccb0d10cf0c5474b))
* **serve:** apply rc.* knob seeds in the cmp-jvm render ([#3035](https://github.com/yschimke/rc-players/issues/3035)) ([1b0d14e](https://github.com/yschimke/rc-players/commit/1b0d14e51c62859748bf33bc6f82c70a49a8f719))
* **serve:** in-browser Remote Compose canvas render lane (+ rename .rcdoc → .rc) ([#2720](https://github.com/yschimke/rc-players/issues/2720)) ([64fa745](https://github.com/yschimke/rc-players/commit/64fa745d6f586426d0c7464c63279781f1e92c0a))
* stand up the players as a build of their own ([d92ac53](https://github.com/yschimke/rc-players/commit/d92ac53c596a4bce8f3149916f7bb5bd5ea15f15))


### Bug Fixes

* address missed Codex review findings ([#3683](https://github.com/yschimke/rc-players/issues/3683)) ([193ff57](https://github.com/yschimke/rc-players/commit/193ff5789e3669f22ab8ca20b88e8328b868c91b))
* address missed review feedback ([#3954](https://github.com/yschimke/rc-players/issues/3954)) ([989027b](https://github.com/yschimke/rc-players/commit/989027bb479c896b1af7c6b12f9b775b5af26c5e))
* address recent Codex review findings ([#3649](https://github.com/yschimke/rc-players/issues/3649)) ([a55e87a](https://github.com/yschimke/rc-players/commit/a55e87a991eefcf2a246ea477afc30726aeddd0d))
* Address recent review feedback ([#4075](https://github.com/yschimke/rc-players/issues/4075)) ([2989cad](https://github.com/yschimke/rc-players/commit/2989cadc8329db014ed4c01b60babf9e097761ac))
* **build:** restore JVM checks ([#4038](https://github.com/yschimke/rc-players/issues/4038)) ([538ebe8](https://github.com/yschimke/rc-players/commit/538ebe84e85ff486201eeb9dcd34cd0841e209df))
* **build:** stop publishing the vendored embedded player, and record its package collision ([#4217](https://github.com/yschimke/rc-players/issues/4217)) ([9305e2d](https://github.com/yschimke/rc-players/commit/9305e2de38c828c04d2a5ea51b61e93b61779650))
* **ci:** bound the changelog with last-release-sha, not bootstrap-sha ([#18](https://github.com/yschimke/rc-players/issues/18)) ([12053eb](https://github.com/yschimke/rc-players/commit/12053ebfc31606b08d47d6edaa40c1ccf5dd26db))
* **ci:** grant the release call the permissions it needs ([#13](https://github.com/yschimke/rc-players/issues/13)) ([0ad6dee](https://github.com/yschimke/rc-players/commit/0ad6dee1deadb7b31171f2c5df289e83bdc93f3e))
* **ci:** make release-please cut a tagged release from this repo's own history ([#16](https://github.com/yschimke/rc-players/issues/16)) ([a0f63b3](https://github.com/yschimke/rc-players/commit/a0f63b32dadfa38f5bc37df4221f14dac387f606))
* **ci:** report missing publishing credentials by name, not as a 401 ([#12](https://github.com/yschimke/rc-players/issues/12)) ([4cab6a1](https://github.com/yschimke/rc-players/commit/4cab6a1cddb0b52d85bc586ae9ac4a1785d10a17))
* continue the published version line, and publish the JS player bundle ([1c40628](https://github.com/yschimke/rc-players/commit/1c40628841e0bf6cad9bf4f64449b3154441a9f5))
* **deps:** bump Compose Multiplatform to 1.11.1 so serve can render 1.11 catalogs ([#3462](https://github.com/yschimke/rc-players/issues/3462)) ([46dbdd9](https://github.com/yschimke/rc-players/commit/46dbdd90f1d53b5cbbaf98a246d9ef5c8ac6374b))
* **deps:** move Remote Compose back to released coordinates ([#4538](https://github.com/yschimke/rc-players/issues/4538)) ([8a029a6](https://github.com/yschimke/rc-players/commit/8a029a60527eab24a10599c43373a3d798c5a6e6))
* **deps:** update gradle minor/patch ([#3134](https://github.com/yschimke/rc-players/issues/3134)) ([854c6e4](https://github.com/yschimke/rc-players/commit/854c6e4822061d760acd1a42d203ac8de0888ef4))
* **deps:** update gradle minor/patch ([#3859](https://github.com/yschimke/rc-players/issues/3859)) ([49e7adf](https://github.com/yschimke/rc-players/commit/49e7adf0cdce462fc6d5d680f4fc78d96bbe2447))
* **design-artifacts:** unblock the remote-m3 CMP/Wasm parity gate ([#3476](https://github.com/yschimke/rc-players/issues/3476)) ([234d7e3](https://github.com/yschimke/rc-players/commit/234d7e31f334f98c4d58222caef877b8a45c3f33))
* export structural SVG from RC-JVM ([#3253](https://github.com/yschimke/rc-players/issues/3253)) ([3745a44](https://github.com/yschimke/rc-players/commit/3745a44ec052758ace1733c808023f07a8ba1788))
* **figma-svg:** read FontFamily.Default as an unstated family ([#3319](https://github.com/yschimke/rc-players/issues/3319)) ([a2b2cca](https://github.com/yschimke/rc-players/commit/a2b2cca6d45d79ec6361b84a213412b0196a46f7))
* green up the four CI failures on main ([#3688](https://github.com/yschimke/rc-players/issues/3688)) ([71e1f0c](https://github.com/yschimke/rc-players/commit/71e1f0c89852d780d205ad0b6372fe69268f50b8))
* **parity:** pin the CMP/Wasm parity lane's wall clock ([#4462](https://github.com/yschimke/rc-players/issues/4462)) ([cc949ad](https://github.com/yschimke/rc-players/commit/cc949ad4993a5343814de2f687cee9af7fa82701))
* raise the version floor to 1.54.0 ([ae3a6fc](https://github.com/yschimke/rc-players/commit/ae3a6fc98bee34b34eaa55f8a40a1c53aea5b053))
* **rc-compare:** honor document generation density ([#3621](https://github.com/yschimke/rc-players/issues/3621)) ([47a96d7](https://github.com/yschimke/rc-players/commit/47a96d721153b049cc689461e4f8b06bd718069c))
* **rc-compare:** render external image placeholders ([#3625](https://github.com/yschimke/rc-players/issues/3625)) ([ddc20cc](https://github.com/yschimke/rc-players/commit/ddc20ccbc361ad172067740717b4655bd1ab1aa1))
* **rc-compare:** report CMP/Wasm pixel parity, guard regressions on the PR ([#3492](https://github.com/yschimke/rc-players/issues/3492)) ([001da45](https://github.com/yschimke/rc-players/commit/001da455b716327042206bc1f8dc99d65bc4dd17))
* **rc-compare:** stop scoring a blank CMP/Wasm capture as a parity number ([#3597](https://github.com/yschimke/rc-players/issues/3597)) ([0243c7a](https://github.com/yschimke/rc-players/commit/0243c7a58dc8ef426fe35d4bb81de0f8ff07ab8a))
* **rc-compare:** uncap the compositor frame rate so short viewports are not scored as slow ([#3459](https://github.com/yschimke/rc-players/issues/3459)) ([4de0ad0](https://github.com/yschimke/rc-players/commit/4de0ad03cac54b2e9be07ecab67bb7a83688006b))
* **rc-embedded-player:** clip inside layout modifiers, not outside them ([#4008](https://github.com/yschimke/rc-players/issues/4008)) ([74962d5](https://github.com/yschimke/rc-players/commit/74962d50f3ad58bd70f11b953822629a73176066))
* **rc-embedded-player:** evaluate paint-channel ops so dynamic colours resolve ([#3977](https://github.com/yschimke/rc-players/issues/3977)) ([ff61d9c](https://github.com/yschimke/rc-players/commit/ff61d9c15ac9e0b3d7c95f6a942e4f40ba080813))
* **rc-embedded-player:** isolate bitmap decode failures ([#4000](https://github.com/yschimke/rc-players/issues/4000)) ([2d25c1b](https://github.com/yschimke/rc-players/commit/2d25c1b4618c28c8267cbb3ef7df5f9c96246ea7)), closes [#3993](https://github.com/yschimke/rc-players/issues/3993)
* **rc-embedded-player:** let the composition reach idle, drop dead autoUpdate ([#2945](https://github.com/yschimke/rc-players/issues/2945)) ([f5daede](https://github.com/yschimke/rc-players/commit/f5daede66facf78652ac1343d9399f53c577d372))
* **rc-embedded-player:** let the graph read measured component sizes ([#3995](https://github.com/yschimke/rc-players/issues/3995)) ([5858161](https://github.com/yschimke/rc-players/commit/58581610c757edc2da82e975dcfe9e7d79d8f2ad))
* **rc-embedded-player:** skip nested bitmap setup ([#4020](https://github.com/yschimke/rc-players/issues/4020)) ([41b3def](https://github.com/yschimke/rc-players/commit/41b3def2929be91284bc52989d61bfd575548563))
* **rc-embedded:** density-scale literal clip-corner radii ([#3032](https://github.com/yschimke/rc-players/issues/3032)) ([c0c5267](https://github.com/yschimke/rc-players/commit/c0c5267e652b1cdbb65b8134447aa00aacd61996))
* **rc-embedded:** draw component chrome once, not twice ([#3037](https://github.com/yschimke/rc-players/issues/3037)) ([42c7605](https://github.com/yschimke/rc-players/commit/42c7605fd89dcaa957d4f26c8896a62aac8c2468))
* **rc-embedded:** let both embedded lanes parse URL-encoded bitmaps ([#3598](https://github.com/yschimke/rc-players/issues/3598)) ([dc90cdc](https://github.com/yschimke/rc-players/commit/dc90cdc3cef5aa5e598a2ede31ed26805fab2181))
* **rc-embedded:** match rounded clip density ([#3162](https://github.com/yschimke/rc-players/issues/3162)) ([7c850cb](https://github.com/yschimke/rc-players/commit/7c850cb5c78932799493fb82bf05d0a328af0671))
* **rc-embedded:** resolve a gradient's bound colour-id stop in the embedded player ([#3019](https://github.com/yschimke/rc-players/issues/3019)) ([b126786](https://github.com/yschimke/rc-players/commit/b1267866a1da95b8ae1924e4bb76faaf53f7131c))
* **rc-embedded:** stop the guard certifying a file that can't move yet ([#2936](https://github.com/yschimke/rc-players/issues/2936)) ([5ae2945](https://github.com/yschimke/rc-players/commit/5ae2945cdece0249709b55f5568c204813a33f04))
* **rc-harness:** validate manifest-complete A/B runs ([#4036](https://github.com/yschimke/rc-players/issues/4036)) ([d467918](https://github.com/yschimke/rc-players/commit/d46791883a315baab53ee8954cf6e22de2c53dfd))
* **rc-js-player:** request a second document's axis, and re-measure for it ([#4180](https://github.com/yschimke/rc-players/issues/4180)) ([88b3406](https://github.com/yschimke/rc-players/commit/88b3406a74dcb7c298078a30478992bc63118aa5))
* **rc-player:** align AndroidX operation semantics ([#3636](https://github.com/yschimke/rc-players/issues/3636)) ([d3ca7ef](https://github.com/yschimke/rc-players/commit/d3ca7ef3583052467ccbb8a526fa292248c08aa7))
* **rc-player:** align text measurement across players ([#3653](https://github.com/yschimke/rc-players/issues/3653)) ([7a608ae](https://github.com/yschimke/rc-players/commit/7a608ae0eabf692283ceb82174900009558f8ed5))
* **rc-player:** capture Remote Compose docs in Dp behavior + stamp generation density ([#2760](https://github.com/yschimke/rc-players/issues/2760)) ([4ce9088](https://github.com/yschimke/rc-players/commit/4ce908873b32d7ba1af84cdef4cdf20c8a6049f9))
* **rc-player:** evaluate content-state operations declared at the document root ([#4221](https://github.com/yschimke/rc-players/issues/4221)) ([f11f151](https://github.com/yschimke/rc-players/commit/f11f1517ff3cb278a598468eef5022def02810ea))
* **rc-player:** evaluate layout modifier colors ([#3623](https://github.com/yschimke/rc-players/issues/3623)) ([9392b74](https://github.com/yschimke/rc-players/commit/9392b742d6915e4b0ef070678f58f64acc605f1a))
* **rc-player:** follow a replaced named-value holder, and rebuild semantics on invalidation ([#4213](https://github.com/yschimke/rc-players/issues/4213)) ([109b259](https://github.com/yschimke/rc-players/commit/109b2593ad7ef981217d0c3543dd1e925c4e3ed2))
* **rc-player:** honour the size a component was asked for in the JS player ([#3474](https://github.com/yschimke/rc-players/issues/3474)) ([6827fea](https://github.com/yschimke/rc-players/commit/6827fea7e4964bf21a9934ce058e9fb952e860f9))
* **rc-player:** implement TEXT_LOOKUP_INT and fix integer-expression ids ([#3427](https://github.com/yschimke/rc-players/issues/3427)) ([86c780f](https://github.com/yschimke/rc-players/commit/86c780ffe9e6aa7f9aafbf7896fec0d31804bca2))
* **rc-player:** instance font axes on the variable file in the view lane ([#3503](https://github.com/yschimke/rc-players/issues/3503)) ([6b209b4](https://github.com/yschimke/rc-players/commit/6b209b4a73bb1a8b5723b5d17e5eaef4f13a9469))
* **rc-player:** keep canvas decorations outside padding ([#3648](https://github.com/yschimke/rc-players/issues/3648)) ([4e3a5d1](https://github.com/yschimke/rc-players/commit/4e3a5d1e5a81d6c9ae41d18da66f8056d6b714cf))
* **rc-player:** load the player-supplied system variables every frame ([#4267](https://github.com/yschimke/rc-players/issues/4267)) ([0bcdedc](https://github.com/yschimke/rc-players/commit/0bcdedce07a327e801e705472f33d3146d2e622d))
* **rc-player:** make dynamic color diagnostics reproducible ([#4037](https://github.com/yschimke/rc-players/issues/4037)) ([a2b9656](https://github.com/yschimke/rc-players/commit/a2b9656fa2162ae15cb659bf6b6a5923e43bae12))
* **rc-player:** match Java text layout modes ([#3663](https://github.com/yschimke/rc-players/issues/3663)) ([432e73d](https://github.com/yschimke/rc-players/commit/432e73d3400eebe02fa0563df5749a055bbdc383))
* **rc-player:** mirror integer values into float state ([#3632](https://github.com/yschimke/rc-players/issues/3632)) ([03bbd64](https://github.com/yschimke/rc-players/commit/03bbd640c9406e5c74eeb75248d3a63c13abe0c7))
* **rc-player:** named-value changes rebuild RcPlayerState and discard animation and touch state ([#4181](https://github.com/yschimke/rc-players/issues/4181)) ([db66e7a](https://github.com/yschimke/rc-players/commit/db66e7a8143958684a2f2cd4dda4c758bcb31760))
* **rc-player:** only adopt same-component ComponentValues nested in containers ([#2751](https://github.com/yschimke/rc-players/issues/2751)) ([2b05832](https://github.com/yschimke/rc-players/commit/2b05832056f35accab31a636141b4fbd0bd39f0c))
* **rc-player:** paint drawWithContent fills at full component bounds ([#2755](https://github.com/yschimke/rc-players/issues/2755)) ([80e5604](https://github.com/yschimke/rc-players/commit/80e5604e48951b21098d20c6a9f040385c73c92e))
* **rc-player:** paint TEXT_LAYOUT (208) instead of silently dropping it ([#2905](https://github.com/yschimke/rc-players/issues/2905)) ([9fab702](https://github.com/yschimke/rc-players/commit/9fab70247c9c201f7dcad89c567d68c51bfedc52))
* **rc-player:** parse the accessibility-semantics op (250) instead of truncating ([#2734](https://github.com/yschimke/rc-players/issues/2734)) ([c21e87f](https://github.com/yschimke/rc-players/commit/c21e87fc7684de8da84f33bf76fab7e528d68788))
* **rc-player:** preserve JVM text correctness ([#4039](https://github.com/yschimke/rc-players/issues/4039)) ([54606af](https://github.com/yschimke/rc-players/commit/54606aff9daf14889c0590b2392ce5d1048fa6b9))
* **rc-player:** preserve lexical theme and canvas scope ([#3655](https://github.com/yschimke/rc-players/issues/3655)) ([e090fe7](https://github.com/yschimke/rc-players/commit/e090fe7ec177c922623a436407f8342dc308a318))
* **rc-player:** refresh the vendored player to 53e19e93 and keep weighted children measurable ([#3465](https://github.com/yschimke/rc-players/issues/3465)) ([6bbdb14](https://github.com/yschimke/rc-players/commit/6bbdb14e8fddecf21954645cf2f88d58c33be711))
* **rc-player:** render text published by lookup operations on the CMP/Wasm lane ([#3461](https://github.com/yschimke/rc-players/issues/3461)) ([f403a62](https://github.com/yschimke/rc-players/commit/f403a62932ce16d14037330b4b94c48f43d6e09f))
* **rc-player:** render tinted icons ([#3937](https://github.com/yschimke/rc-players/issues/3937)) ([b33addf](https://github.com/yschimke/rc-players/commit/b33addfa285ac64034a34af210aa47b2f9bf2a67))
* **rc-player:** replay layout color attributes ([#3629](https://github.com/yschimke/rc-players/issues/3629)) ([58a8cde](https://github.com/yschimke/rc-players/commit/58a8cde687acb37225b7950567f3d04df05225ae))
* **rc-player:** replay root layout state operations ([#3631](https://github.com/yschimke/rc-players/issues/3631)) ([fed7145](https://github.com/yschimke/rc-players/commit/fed7145634acc55f928cba2703941aeda4fc5e02))
* **rc-player:** resolve branded typefaces in both embedded lanes ([#4174](https://github.com/yschimke/rc-players/issues/4174)) ([87d9cde](https://github.com/yschimke/rc-players/commit/87d9cdeef64031a613eea02d0e5789b7aa45885e))
* **rc-player:** resolve ColorTheme indices and modes in every player ([#3962](https://github.com/yschimke/rc-players/issues/3962)) ([bdb4e10](https://github.com/yschimke/rc-players/commit/bdb4e1076a3fb83b980db7e0dbd12aabfabd284c))
* **rc-player:** resolve colours computed in a draw-content block ([#4171](https://github.com/yschimke/rc-players/issues/4171)) ([62b72c4](https://github.com/yschimke/rc-players/commit/62b72c4fc9aca00232dc5cea8c7c42122d47ee1f))
* **rc-player:** resolve dynamic path coords + content-wrapper sizes ([#2753](https://github.com/yschimke/rc-players/issues/2753)) ([382e087](https://github.com/yschimke/rc-players/commit/382e087e8c89a7f108fb53c823aeb44def888463))
* **rc-player:** resolve embedded theme colors ([#3952](https://github.com/yschimke/rc-players/issues/3952)) ([e3e508e](https://github.com/yschimke/rc-players/commit/e3e508ecc851f85ad9ccaf695a28c9b036229640))
* **rc-player:** resolve size-relative corner radii on MODIFIER_ROUNDED_CLIP_RECT ([#2978](https://github.com/yschimke/rc-players/issues/2978)) ([b09e39c](https://github.com/yschimke/rc-players/commit/b09e39c3a5a25a13bd2aeea1ff828737bd1a898d))
* **rc-player:** resolve the default font family and honour requested weight ([#3468](https://github.com/yschimke/rc-players/issues/3468)) ([dc51062](https://github.com/yschimke/rc-players/commit/dc51062a78fc9755bdd2342daaa86487c3681b82))
* **rc-player:** scale dp-typed size modifiers by generation density ([#2757](https://github.com/yschimke/rc-players/issues/2757)) ([fe8cd1e](https://github.com/yschimke/rc-players/commit/fe8cd1e6dfa39f55d46350c97f7629477314c786))
* **rc-player:** stop applying padding twice at density != 1 ([#4727](https://github.com/yschimke/rc-players/issues/4727)) ([60c7710](https://github.com/yschimke/rc-players/commit/60c771094f4cfe9c11b5b86f3f5a9285c7392da6))
* **rc-player:** stop doubling the rounded clip radius at density != 1 ([#4710](https://github.com/yschimke/rc-players/issues/4710)) ([72cb7d0](https://github.com/yschimke/rc-players/commit/72cb7d070d69827dc7d973e9ba1bf13471e7332c))
* **rc-player:** stop the CMP player doubling padding at density != 1 ([#4766](https://github.com/yschimke/rc-players/issues/4766)) ([f6a9ad3](https://github.com/yschimke/rc-players/commit/f6a9ad3810e6e1bbed695273978cbde5c22fdbb2))
* **rc-player:** stop the CMP player doubling the rounded clip radius ([#4744](https://github.com/yschimke/rc-players/issues/4744)) ([e6439e4](https://github.com/yschimke/rc-players/commit/e6439e4a752fc3dcb3e3439758b9855ba68c7944))
* **rc-player:** track diagnostic task output ([#4045](https://github.com/yschimke/rc-players/issues/4045)) ([f3e00ff](https://github.com/yschimke/rc-players/commit/f3e00ffd8b98b36cda67b1443a3ca2887ebdd35d))
* **rc-player:** un-strand RcNamedValueRenderTest from the old source set ([#4186](https://github.com/yschimke/rc-players/issues/4186)) ([c95f18f](https://github.com/yschimke/rc-players/commit/c95f18f0a1d7d4920c8f4e491e4e631540c41009))
* **rc-player:** unrepresentable named types, colliding font identities, swallowed cancellation ([#4198](https://github.com/yschimke/rc-players/issues/4198)) ([4a0cfe2](https://github.com/yschimke/rc-players/commit/4a0cfe271e04b7c28aafc127d189ee5575bb308c))
* **release:** check the Swift tag before writing, and compute one snapshot version ([#4208](https://github.com/yschimke/rc-players/issues/4208)) ([dcff72e](https://github.com/yschimke/rc-players/commit/dcff72e6f0d46089a4d6111118ed195b749fd6c0))
* **release:** publish Apple targets from macOS, and make the Swift tag resolvable ([#4197](https://github.com/yschimke/rc-players/issues/4197)) ([3f0040e](https://github.com/yschimke/rc-players/commit/3f0040e998d9a1b41c16af31ab60b2db4c325382))
* **remote-compose-player:** honor DP density behavior for padding ([#2768](https://github.com/yschimke/rc-players/issues/2768)) ([21ad959](https://github.com/yschimke/rc-players/commit/21ad959f3f578623ec5cd5622d98586ccdca460c))
* **remote-compose-player:** scale corners, spacing, offset, border under DP density ([#2769](https://github.com/yschimke/rc-players/issues/2769)) ([a8a722c](https://github.com/yschimke/rc-players/commit/a8a722c2a17bdc6dc31d998c756c801519c9cd63))
* **serve:** register the vendored typefaces for the browser Remote Compose lane ([#3507](https://github.com/yschimke/rc-players/issues/3507)) ([02a8b6c](https://github.com/yschimke/rc-players/commit/02a8b6c87005a170669aa0d68f3dd336c315ac33))
* **serve:** survive a shadowed embedded player, and stop starving theme optimization ([#4464](https://github.com/yschimke/rc-players/issues/4464)) ([2dd39c7](https://github.com/yschimke/rc-players/commit/2dd39c7997c3504d24df933cb1beccb6d0500d6f))


### Performance Improvements

* **rc-compare:** hand the CMP/Wasm player each document in place, and capture on convergence ([#3508](https://github.com/yschimke/rc-players/issues/3508)) ([cfb2536](https://github.com/yschimke/rc-players/commit/cfb2536d5f8a66298c3964cb832351a867296f1b))
* **rc-compare:** let the parity lane skip the player's snapshot-handoff tail ([#3466](https://github.com/yschimke/rc-players/issues/3466)) ([7db26b0](https://github.com/yschimke/rc-players/commit/7db26b0fa7988c6bff8c7f1435682b98cd6f8bc2))
* **serve:** pool the cmp-jvm render workers instead of a JVM per document ([#3514](https://github.com/yschimke/rc-players/issues/3514)) ([8c3c828](https://github.com/yschimke/rc-players/commit/8c3c8287c75516633dfdced014e7dd7d5c517b2a))

## Changelog

Releases of the Remote Compose players. The line continues compose-ai-tools', which published these
coordinates through 1.54.0 — see that repository's changelog for anything before the extraction.
