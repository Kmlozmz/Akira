package com.akira.app.core.widget

/** Dedicated opaque window lets compatible launchers expand/return to the widget (API 31+).
 * MainActivity selects the translucent Compose fallback on older Android versions. */
class WidgetLaunchActivity : com.akira.app.MainActivity()
