package com.gatekeeper.app.ui.gate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.gatekeeper.app.reader.ReaderActivity
import com.gatekeeper.app.ui.theme.GatekeeperTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The full-screen gate (R-2.3/R-2.4). Launched by the AccessibilityService the
 * moment a blacklisted app comes to the foreground. Passing the gate grants a
 * timed session for that one app and relaunches it; backing out goes Home so
 * the distracting app is never reachable without paying the toll.
 */
@AndroidEntryPoint
class GateActivity : ComponentActivity() {

    private val viewModel: GateViewModel by viewModels()

    private val readerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pages = result.data?.getIntExtra(ReaderActivity.RESULT_PAGES_READ, 0) ?: 0
            viewModel.onReadingResult(pages, passed = result.resultCode == RESULT_OK)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startGate(intent)

        // Back must not reveal the gated app underneath — go Home instead.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            GatekeeperTheme {
                GateScreen(
                    viewModel = viewModel,
                    onOpenBook = { bookId ->
                        readerLauncher.launch(
                            ReaderActivity.intent(
                                this,
                                bookId = bookId,
                                gateMode = true,
                                requiredPages = viewModel.pagesAlreadyRead() + viewModel.pagesStillNeeded(),
                                carriedPages = viewModel.pagesAlreadyRead(),
                            )
                        )
                    },
                    onPassed = { launchGatedApp() },
                    onGiveUp = { goHome() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: the user may have switched straight to a different gated app.
        setIntent(intent)
        startGate(intent)
    }

    private fun startGate(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg) // stale/uninstalled packages must not crash (R-3.4)
        viewModel.start(pkg, label)
    }

    private fun launchGatedApp() {
        val launch = packageManager.getLaunchIntentForPackage(viewModel.packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(launch)
        }
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"

        fun intent(context: Context, packageName: String): Intent =
            Intent(context, GateActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
    }
}
