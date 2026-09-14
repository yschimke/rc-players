package ee.schimke.composeai.rcplayer.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcNativeImageResourceTest {
  @Test
  fun publishedImageButtonExportsNativeImages() {
    val bytes =
      checkNotNull(
          javaClass.getResourceAsStream("/rc-fixtures/ImageBackgroundRemoteButton-454x200.rc")
        )
        .use { it.readBytes() }
    val snapshot = RcNativeSnapshotBridge.decode(bytes)

    assertEquals(1, snapshot.images.size)
    assertEquals(8, snapshot.images.single().width)
    assertEquals(8, snapshot.images.single().height)
    assertTrue(snapshot.root.children.isNotEmpty())
    assertTrue(
      snapshot.root
        .descendants()
        .flatMap { it.commands }
        .any { it.image != null || it.textureImageId == snapshot.images.single().id }
    )
  }

  private fun RcNativeNodeSnapshot.descendants(): Sequence<RcNativeNodeSnapshot> =
    sequenceOf(this) + children.asSequence().flatMap { it.descendants() }
}
