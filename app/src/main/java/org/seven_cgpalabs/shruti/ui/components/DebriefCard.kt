package org.seven_cgpalabs.shruti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.seven_cgpalabs.shruti.core.CallSessionSummary

@Composable
fun DebriefCard(
    session: CallSessionSummary,
    isPlaying: Boolean,
    onPlayDebrief: () -> Unit,
    onSendGatePass: () -> Unit = {},
    onCallback: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161A22))
            .border(1.dp, Color(0xFF282E3A), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            // Header Row: Type & Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = if (session.callType == "delivery") {
                        Icons.Default.LocalShipping
                    } else {
                        Icons.Default.Security
                    }
                    val iconColor = if (session.callType == "delivery") Color(0xFF00E5FF) else Color(0xFFFF9100)

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (session.callType == "delivery") "DELIVERY CALL DEBRIEF" else "SPAM CLASSIFICATION",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${session.durationSeconds}s call",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle
            Text(
                text = "Caller: ${session.callerLabel}",
                color = Color(0xFFC9D1D9),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            // Delivery Logistics Stepper (if delivery call)
            if (session.callType == "delivery") {
                Spacer(modifier = Modifier.height(12.dp))
                LogisticsStepper()
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Zero-Persistence Privacy Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D1117))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${session.vectorCount} Encrypted Trajectory Vectors • Zero Text Stored",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayDebrief,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) Color(0xFFFF9100) else Color(0xFF00E5FF)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "Playing..." else "Play Voice Debrief",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (session.callType == "delivery") {
                    OutlinedButton(
                        onClick = onSendGatePass,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Gate Pass",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onCallback,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Callback",
                        color = Color(0xFF7C4DFF),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LogisticsStepper() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1117))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepNode(label = "Gate Entry", isDone = true)
        Text(text = "─►", color = Color(0xFF00E5FF), fontSize = 10.sp)
        StepNode(label = "Security Desk", isDone = true)
        Text(text = "─►", color = Color(0xFF00E5FF), fontSize = 10.sp)
        StepNode(label = "Flat 804 Drop", isDone = true, isCurrent = true)
    }
}

@Composable
fun StepNode(label: String, isDone: Boolean, isCurrent: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCurrent -> Color(0xFF00E5FF)
                        isDone -> Color(0xFF1DE9B6)
                        else -> Color(0xFF484F58)
                    }
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = if (isCurrent) Color(0xFF00E5FF) else Color(0xFF8B949E),
            fontSize = 10.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
        )
    }
}
