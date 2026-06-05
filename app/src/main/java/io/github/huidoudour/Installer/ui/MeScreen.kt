package io.github.huidoudour.Installer.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.huidoudour.Installer.R

/**
 * 关于我页面 - Compose 实现
 * 布局严格还原 activity_me.xml：
 * - 3个TextView packed chain 垂直居中
 * - Button 底部居中，marginBottom=80dp，width=135dp
 * - 按钮 textColor=black, backgroundTint=light_pink
 */
@Composable
fun MeScreen() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 文本部分 - 3个TextView packed chain 垂直居中
        // 对应 XML 中 textView1, textView3, textView2 的 packed vertical chain
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // textView1: Hello World!
            Text(
                text = stringResource(R.string.hello_world),
                style = MaterialTheme.typography.bodyLarge
            )

            // textView3: 你保护世界，我保护你 (marginTop=16dp)
            Text(
                text = stringResource(R.string.text1),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )

            // textView2: 亻尔女子，我是 huidoudour（慧兜兜）(marginTop=16dp)
            Text(
                text = stringResource(R.string.about_me),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        // Button 底部居中
        // 对应 XML: layout_constraintBottom_toBottomOf="parent"
        //          layout_constraintLeft_toLeftOf="parent"
        //          layout_constraintRight_toRightOf="parent"
        //          android:layout_marginBottom="80dp"
        Button(
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huidoudour"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("MeActivity", "Failed to open URL: ${e.message}", e)
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.link_open_failed, e.message),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(135.dp)
                .padding(bottom = 80.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFB6C1),  // light_pink
                contentColor = Color(0xFF000000)       // black
            )
        ) {
            Text(
                text = stringResource(R.string.find_me),
                fontSize = 14.sp
            )
        }
    }
}
