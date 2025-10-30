package com.call.janmapping

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "search") {
        composable("search") {
            SearchScreen(
                onOpenMap = { jans, productNames ->
                    val jansCsv = jans.joinToString(",")
                    val productsCsv = productNames.joinToString(",")
                    Log.d("AppNav", "navigate map jans='$jansCsv', products='$productsCsv'")
                    nav.navigate("map?jans=$jansCsv&products=$productsCsv")
                }
            )
        }
        composable(
            route = "map?jans={jans}&products={products}",
            arguments = listOf(
                navArgument("jans") { type = NavType.StringType; defaultValue = "" },
                navArgument("products") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStack ->
            val jansCsv = backStack.arguments?.getString("jans") ?: ""
            val productsCsv = backStack.arguments?.getString("products") ?: ""
            val jans: List<String> = jansCsv.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val productNames: List<String> = productsCsv.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            Log.d("AppNav", "map route jans parsed size=${jans.size}, products size=${productNames.size}")
            MapScreen(jans = jans, productNames = productNames)
        }
    }
}
