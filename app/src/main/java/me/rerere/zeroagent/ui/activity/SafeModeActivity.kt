package me.rerere.zeroagent.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import me.rerere.zeroagent.ui.components.ui.ZeroAgentCard as Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.rerere.zeroagent.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.zeroagent.ui.theme.ZeroAgentTheme
import me.rerere.zeroagent.RouteActivity
import me.rerere.zeroagent.utils.CrashHandler

class SafeModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stackTrace = CrashHandler.getStackTrace(this)
        CrashHandler.clearCrashed(this)
        enableEdgeToEdge()
        setContent {
            ZeroAgentTheme {
                val context = LocalContext.current

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            colors = me.rerere.zeroagent.ui.theme.CustomColors.topBarColors,title = { Text(stringResource(R.string.safe_mode_title)) })
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.safe_mode_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            shapes = ButtonDefaults.shapes(), onClick = {
                                startActivity(Intent(this@SafeModeActivity, RouteActivity::class.java))
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.safe_mode_enter_app))
                        }

                        if (stackTrace != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.safe_mode_crash_report),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                OutlinedButton(
                                    shapes = ButtonDefaults.shapes(), onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("crash", stackTrace))
                                    }
                                ) {
                                    Text(stringResource(R.string.safe_mode_copy))
                                }
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                val vScroll = rememberScrollState()
                                val hScroll = rememberScrollState()
                                Text(
                                    text = stackTrace,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .verticalScroll(vScroll)
                                        .horizontalScroll(hScroll),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}
