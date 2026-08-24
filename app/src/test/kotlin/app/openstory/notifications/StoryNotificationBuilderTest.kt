package app.openstory.notifications

import android.app.PendingIntent
import android.content.Intent
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryNotificationBuilderTest {
    @Test
    fun boundsAndSanitizesUserControlledLabels() {
        val context = RuntimeEnvironment.getApplication()
        val notification = StoryNotificationBuilder(context).build(
            ValidatedChapterNotificationTarget(
                StoryId("story:1"),
                CanonicalChapterId("chapter:1"),
                null,
                "S".repeat(100) + "\nsecret",
                "C".repeat(100) + "\u0000secret",
                null,
                PendingIntent.getActivity(
                    context,
                    1,
                    Intent(context, app.openstory.MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ),
        )
        val content = shadowOf(notification).contentText.toString()
        assertTrue(content.length <= 80 + 3 + 64)
        assertFalse(content.any(Char::isISOControl))
        assertFalse("secret" in content)
    }
}
