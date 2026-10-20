package edu.saihs.tripclip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import edu.saihs.tripclip.ui.navigation.TripClipNavDisplay
import edu.saihs.tripclip.ui.theme.TripClipTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TripClipTheme {
                TripClipNavDisplay()
            }
        }
    }
}
