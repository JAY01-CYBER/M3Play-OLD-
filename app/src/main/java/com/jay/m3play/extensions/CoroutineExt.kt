/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.extensions

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

fun <T> Flow<T>.collect(
    scope: CoroutineScope,
    action: suspend (value: T) -> Unit,
) {
    scope.launch {
        collect(action)
    }
}

fun <T> Flow<T>.collectLatest(
    scope: CoroutineScope,
    action: suspend (value: T) -> Unit,
) {
    scope.launch {
        collectLatest(action)
    }
}

val SilentHandler = CoroutineExceptionHandler { _, _ -> }
