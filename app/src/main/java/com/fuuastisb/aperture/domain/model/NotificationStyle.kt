package com.fuuastisb.aperture.domain.model

/**
 * How the mandatory recording notification presents itself. Android forces *a* notification for a
 * camera foreground service; this only controls the wording, since in an emergency an obvious
 * "recording" notice could endanger the user.
 */
enum class NotificationStyle { DISCREET, CLEAR }
