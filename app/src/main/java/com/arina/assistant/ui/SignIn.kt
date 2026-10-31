package com.arina.assistant.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
            vm.signInProblem(e.message ?: "Sign-in failed.")
        }
    }
}

/** The first screen when nobody is signed in: the brand and one button. */
@Composable
fun SignInScreen(vm: ArinaEngine) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020817), Color(0xFF031428), Color(0xFF020817)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(Brand.APP.uppercase(), color = ArinaColors.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
            Text("by " + Brand.COMPANY, color = ArinaColors.Muted, fontSize = 13.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(30.dp))
            Text(
                "Sign in so your agent remembers you on every phone. Your data belongs to you alone.",
                color = ArinaColors.Muted, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (vm.signInBusy.value) "Signing in…" else "Continue with Google",
                color = ArinaColors.Background,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(ArinaColors.Glow)
                    .clickable(enabled = !vm.signInBusy.value) { startGoogleSignIn(activity, vm, scope) }
                    .padding(horizontal = 30.dp, vertical = 13.dp)
            )
            vm.notice.value?.let {
                Text(it, color = Color(0xFFFFB86B), fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Skip for now",
                color = ArinaColors.Muted,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.skipSignIn() }.padding(12.dp)
            )
        }
    }
}
