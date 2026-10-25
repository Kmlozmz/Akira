package com.akira.app.feature.home

enum class HomeDestination { HOME, SEARCH, LIBRARY, SETTINGS }

internal fun settingsReturnDestination(current: HomeDestination, previous: HomeDestination): HomeDestination =
    if (current == HomeDestination.SETTINGS) previous.takeUnless { it == HomeDestination.SETTINGS } ?: HomeDestination.HOME
    else current
