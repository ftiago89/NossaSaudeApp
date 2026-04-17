package com.example.nossasaudeapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nossasaudeapp.ui.home.HomeScreen
import com.example.nossasaudeapp.ui.consultation.ConsultationDetailScreen
import com.example.nossasaudeapp.ui.imageviewer.ImageViewerScreen
import com.example.nossasaudeapp.ui.settings.SettingsScreen
import com.example.nossasaudeapp.ui.search.SearchScreen
import com.example.nossasaudeapp.ui.consultation.ConsultationFormScreen
import com.example.nossasaudeapp.ui.member.MemberFormScreen
import com.example.nossasaudeapp.ui.member.MemberProfileScreen

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME
    ) {
        composable(NavRoutes.HOME) {
            HomeScreen(
                onMemberClick = { memberId ->
                    navController.navigate(NavRoutes.memberProfile(memberId))
                },
                onAddMember = { navController.navigate(NavRoutes.MEMBER_NEW) },
                onSearch = { navController.navigate(NavRoutes.SEARCH) },
                onSettings = { navController.navigate(NavRoutes.SETTINGS) },
            )
        }
        composable(NavRoutes.MEMBER_NEW) {
            MemberFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.MEMBER_EDIT) {
            MemberFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.MEMBER_PROFILE) {
            MemberProfileScreen(
                onBack = { navController.popBackStack() },
                onEdit = { memberId -> navController.navigate(NavRoutes.memberEdit(memberId)) },
                onAddConsultation = { memberId ->
                    navController.navigate(NavRoutes.consultationNew(memberId))
                },
                onConsultationClick = { consultationId ->
                    navController.navigate(NavRoutes.consultationDetail(consultationId))
                },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.CONSULTATION_NEW) {
            ConsultationFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id -> navController.navigate(NavRoutes.consultationDetail(id)) {
                    popUpTo(NavRoutes.CONSULTATION_NEW) { inclusive = true }
                }},
            )
        }
        composable(NavRoutes.CONSULTATION_EDIT) {
            ConsultationFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id -> navController.navigate(NavRoutes.consultationDetail(id)) {
                    popUpTo(NavRoutes.CONSULTATION_DETAIL) { inclusive = true }
                }},
            )
        }
        composable(NavRoutes.CONSULTATION_DETAIL) {
            ConsultationDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(NavRoutes.consultationEdit(id)) },
                onDeleted = { navController.popBackStack() },
                onViewImages = { urls, index ->
                    navController.navigate(NavRoutes.imageViewer(urls, index))
                },
            )
        }
        composable(NavRoutes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onConsultationClick = { id -> navController.navigate(NavRoutes.consultationDetail(id)) },
            )
        }
        composable(NavRoutes.IMAGE_VIEWER) { entry ->
            val raw = entry.arguments?.getString("keys").orEmpty()
            val index = entry.arguments?.getString("index")?.toIntOrNull() ?: 0
            val urls = if (raw.isBlank()) emptyList()
            else raw.split("|").map { android.net.Uri.decode(it) }
            ImageViewerScreen(
                urls = urls,
                initialIndex = index,
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = name)
    }
}
