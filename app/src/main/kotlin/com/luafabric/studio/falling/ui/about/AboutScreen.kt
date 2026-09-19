package com.luafabric.studio.falling.ui.about

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.luafabric.studio.falling.BuildConfig
import com.luafabric.studio.falling.R
import muling.views.tool.utils.AppInfoUtil
import muling.views.tool.utils.LogCatcher

data class Developer(
    val nameResId: Int,
    val roleResId: Int,
    val description: String,
    val color: Color,
    val iconResId: Int,
    val url: String = ""
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val packageInfo = AppInfoUtil.getPackageInfo()
    val appVersionName = packageInfo?.versionName ?: "1.0.0"
    val copyrightYear = BuildConfig.COPYRIGHT_YEAR

    val teamMembers = remember {
        listOf(
            Developer(
                nameResId = R.string.dev_muling_name,
                roleResId = R.string.dev_muling_role,
                description = "",
                color = Color(0xFF8D4A5A),
                iconResId = R.drawable.ic_muling,
                url = "https://github.com/TheMuLing"
            ),
            Developer(
                nameResId = R.string.dev_w_name,
                roleResId = R.string.dev_w_role,
                description = "",
                color = Color(0xFF8D4A5A),
                iconResId = R.drawable.ic_w,
                url = "https://github.com/wisyh"
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            AppHeaderSection(
                appVersionName = appVersionName
            )
        }

        item {
            SectionTitle(stringResource(R.string.tech_stack_title))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                teamMembers.forEach { dev ->
                    DeveloperChip(dev, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.community_title))

            Surface(
                onClick = {
                    try {
                        LogCatcher.i("AboutScreen", "打开QQ群")
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=I96XEdCObX_xJ6sdtqIbL4iMyOL4Sx51&authKey=vB%2FWu7tABJtyweVPuTUtiwChmFPg3IyO6lZkAsb49r5puqX202vw%2FozUarawnEaz&noverify=0&group_code=1106643491".toUri()
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        LogCatcher.e("AboutScreen", "打开QQ群失败", e)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF12B7F5),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_qq_group),
                                contentDescription = "QQ Group",
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.qq_group_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.qq_group_number),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.copyright, copyrightYear),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

}

@Composable
fun AppHeaderSection(
    appVersionName: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(160.dp)
                .clip(MaterialTheme.shapes.extraLarge)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_studio),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = appVersionName,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

@Composable
fun DeveloperChip(
    dev: Developer,
    iconResId: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val actualIconResId = iconResId ?: dev.iconResId

    Card(
        onClick = {
            if (dev.url.isNotEmpty()) {
                try {
                    LogCatcher.i("AboutScreen", "打开开发者链接: ${context.getString(dev.nameResId)} - ${dev.url}")
                    val intent = Intent(Intent.ACTION_VIEW, dev.url.toUri())
                    context.startActivity(intent)
                } catch (e: Exception) {
                    LogCatcher.e("AboutScreen", "打开链接失败", e)
                }
            }
        },
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (actualIconResId != 0) {
                    Icon(
                        painter = painterResource(id = actualIconResId),
                        contentDescription = "${context.getString(dev.nameResId)}",
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(dev.color)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(dev.nameResId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(dev.roleResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                painter = painterResource(id = R.drawable.ic_export_variant),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}