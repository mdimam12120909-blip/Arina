package com.arina.assistant.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arina.assistant.Brand
import com.arina.assistant.cloud.GoogleSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Opens Google's account chooser and signs the person in to Entrion. */
fun startGoogleSignIn(activity: Activity, vm: ArinaEngine, scope: CoroutineScope) {
    scope.launch {
        try {
            val token = GoogleSignIn.idToken(activity) ?: return@launch
            vm.signInWithGoogle(token)
        } catch (e: Exception) {
            vm.signInProblem((e.message ?: "Sign-in failed.") + if (com.arina.assistant.BuildConfig.ADMIN_APP) " (Admin app: Google sign-in must be enabled for package ${activity.packageName}.)" else "")
        }
    }
}

/** The first screen when nobody is signed in: the brand, what the app does, and one clear button. */
@Composable
fun SignInScreen(vm: ArinaEngine) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ArinaColors.Background, ArinaColors.Background, ArinaColors.Surface)))
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))
            LogoTile(Modifier.size(96.dp))
            Spacer(Modifier.height(22.dp))
            Text("Welcome to " + Brand.APP, color = ArinaColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("A voice agent that lives on your phone, speaks your language and gets things done.", color = ArinaColors.Muted, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(26.dp))
            Feature("Remembers you", "Your memory and chats follow your account.")
            Feature("Works your phone", "Opens apps, calls, searches and more when you ask.")
            Feature("Yours alone", "Your data is never shared with other accounts.")
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
                    .background(ArinaColors.Glow)
                    .clickable(enabled = !vm.signInBusy.value) { startGoogleSignIn(activity, vm, scope) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Text("G", color = Color(0xFF4285F4), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (vm.signInBusy.value) "Signing in…" else "Continue with Google",
                    color = if (ArinaColors.dark) ArinaColors.Background else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }
            vm.notice.value?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFD93A3A), fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Skip for now",
                color = ArinaColors.Muted, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.skipSignIn() }.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(Brand.APP + " by " + Brand.COMPANY, color = ArinaColors.Muted.copy(alpha = 0.8f), fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Feature(title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(ArinaColors.Surface).border(1.dp, ArinaColors.Deep, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Brush.linearGradient(listOf(ArinaColors.Glow, ArinaColors.Pink))))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = ArinaColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
