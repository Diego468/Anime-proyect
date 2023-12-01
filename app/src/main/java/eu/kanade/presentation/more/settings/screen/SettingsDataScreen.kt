package eu.kanade.presentation.more.settings.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.hippo.unifile.UniFile
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.data.CreateBackupScreen
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.presentation.permissions.PermissionRequestHelper
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.backup.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.BackupRestoreJob
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.EpisodeCache
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.i18n.localize
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.backup.service.FLAG_CATEGORIES
import tachiyomi.domain.backup.service.FLAG_CHAPTERS
import tachiyomi.domain.backup.service.FLAG_EXTENSIONS
import tachiyomi.domain.backup.service.FLAG_EXT_SETTINGS
import tachiyomi.domain.backup.service.FLAG_HISTORY
import tachiyomi.domain.backup.service.FLAG_SETTINGS
import tachiyomi.domain.backup.service.FLAG_TRACK
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.localize
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsDataScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.label_data_storage

    @Composable
    override fun getPreferences(): List<Preference> {
        val backupPreferences = Injekt.get<BackupPreferences>()
        val storagePreferences = Injekt.get<StoragePreferences>()

        PermissionRequestHelper.requestStoragePermission()

        return listOf(
            getStorageLocationPref(storagePreferences = storagePreferences),
            Preference.PreferenceItem.InfoPreference(localize(MR.strings.pref_storage_location_info)),

            getBackupAndRestoreGroup(backupPreferences = backupPreferences),
            getDataGroup(backupPreferences = backupPreferences),
        )
    }

    @Composable
    private fun getStorageLocationPref(
        storagePreferences: StoragePreferences,
    ): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        val storageDirPref = storagePreferences.baseStorageDirectory()
        val storageDir by storageDirPref.collectAsState()
        val pickStorageLocation = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                context.contentResolver.takePersistableUriPermission(uri, flags)

                val file = UniFile.fromUri(context, uri)
                storageDirPref.set(file.uri.toString())
            }
        }

        return Preference.PreferenceItem.TextPreference(
            title = localize(MR.strings.pref_storage_location),
            subtitle = remember(storageDir) {
                (UniFile.fromUri(context, storageDir.toUri())?.filePath)
            } ?: localize(MR.strings.invalid_location, storageDir),
            onClick = {
                try {
                    pickStorageLocation.launch(null)
                } catch (e: ActivityNotFoundException) {
                    context.localize(MR.strings.file_picker_error)
                }
            },
        )
    }

    @Composable
    private fun getBackupAndRestoreGroup(backupPreferences: BackupPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val lastAutoBackup by backupPreferences.lastAutoBackupTimestamp().collectAsState()

        return Preference.PreferenceGroup(
            title = localize(MR.strings.label_backup),
            preferenceItems = listOf(
                // Manual actions
                getCreateBackupPref(),
                getRestoreBackupPref(),

                // Automatic backups
                Preference.PreferenceItem.ListPreference(
                    pref = backupPreferences.backupInterval(),
                    title = localize(MR.strings.pref_backup_interval),
                    entries = mapOf(
                        0 to localize(MR.strings.off),
                        6 to localize(MR.strings.update_6hour),
                        12 to localize(MR.strings.update_12hour),
                        24 to localize(MR.strings.update_24hour),
                        48 to localize(MR.strings.update_48hour),
                        168 to localize(MR.strings.update_weekly),
                    ),
                    onValueChanged = {
                        BackupCreateJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.InfoPreference(
                    localize(MR.strings.backup_info) + "\n\n" +
                        localize(MR.strings.last_auto_backup_info, relativeTimeSpanString(lastAutoBackup)),
                ),
            ),
        )
    }

    @Composable
    private fun getCreateBackupPref(): Preference.PreferenceItem.TextPreference {
        val navigator = LocalNavigator.currentOrThrow
        return Preference.PreferenceItem.TextPreference(
            title = localize(MR.strings.pref_create_backup),
            subtitle = localize(MR.strings.pref_create_backup_summ),
            onClick = { navigator.push(CreateBackupScreen()) },
        )
    }

    @Composable
    private fun getRestoreBackupPref(): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        var error by remember { mutableStateOf<Any?>(null) }
        if (error != null) {
            val onDismissRequest = { error = null }
            when (val err = error) {
                is InvalidRestore -> {
                    AlertDialog(
                        onDismissRequest = onDismissRequest,
                        title = { Text(text = localize(MR.strings.invalid_backup_file)) },
                        text = { Text(text = listOfNotNull(err.uri, err.message).joinToString("\n\n")) },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    context.copyToClipboard(err.message, err.message)
                                    onDismissRequest()
                                },
                            ) {
                                Text(text = localize(MR.strings.action_copy_to_clipboard))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = onDismissRequest) {
                                Text(text = localize(MR.strings.action_ok))
                            }
                        },
                    )
                }
                is MissingRestoreComponents -> {
                    AlertDialog(
                        onDismissRequest = onDismissRequest,
                        title = { Text(text = localize(MR.strings.pref_restore_backup)) },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            ) {
                                val msg = buildString {
                                    append(localize(MR.strings.backup_restore_content_full))
                                    if (err.sources.isNotEmpty()) {
                                        append("\n\n").append(
                                            localize(MR.strings.backup_restore_missing_sources),
                                        )
                                        err.sources.joinTo(
                                            this,
                                            separator = "\n- ",
                                            prefix = "\n- ",
                                        )
                                    }
                                    if (err.trackers.isNotEmpty()) {
                                        append("\n\n").append(
                                            localize(MR.strings.backup_restore_missing_trackers),
                                        )
                                        err.trackers.joinTo(
                                            this,
                                            separator = "\n- ",
                                            prefix = "\n- ",
                                        )
                                    }
                                }
                                Text(text = msg)
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    BackupRestoreJob.start(context, err.uri)
                                    onDismissRequest()
                                },
                            ) {
                                Text(text = localize(MR.strings.action_restore))
                            }
                        },
                    )
                }
                else -> error = null // Unknown
            }
        }

        val chooseBackup = rememberLauncherForActivityResult(
            object : ActivityResultContracts.GetContent() {
                override fun createIntent(context: Context, input: String): Intent {
                    val intent = super.createIntent(context, input)
                    return Intent.createChooser(
                        intent,
                        context.localize(MR.strings.file_select_backup),
                    )
                }
            },
        ) {
            if (it == null) {
                context.localize(MR.strings.file_null_uri_error)
                return@rememberLauncherForActivityResult
            }

            val results = try {
                BackupFileValidator().validate(context, it)
            } catch (e: Exception) {
                error = InvalidRestore(it, e.message.toString())
                return@rememberLauncherForActivityResult
            }

            if (results.missingSources.isEmpty() && results.missingTrackers.isEmpty()) {
                BackupRestoreJob.start(context, it)
                return@rememberLauncherForActivityResult
            }

            error = MissingRestoreComponents(it, results.missingSources, results.missingTrackers)
        }

        return Preference.PreferenceItem.TextPreference(
            title = localize(MR.strings.pref_restore_backup),
            subtitle = localize(MR.strings.pref_restore_backup_summ),
            onClick = {
                if (!BackupRestoreJob.isRunning(context)) {
                    if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                        context.localize(MR.strings.restore_miui_warning, Toast.LENGTH_LONG)
                    }
                    // no need to catch because it's wrapped with a chooser
                    chooseBackup.launch("*/*")
                } else {
                    context.localize(MR.strings.restore_in_progress)
                }
            },
        )
    }

    @Composable
    private fun getDataGroup(backupPreferences: BackupPreferences): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        val backupIntervalPref = backupPreferences.backupInterval()
        val backupInterval by backupIntervalPref.collectAsState()

        val chapterCache = remember { Injekt.get<ChapterCache>() }
        val episodeCache = remember { Injekt.get<EpisodeCache>() }
        var cacheReadableSizeSema by remember { mutableIntStateOf(0) }
        val cacheReadableMangaSize = remember(cacheReadableSizeSema) { chapterCache.readableSize }
        val cacheReadableAnimeSize = remember(cacheReadableSizeSema) { episodeCache.readableSize }

        return Preference.PreferenceGroup(
            title = localize(MR.strings.label_data),
            preferenceItems = listOf(
                getMangaStorageInfoPref(cacheReadableMangaSize),
                getAnimeStorageInfoPref(cacheReadableAnimeSize),

                Preference.PreferenceItem.TextPreference(
                    title = localize(MR.strings.pref_clear_chapter_cache),
                    subtitle = localize(
                        MR.strings.used_cache_both,
                        cacheReadableAnimeSize,
                        cacheReadableMangaSize,
                    ),
                    onClick = {
                        scope.launchNonCancellable {
                            try {
                                val deletedFiles = chapterCache.clear() + episodeCache.clear()
                                withUIContext {
                                    context.toast(context.localize(MR.strings.cache_deleted, deletedFiles))
                                    cacheReadableSizeSema++
                                }
                            } catch (e: Throwable) {
                                logcat(LogPriority.ERROR, e)
                                withUIContext { context.localize(MR.strings.cache_delete_error) }
                            }
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.autoClearItemCache(),
                    title = localize(MR.strings.pref_auto_clear_chapter_cache),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    pref = backupPreferences.backupFlags(),
                    enabled = backupInterval != 0,
                    title = localize(MR.strings.pref_backup_flags),
                    subtitle = localize(MR.strings.pref_backup_flags_summary),
                    entries = mapOf(
                        FLAG_CATEGORIES to localize(MR.strings.general_categories),
                        FLAG_CHAPTERS to localize(MR.strings.chapters_episodes),
                        FLAG_HISTORY to localize(MR.strings.history),
                        FLAG_TRACK to localize(MR.strings.track),
                        FLAG_SETTINGS to localize(MR.strings.settings),
                        FLAG_EXT_SETTINGS to localize(MR.strings.extension_settings),
                        FLAG_EXTENSIONS to localize(MR.strings.label_extensions),
                    ),
                    onValueChanged = {
                        if (FLAG_SETTINGS in it || FLAG_EXT_SETTINGS in it) {
                            context.localize(MR.strings.backup_settings_warning, Toast.LENGTH_LONG)
                        }
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    fun getMangaStorageInfoPref(
        chapterCacheReadableSize: String,
    ): Preference.PreferenceItem.CustomPreference {
        val context = LocalContext.current
        val available = remember {
            Formatter.formatFileSize(context, DiskUtil.getAvailableStorageSpace(Environment.getDataDirectory()))
        }
        val total = remember {
            Formatter.formatFileSize(context, DiskUtil.getTotalStorageSpace(Environment.getDataDirectory()))
        }

        return Preference.PreferenceItem.CustomPreference(
            title = localize(MR.strings.pref_manga_storage_usage),
        ) {
            BasePreferenceWidget(
                title = localize(MR.strings.pref_manga_storage_usage),
                subcomponent = {
                    // TODO: downloads, SD cards, bar representation?, i18n
                    Box(modifier = Modifier.padding(horizontal = PrefsHorizontalPadding)) {
                        Text(text = "Available: $available / $total (chapter cache: $chapterCacheReadableSize)")
                    }
                },
            )
        }
    }

    @Composable
    fun getAnimeStorageInfoPref(
        episodeCacheReadableSize: String,
    ): Preference.PreferenceItem.CustomPreference {
        val context = LocalContext.current
        val available = remember {
            Formatter.formatFileSize(context, DiskUtil.getAvailableStorageSpace(Environment.getDataDirectory()))
        }
        val total = remember {
            Formatter.formatFileSize(context, DiskUtil.getTotalStorageSpace(Environment.getDataDirectory()))
        }

        return Preference.PreferenceItem.CustomPreference(
            title = localize(MR.strings.pref_anime_storage_usage),
        ) {
            BasePreferenceWidget(
                title = localize(MR.strings.pref_anime_storage_usage),
                subcomponent = {
                    // TODO: downloads, SD cards, bar representation?, i18n
                    Box(modifier = Modifier.padding(horizontal = PrefsHorizontalPadding)) {
                        Text(text = "Available: $available / $total (Episode cache: $episodeCacheReadableSize)")
                    }
                },
            )
        }
    }
}

private data class MissingRestoreComponents(
    val uri: Uri,
    val sources: List<String>,
    val trackers: List<String>,
)

private data class InvalidRestore(
    val uri: Uri? = null,
    val message: String,
)
