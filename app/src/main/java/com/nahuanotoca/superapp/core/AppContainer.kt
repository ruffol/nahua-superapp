package com.nahuanotoca.superapp.core

import android.content.Context
import com.nahuanotoca.superapp.NahuaApplication
import com.nahuanotoca.superapp.core.data.InboxRepository
import com.nahuanotoca.superapp.core.data.PreferencesRepository
import com.nahuanotoca.superapp.core.data.SessionRepository
import com.nahuanotoca.superapp.core.data.WalletRepository
import com.nahuanotoca.superapp.core.data.catalog.ServiceCatalog
import com.nahuanotoca.superapp.core.location.LocationProvider
import com.nahuanotoca.superapp.core.miniapp.MiniAppRegistry
import com.nahuanotoca.superapp.core.rag.ChatClient
import com.nahuanotoca.superapp.core.rag.PdfExtractor
import com.nahuanotoca.superapp.core.rag.RagRepository
import com.nahuanotoca.superapp.core.rag.RagSettingsRepository
import com.nahuanotoca.superapp.core.rag.ReportWriter
import com.nahuanotoca.superapp.core.rag.StudioRepository
import com.nahuanotoca.superapp.core.update.ApkInstaller
import com.nahuanotoca.superapp.core.update.UpdateRepository

/**
 * Manual dependency container. Repositories are process-singletons; feature ViewModels take the
 * container as a constructor argument and never reach for Android singletons themselves.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val wallet = WalletRepository(appContext)
    val preferences = PreferencesRepository(appContext)
    val session = SessionRepository()
    val inbox = InboxRepository()
    val miniApps = MiniAppRegistry(appContext)
    val locationProvider = LocationProvider(appContext)
    val ragSettings = RagSettingsRepository(appContext)
    val rag = RagRepository(appContext)
    val pdfExtractor = PdfExtractor(appContext)
    val chatClient = ChatClient()
    val studio = StudioRepository(appContext)
    val reportWriter = ReportWriter(appContext)
    val updates = UpdateRepository(appContext)
    val apkInstaller = ApkInstaller(appContext)
    val services = ServiceCatalog
}

/** Resolves the container from any [Context] used inside Compose. */
val Context.appContainer: AppContainer
    get() = (applicationContext as NahuaApplication).container