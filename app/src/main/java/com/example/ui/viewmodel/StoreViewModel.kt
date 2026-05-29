package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cache.RecentSearchEntity
import com.example.data.model.*
import com.example.data.repository.StoreRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(
        val banners: List<FeaturedBanner>,
        val categories: List<Category>,
        val featuredApps: List<AppInfo>,
        val topChartApps: List<AppInfo>,
        val trendingApps: List<AppInfo>,
        val premiumApps: List<AppInfo>,
        val recommendedApps: List<AppInfo>
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

sealed interface DetailUiState {
    object Loading : DetailUiState
    data class Success(
        val app: AppInfo,
        val isInstalled: Boolean,
        val isFavorite: Boolean,
        val relatedApps: List<AppInfo>
    ) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

sealed interface SearchUiState {
    data class Idle(val recentSearches: List<RecentSearchEntity>) : SearchUiState
    object Searching : SearchUiState
    data class Results(val apps: List<AppInfo>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

data class UserState(
    val isLoggedIn: Boolean = false,
    val email: String? = null,
    val displayName: String? = null,
    val isGuest: Boolean = false,
    val uid: String? = null
)

class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StoreRepository(application)
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e("StoreViewModel", "Failed to retrieve FirebaseAuth instance", e)
            null
        }
    }

    // Home UI Stream
    private val _homeUiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    // Detail UI Stream
    private val _detailUiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val detailUiState: StateFlow<DetailUiState> = _detailUiState.asStateFlow()

    // Search UI Stream
    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle(emptyList()))
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    // User/Auth State
    private val _userState = MutableStateFlow(UserState())
    val userState: StateFlow<UserState> = _userState.asStateFlow()

    // Local lists for calculations
    private var allApps: List<AppInfo> = emptyList()

    init {
        checkCurrentAuthStatus()
        observeRecentSearches()
        loadHomeData()
    }

    private fun checkCurrentAuthStatus() {
        val user = auth?.currentUser
        if (user != null) {
            _userState.value = UserState(
                isLoggedIn = true,
                email = user.email,
                displayName = user.displayName ?: (if (user.isAnonymous) "Guest Partner" else user.email?.substringBefore("@")),
                isGuest = user.isAnonymous,
                uid = user.uid
            )
        } else {
            // Safe fallback simulated state for offline mode
            _userState.value = UserState(
                isLoggedIn = true,
                email = "guest@example.com",
                displayName = "Offline Visitor",
                isGuest = true,
                uid = "offline_guest_uid"
            )
        }
    }

    fun observeRecentSearches() {
        viewModelScope.launch {
            repository.recentSearches.collect { searches ->
                if (_searchUiState.value is SearchUiState.Idle) {
                    _searchUiState.value = SearchUiState.Idle(searches)
                }
            }
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _homeUiState.value = HomeUiState.Loading
            try {
                // Fetch in parallel or sequence
                allApps = repository.fetchApps()
                val categories = repository.fetchCategories()
                val banners = repository.fetchBanners()

                val featured = allApps.filter { it.featured }
                val topCharts = allApps.filter { it.topChart }
                val trending = allApps.filter { it.trending }
                val premium = allApps.filter { it.premium }
                val recommended = allApps.shuffled().take(4)

                _homeUiState.value = HomeUiState.Success(
                    banners = banners,
                    categories = categories,
                    featuredApps = featured,
                    topChartApps = topCharts,
                    trendingApps = trending,
                    premiumApps = premium,
                    recommendedApps = recommended
                )
            } catch (e: Exception) {
                _homeUiState.value = HomeUiState.Error("Failed to sync app data: ${e.message}")
            }
        }
    }

    // Authentications - Guest, anonymous login
    fun signInGuest(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val fbAuth = auth
                if (fbAuth == null) {
                    _userState.value = UserState(
                        isLoggedIn = true,
                        email = "guest@example.com",
                        displayName = "Offline Visitor",
                        isGuest = true,
                        uid = "offline_guest_uid"
                    )
                    onSuccess()
                    return@launch
                }
                fbAuth.signInAnonymously().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        checkCurrentAuthStatus()
                        onSuccess()
                    } else {
                        onError(task.exception?.message ?: "Unknown guest login error.")
                    }
                }
            } catch (e: Exception) {
                onError(e.message ?: "Authentication exception")
            }
        }
    }

    // Email sign-up or log-in
    fun signInEmail(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val fbAuth = auth
                if (fbAuth == null) {
                    _userState.value = UserState(
                        isLoggedIn = true,
                        email = email,
                        displayName = email.substringBefore("@"),
                        isGuest = false,
                        uid = "offline_" + email.hashCode()
                    )
                    onSuccess()
                    return@launch
                }
                fbAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        checkCurrentAuthStatus()
                        onSuccess()
                    } else {
                        // Attempt automatically creating user if not exist (Convenience pattern)
                        signUpEmail(email, password, onSuccess, onError)
                    }
                }
            } catch (e: Exception) {
                onError(e.message ?: "Auth Exception")
            }
        }
    }

    private fun signUpEmail(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val fbAuth = auth
        if (fbAuth == null) {
            _userState.value = UserState(
                isLoggedIn = true,
                email = email,
                displayName = email.substringBefore("@"),
                isGuest = false,
                uid = "offline_" + email.hashCode()
            )
            onSuccess()
            return
        }
        fbAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                checkCurrentAuthStatus()
                onSuccess()
            } else {
                onError(task.exception?.message ?: "Email login failed and registration rejected.")
            }
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e("StoreViewModel", "auth?.signOut() raw exception", e)
        }
        _userState.value = UserState(isLoggedIn = false)
    }

    // Detail Screen controller
    private var detailObserverJob: kotlinx.coroutines.Job? = null
    fun selectAppDetail(appId: String) {
        detailObserverJob?.cancel()
        _detailUiState.value = DetailUiState.Loading
        detailObserverJob = viewModelScope.launch {
            try {
                val app = repository.fetchAppById(appId)
                if (app != null) {
                    // Combine status flow mapping
                    val isInstalledFlow = repository.isAppInstalledFlow(appId)
                    val isFavoriteFlow = repository.isAppFavoriteFlow(appId)

                    combine(isInstalledFlow, isFavoriteFlow) { installed, favorite ->
                        val cleanCategory = app.category.trim().lowercase()
                        val related = allApps.filter { candidate ->
                            candidate.appId != app.appId && (
                                candidate.category.trim().lowercase() == cleanCategory ||
                                candidate.tags.map { it.lowercase() }.contains(cleanCategory)
                            )
                        }.toMutableList()

                        // Padding to ensure we always show a detailed set of recommendations of 8-10 entries
                        if (related.size < 10) {
                            val extraApps = allApps.filter { candidate ->
                                candidate.appId != app.appId && !related.any { it.appId == candidate.appId }
                            }.sortedByDescending { it.rating }
                            related.addAll(extraApps.take(10 - related.size))
                        }

                        DetailUiState.Success(
                            app = app,
                            isInstalled = installed,
                            isFavorite = favorite,
                            relatedApps = related.distinctBy { it.appId }
                        )
                    }.collect { combinedState ->
                        _detailUiState.value = combinedState
                    }
                } else {
                    _detailUiState.value = DetailUiState.Error("Game profile not found.")
                }
            } catch (e: Exception) {
                _detailUiState.value = DetailUiState.Error(e.message ?: "An unexpected error occurred.")
            }
        }
    }

    // Search Operations
    fun performSearch(query: String) {
        if (query.trim().isEmpty()) {
            viewModelScope.launch {
                val searches = repository.recentSearches.first()
                _searchUiState.value = SearchUiState.Idle(searches)
            }
            return
        }

        _searchUiState.value = SearchUiState.Searching
        viewModelScope.launch {
            try {
                val results = repository.searchApps(query)
                _searchUiState.value = SearchUiState.Results(results)
            } catch (e: Exception) {
                _searchUiState.value = SearchUiState.Error(e.message ?: "Search operations failed.")
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }

    fun removeRecentSearchItem(query: String) {
        viewModelScope.launch {
            repository.removeRecentSearch(query)
        }
    }

    // App Operations (install, uninstall, wishlist)
    fun installSimulation(appId: String) {
        viewModelScope.launch {
            repository.setInstalledState(appId, true)
        }
    }

    fun uninstallSimulation(appId: String) {
        viewModelScope.launch {
            repository.setInstalledState(appId, false)
        }
    }

    fun toggleWishlist(appId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(appId)
        }
    }

    // Admin-Ready operation (Submit new application to Firebase Realtime Database)
    fun addNewAppToFirebase(app: AppInfo, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // Post to Firebase Realtime Database REST URL
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    
                    // Simple JSON payload escape mapping
                    val jsonBody = """
                        {
                            "category": "${app.category.replace("\"", "\\\"")}",
                            "desc": "${app.fullDescription.replace("\"", "\\\"").replace("\n", "\\n")}",
                            "dev": "${app.developerName.replace("\"", "\\\"")}",
                            "downloads": "${app.downloads.replace("\"", "\\\"")}",
                            "featured": ${app.featured},
                            "icon": "${app.appIcon.replace("\"", "\\\"")}",
                            "id": "${app.appId.replace("\"", "\\\"")}",
                            "keywords": "${app.appPackage.replace("\"", "\\\"")}",
                            "link": "${app.apkUrl.replace("\"", "\\\"")}",
                            "rate": "${app.rating}",
                            "screenshots": [],
                            "size": "${app.size.replace("\"", "\\\"")}",
                            "status": "approved",
                            "tag": "User Upload",
                            "timestamp": ${System.currentTimeMillis()},
                            "title": "${app.appName.replace("\"", "\\\"")}",
                            "ver": "${app.version.replace("\"", "\\\"")}"
                        }
                    """.trimIndent()
                    
                    val requestBody = jsonBody.toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url("https://wajidtechtube-default-rtdb.firebaseio.com/apps/${app.appId}.json")
                        .put(requestBody)
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("Failed upload with HTTP code: ${response.code}")
                        }
                    }
                }
                
                // Reload home list to fetch new items
                loadHomeData()
                onComplete(true)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Admin: Failed to insert new app to Firebase RTD", e)
                
                // Fallback attempt to legacy Firestore if configured
                try {
                    val firestore = FirebaseFirestore.getInstance()
                    firestore.collection("apps").document(app.appId).set(app).await()
                    loadHomeData()
                    onComplete(true)
                } catch (ex: Exception) {
                    Log.e("StoreViewModel", "Admin: Firestore write also failed", ex)
                    onComplete(false)
                }
            }
        }
    }
}
