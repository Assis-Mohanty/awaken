package com.assismohanty.awaken.reelcounter.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** A single-threaded dispatcher: everything that touches the counter state runs on it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CounterDispatcher

/** The DataStore that holds Reel Counter's own settings (enabled, limits, bubble position). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReelPrefs
