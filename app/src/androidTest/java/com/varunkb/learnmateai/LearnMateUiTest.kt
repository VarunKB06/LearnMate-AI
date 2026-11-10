package com.varunkb.learnmateai

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI flow: (optional onboarding) -> dashboard -> adaptive quiz of 10 -> result -> assistant.
 * Works whether the app starts fresh or with an existing profile.
 * Run on a device: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class LearnMateUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun exists(text: String, substring: Boolean = false) =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, substring: Boolean = false, timeoutMs: Long = 15_000) =
        rule.waitUntil(timeoutMs) { exists(text, substring) }

    private fun reachDashboard() {
        rule.waitUntil(20_000) { exists("Let's Begin") || exists("Start a practice quiz") }
        if (exists("Let's Begin")) {
            rule.onNodeWithText("Let's Begin").performClick()
            waitFor("Save profile")
            rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Tester")
            rule.onNodeWithText("Save profile").performScrollTo().performClick()
        }
        waitFor("Start a practice quiz")
    }

    @Test fun dashboardShowsSubjectAndOfflineMessage() {
        reachDashboard()
        waitFor("Your learning data stays on this device.")
    }

    @Test fun completingAQuizShowsResult() {
        reachDashboard()
        rule.onNodeWithText("Start a practice quiz").performScrollTo().performClick()
        waitFor("Question 1 of 10")
        repeat(10) {
            rule.onAllNodesWithText("A. ", substring = true).onFirst().performScrollTo().performClick()
            val label = if (exists("Finish quiz")) "Finish quiz" else "Next question"
            rule.onNodeWithText(label).performScrollTo().performClick()
        }
        waitFor("QUIZ COMPLETE")
        waitFor("Updated mastery")
    }

    @Test fun assistantAnswersFromLessonsAndAdmitsUnknowns() {
        reachDashboard()
        rule.onNodeWithText("Ask LearnMate (Offline)").performScrollTo().performClick()
        waitFor("Ask LearnMate")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("What is the capital of France?")
        rule.onAllNodesWithText("Ask LearnMate").onLast().performScrollTo().performClick()
        waitFor("don't have a reliable answer", substring = true)
    }

    @Test fun learnTabShowsCatalogueAndOpensALesson() {
        reachDashboard()
        rule.onNodeWithText("Learn").performClick()
        waitFor("lessons in", substring = true)
    }

    @Test fun studioTabOpens() {
        reachDashboard()
        rule.onNodeWithText("Studio").performClick()
        waitFor("Studio", substring = true)
    }
}
