package com.call.janmapping

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SearchScreen(
    onOpenMap: (jans: List<String>, productNames: List<String>) -> Unit,
    vm: SearchViewModel = viewModel()
) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        vm.goMap.collectLatest { mapData ->
            Log.d("SearchScreen", "goMap jans=${mapData.jans.size}, products=${mapData.productNames.size}")
            onOpenMap(mapData.jans, mapData.productNames)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("欲しいものは？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            FilledTonalButton(
                onClick = {
                    val intent = Intent(context, PaymentActivity::class.java)
                    context.startActivity(intent)
                }
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("決済")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (s.stage == SearchViewModel.Stage.CATEGORY) {
            OutlinedTextField(
                value = s.query,
                onValueChange = vm::onQueryChanged,
                label = { Text("例：しょっぱいもの / 甘いもの / あったかい飲み物") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::startSearch, enabled = s.query.isNotBlank()) {
                Text("候補を取得")
            }
        } else {
            Text(
                "絞り込み候補（商品名）を選んでください",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
        }

        s.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color.Red)
        }

        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(s.suggestions) { opt ->
                    ListItem(
                        headlineContent = { Text(opt.label) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectSuggestion(opt) }
                    )
                    Divider()
                }
            }
            if (s.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
