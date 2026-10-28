package com.custom.tvlauncher.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.custom.tvlauncher.BuildConfig
import com.custom.tvlauncher.data.AppEntry
import com.custom.tvlauncher.data.AppRepository
import com.custom.tvlauncher.data.DayForecast
import com.custom.tvlauncher.data.FavoritesStore
import com.custom.tvlauncher.data.Movie
import com.custom.tvlauncher.data.MovieRepository
import com.custom.tvlauncher.data.Service
import com.custom.tvlauncher.data.Suggestion
import com.custom.tvlauncher.data.SuggestionRepository
import com.custom.tvlauncher.data.SystemInfo
import com.custom.tvlauncher.data.SystemRepository
import com.custom.tvlauncher.data.WallpaperRepository
import com.custom.tvlauncher.data.Weather
import com.custom.tvlauncher.data.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val wallpaperUrl: String? = null,
    val weather: Weather? = null,
    val movies: List<Movie> = emptyList(),
    val allApps: List<AppEntry> = emptyList(),
    val favorites: List<AppEntry> = emptyList(),
    val suggestions: Map<Service, List<Suggestion>> = emptyMap(),
    /** Paquet de l'app survolée dans les favoris ("" = tuile "Plus d'applications", null = rien survolé). */
    val focusedPackage: String? = null,
    val drawerOpen: Boolean = false,
    val weatherOpen: Boolean = false,
    val week: List<DayForecast> = emptyList(),
    val system: SystemInfo = SystemInfo(),
    val wallpaperBusy: Boolean = false,
) {
    /** Service de l'application actuellement survolée dans les favoris (null si aucun). */
    val focusedService: Service?
        get() {
            val pkg = focusedPackage?.takeIf { it.isNotEmpty() } ?: return null
            val app = allApps.firstOrNull { it.packageName == pkg } ?: return null
            return Service.of(app)
        }

    /** Suggestions du service survolé. */
    val visibleSuggestions: List<Suggestion>
        get() = focusedService?.let { suggestions[it] }.orEmpty()
}

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val appRepo = AppRepository(ctx)
    private val favStore = FavoritesStore(ctx)
    private val weatherRepo = WeatherRepository()
    private val wallpaperRepo = WallpaperRepository()
    private val movieRepo = MovieRepository(ctx)
    private val suggestionRepo = SuggestionRepository(ctx)
    private val systemRepo = SystemRepository(ctx)
    private var wallpaperJob: Job? = null

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var favPackages: List<String> = favStore.load() ?: emptyList()

    init {
        reloadApps()

        // Fond d'écran : nouvelle photo toutes les 30 minutes
        startWallpaperLoop()

        // Météo : toutes les 15 minutes
        viewModelScope.launch {
            while (isActive) {
                weatherRepo.fetch()?.let { w -> _ui.update { it.copy(weather = w) } }
                val week = weatherRepo.fetchWeek()
                if (week.isNotEmpty()) _ui.update { it.copy(week = week) }
                delay(15 * 60_000L)
            }
        }

        // Mémoire et stockage : toutes les 10 secondes (si l'option est activée)
        if (BuildConfig.SHOW_SYSTEM_STATS) viewModelScope.launch {
            while (isActive) {
                refreshSystem()
                delay(10_000L)
            }
        }

        // Dernières sorties Canal+ : toutes les 6 heures (1 minute après un échec)
        viewModelScope.launch {
            while (isActive) {
                val list = movieRepo.fetchCanalNew()
                if (list.isNotEmpty()) _ui.update { it.copy(movies = list) }
                delay(if (list.isEmpty()) 60_000L else 6 * 60 * 60_000L)
            }
        }

        // Suggestions (YouTube, Prime Video, Apple TV, Canal+, Disney+) : toutes les 3 heures
        if (BuildConfig.SHOW_SUGGESTIONS) viewModelScope.launch {
            while (isActive) {
                val fresh = suggestionRepo.fetchAll().filterValues { it.isNotEmpty() }
                if (fresh.isNotEmpty()) _ui.update { it.copy(suggestions = it.suggestions + fresh) }
                delay(if (fresh.size < Service.values().size) 10 * 60_000L else 3 * 60 * 60_000L)
            }
        }
    }

    // ---------- Applications & favoris ----------

    fun reloadApps() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.Default) { appRepo.loadAll() }
            if (favPackages.isEmpty()) {
                val installed = list.map { it.packageName }.toSet()
                favPackages = FavoritesStore.DEFAULTS.filter { it in installed }
                    .ifEmpty { list.take(6).map { it.packageName } }
                favStore.save(favPackages)
            }
            _ui.update { it.copy(allApps = list, favorites = resolve(favPackages, list)) }
        }
    }

    private fun resolve(pkgs: List<String>, apps: List<AppEntry>): List<AppEntry> {
        val byPkg = apps.associateBy { it.packageName }
        return pkgs.mapNotNull { byPkg[it] }
    }

    private fun commitFavorites(pkgs: List<String>) {
        favPackages = pkgs
        favStore.save(pkgs)
        _ui.update { it.copy(favorites = resolve(pkgs, it.allApps)) }
    }

    fun moveFavorite(pkg: String, delta: Int) {
        val list = _ui.value.favorites.map { it.packageName }.toMutableList()
        val i = list.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        commitFavorites(list)
    }

    fun removeFavorite(pkg: String) =
        commitFavorites(_ui.value.favorites.map { it.packageName }.filter { it != pkg })

    fun addFavorite(pkg: String) {
        val current = _ui.value.favorites.map { it.packageName }
        if (pkg !in current) commitFavorites(current + pkg)
    }

    fun setDrawer(open: Boolean) = _ui.update { it.copy(drawerOpen = open) }

    // ---------- Fond d'écran ----------

    private fun startWallpaperLoop() {
        wallpaperJob?.cancel()
        wallpaperJob = viewModelScope.launch {
            while (isActive) {
                _ui.update { it.copy(wallpaperBusy = true) }
                val url = wallpaperRepo.nextUrl()
                if (url != null) {
                    // on pré-charge dans le cache Coil pour un fondu enchaîné sans flash noir
                    runCatching {
                        ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).size(1920, 1080).build())
                    }
                    _ui.update { it.copy(wallpaperUrl = url) }
                }
                _ui.update { it.copy(wallpaperBusy = false) }
                delay(if (url == null) 60_000L else WALLPAPER_PERIOD_MS)
            }
        }
    }

    /** Change l'image tout de suite et repart pour 30 minutes. */
    fun changeWallpaper() = startWallpaperLoop()

    // ---------- Météo de la semaine ----------

    fun setWeatherOpen(open: Boolean) = _ui.update { it.copy(weatherOpen = open, drawerOpen = false) }

    fun refreshSystem() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.Default) { systemRepo.read() }
            _ui.update { it.copy(system = info) }
        }
    }

    // ---------- Focus (suggestions du service survolé) ----------

    fun onAppFocused(app: AppEntry) = _ui.update { it.copy(focusedPackage = app.packageName) }

    fun onMoreTileFocused() = _ui.update { it.copy(focusedPackage = "") }

    private companion object {
        const val WALLPAPER_PERIOD_MS = 30 * 60_000L
    }
}
