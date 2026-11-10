package com.demomart.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demomart.client.R
import com.demomart.client.account.key
import com.demomart.client.net.HistoryItem
import com.demomart.client.ui.formatEur
import com.demomart.client.ui.formatPoints
import com.demomart.client.ui.formatPointsDelta
import com.demomart.client.ui.theme.Cream
import com.demomart.client.ui.theme.DeepGreen
import com.demomart.client.ui.theme.EarnGreen
import com.demomart.client.ui.theme.RedeemOrange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val historyDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onCard: () -> Unit, onSignOut: () -> Unit) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    val firstName = account?.displayName?.trim()?.substringBefore(' ')?.ifEmpty { "there" } ?: "there"

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hi, $firstName") },
                actions = {
                    IconButton(onCard) { Icon(painterResource(R.drawable.ic_card), "My card") }
                    Box {
                        IconButton({ menuOpen = true }) { Icon(Icons.Default.MoreVert, "Account options") }
                        DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Sign out") }, onClick = {
                                menuOpen = false
                                onSignOut()
                            })
                        }
                    }
                },
            )
        },
    ) { insets ->
        PullToRefreshBox(refreshing, viewModel::refresh, Modifier.fillMaxSize().padding(insets)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(Brush.horizontalGradient(listOf(DeepGreen, Color(0xFF2F7A5F))))
                            .padding(22.dp),
                    ) {
                        Text("Your balance", color = Cream, style = MaterialTheme.typography.labelLarge)
                        val balance = account?.balance
                        Text(balance?.let(::formatPoints) ?: "—", color = Cream,
                            style = MaterialTheme.typography.displaySmall)
                        balance?.let {
                            Text("worth ${formatEur(it)} at the till", color = Cream)
                        }
                    }
                }
                error?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                item { Text("Recent", style = MaterialTheme.typography.titleLarge) }
                val history = account?.history.orEmpty()
                if (history.isEmpty() && account?.refreshedAt != null) {
                    item { Text("Your purchases will show up here") }
                }
                items(history, key = { it.key }) { HistoryRow(it) }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem) {
    val date = try {
        Instant.parse(item.at).atZone(ZoneId.systemDefault()).format(historyDate)
    } catch (_: DateTimeParseException) {
        item.at
    }
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (item.kind == "refund") "Refund" else "Purchase ${formatEur(item.paidCents)}")
                Text(date, style = MaterialTheme.typography.bodySmall)
            }
            if (item.kind == "sale" && item.redeemedPoints > 0) {
                Text(formatPoints(-item.redeemedPoints).removeSuffix(" pts"), color = RedeemOrange)
                Text("·")
                Text(formatPointsDelta(item.earnedPoints), color = EarnGreen)
            } else {
                Text(
                    formatPointsDelta(item.pointsDelta),
                    color = if (item.pointsDelta < 0) RedeemOrange else EarnGreen,
                )
            }
        }
    }
}
