package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.compose.composeSupportReport
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcOperationProfiles
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

/**
 * The 3D document the Wasm player's browser smoke run plays (`scripts/wasm-smoke/smoke.mjs`),
 * committed so the bundle can stage it without running the JVM: it must still be exactly what
 * [Rc3DDocuments.webDepthScene] encodes. After a deliberate change, rewrite it with `./gradlew
 * :rc-player-3d:jvmTest --tests '*Rc3DFixtureTest*' -Prc.3d.updateFixtures=true`.
 */
class Rc3DFixtureTest {
  @Test
  fun theCommittedWebFixtureIsCurrent() {
    val bytes = Rc3DDocuments.webDepthScene().encode()
    if (System.getProperty("rc.3d.updateFixtures") == "true") {
      File(FIXTURE_SOURCE).apply { parentFile.mkdirs() }.writeBytes(bytes)
    }
    val committed = javaClass.getResourceAsStream("/rc-fixtures/$NAME")?.readBytes()
    assertNotNull(committed, "src/jvmTest/resources/rc-fixtures/$NAME is missing")
    assertContentEquals(bytes, committed, "$NAME is stale; see this class's KDoc")
  }

  /**
   * The browser host checks a document against its profile before playing it; the 3D operations are
   * the extension's, not the profile's, so they must not count against it.
   */
  @Test
  fun aProfileDoesNotRefuseAnInstalledExtensionsOperations() {
    val document =
      RcDocumentCodec.decode(Rc3DDocuments.webDepthScene().encode(), Rc3DExtension.wireExtensions)
    document.composeSupportReport(RcOperationProfiles.CMP_WASM_ALPHA18).requireRenderable(false)
  }

  private companion object {
    const val NAME = "rc3d-depth-scene.rc"
    const val FIXTURE_SOURCE = "src/jvmTest/resources/rc-fixtures/$NAME"
  }
}
