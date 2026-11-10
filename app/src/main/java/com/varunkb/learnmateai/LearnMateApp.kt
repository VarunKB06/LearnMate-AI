package com.varunkb.learnmateai

import android.app.Application
import com.varunkb.learnmateai.data.AppContainer

class LearnMateApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
