package com.fuuastisb.aperture.core.di

import javax.inject.Qualifier

/**
 * Marks the process-lifetime [kotlinx.coroutines.CoroutineScope] — for best-effort work that must
 * outlive a component's lifecycle (e.g. the recording service's backend "end"/metadata calls and the
 * MediaStore publish, which would otherwise be cancelled when the service stops).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
