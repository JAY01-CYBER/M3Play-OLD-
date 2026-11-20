/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.utils

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.jay.m3play.extensions.toEnum
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.properties.ReadOnlyProperty

// YAHAN MASTER FIX LAGAYA HAI (ReplaceFileCorruptionHandler)
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler(
        produceNewData = { emptyPreferences() }
    )
)

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    runBlocking {
        try {
            data.first()[key]
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T =
    runBlocking {
        try {
            data.first()[key] ?: defaultValue
        } catch (e: Exception) {
            e.printStackTrace()
            defaultValue
        }
    }

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> 
    try {
        context.dataStore[key] ?: defaultValue
    } catch (e: Exception) {
        defaultValue
    }
}

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> 
    try {
        context.dataStore[key].toEnum(defaultValue)
    } catch (e: Exception) {
        defaultValue
    }
}

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state =
        remember(context, key, defaultValue) {
            context.dataStore.data
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(
            initialValue = defaultValue
        )

    return remember(state, coroutineScope, context, key) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        try {
                            context.dataStore.edit { it[key] = value }
                        } catch (e: Exception) {}
                    }
                }
            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val initialValue = defaultValue
    val state =
        remember(context, key, defaultValue) {
            context.dataStore.data
                .map { it[key].toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue)

    return remember(state, coroutineScope, context, key) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        try {
                            context.dataStore.edit { it[key] = value.name }
                        } catch (e: Exception) {}
                    }
                }
            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
