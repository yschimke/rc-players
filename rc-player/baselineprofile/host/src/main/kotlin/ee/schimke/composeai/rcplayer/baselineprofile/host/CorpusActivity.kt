package ee.schimke.composeai.rcplayer.baselineprofile.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer

/**
 * Plays the catalog corpus in one scrolling list, so a single fling journey reaches every kind of
 * component the players decode, lay out and draw. The first document of each component kind comes
 * first, so even a short scroll covers the breadth of the corpus before its variants.
 */
class CorpusActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val documents = corpusDocuments()
    setContent {
      LazyColumn(
        modifier =
          Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.testTag("corpus")
      ) {
        items(documents, key = { it }) { name ->
          val bytes = remember(name) { assets.open(name).use { it.readBytes() } }
          Column(Modifier.fillMaxWidth().padding(8.dp)) {
            BasicText(name)
            RcComposePlayer(
              bytes = bytes,
              modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
            )
          }
        }
      }
    }
  }

  private fun corpusDocuments(): List<String> {
    val all = assets.list("").orEmpty().filter { it.endsWith(".rc") }.sorted()
    val (leaders, variants) =
      all.withIndex().partition { (i, name) ->
        i == 0 || name.substringBefore("__") != all[i - 1].substringBefore("__")
      }
    return leaders.map { it.value } + variants.map { it.value }
  }
}
