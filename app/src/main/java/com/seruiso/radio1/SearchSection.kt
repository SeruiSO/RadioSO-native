package com.seruiso.radio1

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Форма пошуку станцій (вкладка search): name/country/genre + hints + Find.
 */
@Composable
fun SearchSection(
    searchOpen: Boolean,
    onSearchOpen: () -> Unit,
    qName: String,
    onName: (String) -> Unit,
    qCountry: String,
    onCountry: (String) -> Unit,
    qGenre: String,
    onGenre: (String) -> Unit,
    suggestFor: String,
    onSuggestFor: (String) -> Unit,
    nameHints: List<String>,
    countryHints: List<String>,
    genreHints: List<String>,
    onSearch: () -> Unit,
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
) {
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .background(card, RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSearchOpen() }
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = LocalContext.current.getString(R.string.nav_search),
                tint = text,
                modifier = Modifier.size(18.dp)
            )
            Text(
                LocalContext.current.getString(R.string.search_ellipsis2),
                color = text,
                modifier = Modifier.weight(1f)
            )
            Text(if (searchOpen) "▴" else "▾", color = muted)
        }
        if (searchOpen) {
            OutlinedTextField(
                value = qName,
                onValueChange = onName,
                singleLine = true,
                label = { Text(LocalContext.current.getString(R.string.search_one)) },
                modifier = Modifier.fillMaxWidth(),
            )
            val past = nameHints.distinct().take(8)
            if (past.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    past.forEach { h ->
                        Text(
                            h,
                            color = muted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onName(h) }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
            Button(
                onClick = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = acc,
                    contentColor = Color(0xFF0A0A0C)
                )
            ) {
                Text(LocalContext.current.getString(R.string.find))
            }
        }
    }
}
