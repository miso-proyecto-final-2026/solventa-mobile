package com.solventa.mobile.verificacion

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText

/** `true` cuando existe al menos un nodo con ese texto (para `waitUntil`). */
fun ComposeContentTestRule.onAllNodesWithTextCompat(texto: String): Boolean =
    onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()
