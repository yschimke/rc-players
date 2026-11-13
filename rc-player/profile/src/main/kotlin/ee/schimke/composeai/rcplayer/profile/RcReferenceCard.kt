package ee.schimke.composeai.rcplayer.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A conference session card written directly in Compose UI, with the shape of the session cards
 * Livery templates draw (time, a live badge, a room, a title, speakers, a tag, a bookmark): the
 * baseline a card played from a document is measured against in [RcFeedProfile].
 */
@Composable
internal fun RcReferenceCard(item: Int, modifier: Modifier) {
  Row(
    modifier.background(Color(0xFFEFDDE6), RoundedCornerShape(12.dp)).clickable {}.padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText("10:30 – 11:10", style = TextStyle(Color(0xFF8E3C8E), 14.sp, FontWeight.Bold))
        Box(
          Modifier.padding(horizontal = 8.dp)
            .background(Color(0xFFB4634B), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
          BasicText("Live", style = TextStyle(Color.White, 12.sp, FontWeight.Bold))
        }
        BasicText("Hall 1", style = TextStyle(Color(0xFF7A7078), 14.sp))
      }
      BasicText(
        "Session $item: building adaptive apps with Compose",
        style = TextStyle(Color(0xFF1E1A1D), 18.sp, FontWeight.Bold),
        maxLines = 2,
      )
      BasicText("Jane Doe, John Smith", style = TextStyle(Color(0xFF7A7078), 14.sp))
      Box(
        Modifier.background(Color(0xFFC8C9D6), RoundedCornerShape(8.dp))
          .padding(horizontal = 12.dp, vertical = 4.dp),
      ) {
        BasicText("compose", style = TextStyle(Color(0xFF1F6A80), 13.sp, FontWeight.Bold))
      }
    }
    Box(
      Modifier.size(40.dp).border(2.dp, Color(0xFF8E3C8E), CircleShape).clickable {},
      contentAlignment = Alignment.Center,
    ) {
      BasicText("★", style = TextStyle(Color(0xFF8E3C8E), 20.sp))
    }
  }
}
