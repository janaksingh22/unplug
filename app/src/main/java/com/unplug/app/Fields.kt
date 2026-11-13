package com.unplug.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Hairline-boxed dialog used for every small form in the app. */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Paper)
                .border(1.dp, Ink)
                .padding(24.dp)
        ) {
            Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** Single-line underlined input. */
@Composable
fun MonoField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    keyboardType: KeyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Ink, fontSize = 17.sp, fontFamily = Mono),
        cursorBrush = SolidColor(Ink),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 10.dp)) {
                    if (value.isEmpty()) Label(placeholder)
                    inner()
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Faint)
                )
            }
        },
    )
}

/** "−  value  +" control. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        RoundIconButton("−", onMinus)
        Text(
            value,
            color = Ink,
            fontSize = 28.sp,
            style = digitsStyle(),
            modifier = Modifier.width(120.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
        RoundIconButton("+", onPlus)
    }
}

/** Two buttons side by side at the bottom of a dialog. */
@Composable
fun DialogButtons(
    secondary: String,
    onSecondary: () -> Unit,
    primary: String,
    onPrimary: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlineButton(secondary, Modifier.weight(1f), onSecondary)
        PrimaryButton(primary, Modifier.weight(1f), onPrimary)
    }
}

fun hhmm(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

fun daysLabel(mask: Int): String = when (mask) {
    Schedule.EVERY_DAY -> "Every day"
    Schedule.WEEKDAYS -> "Weekdays"
    0b1100000 -> "Weekends"
    else -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        .filterIndexed { i, _ -> mask and (1 shl i) != 0 }
        .joinToString(" ")
}
