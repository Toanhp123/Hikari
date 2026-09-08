package app.openstory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.ui.HikariBootSurface
import app.openstory.ui.HikariBootTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HikariBootTheme {
                HikariBootSurface {
                    Text(
                        text = "Hikari",
                        modifier = Modifier.testTag("startup-shell"),
                    )
                }
            }
        }
    }
}
