package com.yasharya.attendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yasharya.attendance.theme.AttendanceTheme
import com.yasharya.attendance.ui.AttendanceNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before setContent, so the first frame is already drawn edge to edge
        // rather than resizing once the theme is applied.
        enableEdgeToEdge()
        setContent {
            AttendanceTheme {
                AttendanceNavHost()
            }
        }
    }
}
