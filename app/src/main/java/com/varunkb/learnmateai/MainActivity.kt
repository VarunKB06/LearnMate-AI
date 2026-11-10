package com.varunkb.learnmateai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.varunkb.learnmateai.ui.Forest
import com.varunkb.learnmateai.ui.ForestDark
import com.varunkb.learnmateai.ui.Ink
import com.varunkb.learnmateai.ui.LearnMateRoot
import com.varunkb.learnmateai.ui.Page

/**
 * Thin host activity. State lives in [MainViewModel] (survives rotation), data lives in Room,
 * learning logic lives in the pure-Kotlin domain package.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Forest,
                    onPrimary = Color.White,
                    secondary = ForestDark,
                    background = Page,
                    surface = Color.White,
                    onSurface = Ink
                )
            ) {
                LearnMateRoot(viewModel)
            }
        }
    }
}
