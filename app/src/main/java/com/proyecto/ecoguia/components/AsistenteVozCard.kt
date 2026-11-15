
package com.proyecto.ecoguia.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proyecto.ecoguia.R

@Composable
fun AsistenteVozCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val morado = Color(0xFF421D70)
    val amarillo = Color(0xFFFFC21C)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Activar asistente de voz" }
            .background(morado)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = (-10).dp),
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "ASISTENTE DE VOZ",
                modifier = Modifier.offset(y = 5.dp),
                fontSize = 10.sp,
                color = Color.White
            )

            Spacer(Modifier.height(7.dp))

            Text(
                text = "Dime qué\nnecesitas",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 25.sp,
                color = Color.White
            )
        }

        Box(
            modifier = Modifier
                .size(69.dp)
                .background(
                    Color(0xFF77529A),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(55.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(47.dp)
                        .background(amarillo, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(
                            id = R.drawable.ic_microfono
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(27.dp),
                        colorFilter = ColorFilter.tint(Color.Black)
                    )
                }
            }
        }
    }
}
