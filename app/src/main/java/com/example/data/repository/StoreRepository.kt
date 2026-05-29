package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.cache.*
import com.example.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.OkHttpClient
import okhttp3.Request

class StoreRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val appDao = db.cachedAppDao()
    private val categoryDao = db.cachedCategoryDao()
    private val searchDao = db.recentSearchDao()
    private val _firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("StoreRepository", "Failed to retrieve FirebaseFirestore instance", e)
            null
        }
    }
    private val firestore: FirebaseFirestore
        get() = _firestore ?: throw Exception("Firestore not initialized")

    // Preferences-based simulated Installed Apps & Wishlist
    private val sharedPrefs = context.getSharedPreferences("aria_store_prefs", Context.MODE_PRIVATE)
    private val installedChanges = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 64)
    private val favoriteChanges = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 64)

    // Local Search History Streams
    val recentSearches: Flow<List<RecentSearchEntity>> = searchDao.getRecentSearches()

    suspend fun addRecentSearch(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            searchDao.insertRecentSearch(RecentSearchEntity(query.trim()))
        }
    }

    suspend fun removeRecentSearch(query: String) = withContext(Dispatchers.IO) {
        searchDao.deleteSearch(query)
    }

    suspend fun clearRecentSearches() = withContext(Dispatchers.IO) {
        searchDao.clearAllRecentSearches()
    }

    // Room flow for local cache
    fun getCachedAppsFlow(): Flow<List<AppInfo>> {
        return appDao.getAllCachedApps().map { list ->
            list.map { it.toModel() }
        }.flowOn(Dispatchers.IO)
    }

    fun getCachedCategoriesFlow(): Flow<List<Category>> {
        return categoryDao.getAllCachedCategories().map { list ->
            list.map { it.toModel() }
        }.flowOn(Dispatchers.IO)
    }

    // HELPER PARSER AND REST FETCH ENGINE
    private fun parseAppsJson(jsonString: String, isDirectAppsObject: Boolean = false): List<AppInfo> {
        val list = mutableListOf<AppInfo>()
        try {
            val root = JSONObject(jsonString)
            val appsJsonObject = if (isDirectAppsObject) {
                root
            } else {
                if (root.has("apps")) root.getJSONObject("apps") else root
            }

            val keys = appsJsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val appObj = appsJsonObject.getJSONObject(key)

                val category = appObj.optString("category", "").trim()
                val desc = appObj.optString("desc", "").trim()
                val dev = appObj.optString("dev", "").trim()
                val downloads = appObj.optString("downloads", "").trim()
                val featured = appObj.optBoolean("featured", false)
                val icon = appObj.optString("icon", "").trim()
                val id = appObj.optString("id", key).trim()
                val link = appObj.optString("link", "").trim()
                val keywords = appObj.optString("keywords", "").trim()
                
                var rating = 4.0
                if (appObj.has("rate")) {
                    val rateVal = appObj.opt("rate")
                    if (rateVal is Number) {
                        rating = rateVal.toDouble()
                    } else if (rateVal is String && rateVal.isNotEmpty()) {
                        rating = rateVal.toDoubleOrNull() ?: 4.0
                    }
                }

                val size = appObj.optString("size", "").trim()
                val status = appObj.optString("status", "").trim()
                val tag = appObj.optString("tag", "").trim()
                val title = appObj.optString("title", "").trim()
                val ver = appObj.optString("ver", "").trim()

                val cleanName = title.filter { it.isLetterOrDigit() }.lowercase().ifEmpty { id }
                val finalIcon = if (icon.isNotEmpty()) {
                    icon
                } else {
                    "https://picsum.photos/seed/${cleanName}_icon/200/200"
                }

                val trimmedCategory = category.trim()

                val screenshotsList = mutableListOf<String>()
                if (appObj.has("screenshots")) {
                    val array = appObj.optJSONArray("screenshots")
                    if (array != null && array.length() > 0) {
                        for (i in 0 until array.length()) {
                            val url = array.optString(i, "")
                            if (url.isNotEmpty()) {
                                screenshotsList.add(url)
                            }
                        }
                    }
                }
                if (screenshotsList.isEmpty()) {
                    for (i in 1..4) {
                        screenshotsList.add("https://picsum.photos/seed/${cleanName}_sc_${i}/500/800")
                    }
                }

                val hash = kotlin.math.abs(id.hashCode())
                val defaultDownloads = "${(hash % 900) + 10}K+"
                val defaultSize = "${(hash % 150) + 5} MB"
                val defaultVersion = "${(hash % 5) + 1}.${(hash % 9)}.${(hash % 20)}"
                val defaultDev = if (hash % 2 == 0) "Wajid Tech" else "Wajid Studio"

                val appInfo = AppInfo(
                    appId = id,
                    appName = title,
                    appPackage = keywords.ifEmpty { "com.developer.aria." + id.replace("-", "").lowercase() },
                    appIcon = finalIcon,
                    appBanner = if (screenshotsList.isNotEmpty()) screenshotsList[0] else "https://picsum.photos/seed/${cleanName}_banner/600/300",
                    screenshots = screenshotsList,
                    shortDescription = if (desc.length > 80) desc.substring(0, 80) + "..." else desc,
                    fullDescription = desc,
                    category = trimmedCategory,
                    rating = rating,
                    downloads = downloads.ifEmpty { defaultDownloads },
                    version = ver.ifEmpty { defaultVersion },
                    size = size.ifEmpty { defaultSize },
                    developerName = dev.ifEmpty { defaultDev },
                    updatedDate = "May 2026",
                    featured = featured,
                    topChart = downloads.filter { it.isDigit() }.toIntOrNull()?.let { it > 500 } ?: false,
                    trending = tag.lowercase().contains("pro") || tag.lowercase().contains("mod") || tag.lowercase().contains("money") || tag.lowercase().contains("official"),
                    premium = tag.lowercase().contains("pro") || tag.lowercase().contains("mod"),
                    apkUrl = link,
                    playStoreUrl = "https://play.google.com/store/apps/details?id=com.developer.ariastore",
                    tags = listOfNotNull(trimmedCategory.ifEmpty { null }, tag.ifEmpty { null }),
                    minimumAndroid = "8.0+",
                    permissions = listOf("Internet", "Write External Storage"),
                    videoTrailer = "",
                    releaseNotes = "Regular improvements and bug fixes."
                )
                list.add(appInfo)
            }
        } catch (e: Exception) {
            Log.e("StoreRepository", "Error parsing Apps JSON", e)
        }
        return list
    }

    private fun parseBannersJson(jsonString: String, isDirectSlider: Boolean = false): List<FeaturedBanner> {
        val list = mutableListOf<FeaturedBanner>()
        try {
            val array = if (isDirectSlider) {
                if (jsonString.trim().startsWith("[")) {
                    JSONArray(jsonString)
                } else {
                    val root = JSONObject(jsonString)
                    root.optJSONArray("slider")
                }
            } else {
                val root = JSONObject(jsonString)
                root.optJSONArray("slider")
            }

            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val idVal = obj.optString("id", "")
                    val image = obj.optString("image", "")
                    val type = obj.optString("type", "")
                    
                    list.add(
                        FeaturedBanner(
                            bannerId = "banner_$idVal",
                            imageUrl = image,
                            targetAppId = "0",
                            title = "Featured App",
                            subtitle = "Check out our latest releases!"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("StoreRepository", "Error parsing Banners JSON", e)
        }
        return list.ifEmpty { getSeedBanners() }
    }

    private fun loadAppsFromAsset(): List<AppInfo> {
        return try {
            val jsonString = context.assets.open("wajidtechtube-default-rtdb.json").bufferedReader().use { it.readText() }
            parseAppsJson(jsonString, isDirectAppsObject = false)
        } catch (e: Exception) {
            Log.e("StoreRepository", "Failed to load apps from asset JSON", e)
            emptyList()
        }
    }

    private fun loadBannersFromAsset(): List<FeaturedBanner> {
        return try {
            val jsonString = context.assets.open("wajidtechtube-default-rtdb.json").bufferedReader().use { it.readText() }
            parseBannersJson(jsonString, isDirectSlider = false)
        } catch (e: Exception) {
            Log.e("StoreRepository", "Failed to load banners from asset JSON", e)
            getSeedBanners()
        }
    }

    private fun fetchAppsFromNetwork(): List<AppInfo>? {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://wajidtechtube-default-rtdb.firebaseio.com/apps.json")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonString = response.body?.string() ?: return null
                    parseAppsJson(jsonString, isDirectAppsObject = true)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("StoreRepository", "Failed to fetch apps from network REST API", e)
            null
        }
    }

    private fun fetchBannersFromNetwork(): List<FeaturedBanner>? {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://wajidtechtube-default-rtdb.firebaseio.com/slider.json")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonString = response.body?.string() ?: return null
                    parseBannersJson(jsonString, isDirectSlider = true)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("StoreRepository", "Failed to fetch banners from network", e)
            null
        }
    }

    private fun generateCategoriesFromApps(apps: List<AppInfo>): List<Category> {
        val uniqueCats = apps.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct()
        return uniqueCats.map { catName ->
            val catId = catName.lowercase().replace(" ", "_").trim()
            Category(
                categoryId = catId,
                categoryName = catName,
                categoryIcon = getCategoryIconUrl(catName),
                banner = "https://picsum.photos/seed/cat_banner_$catId/600/200"
            )
        }
    }

    private fun getCategoryIconUrl(categoryName: String): String {
        return when (categoryName.lowercase().trim()) {
            "game", "games", "game " -> "https://img.icons8.com/color/120/controller.png"
            "racing" -> "https://img.icons8.com/color/120/racing-helmet.png"
            "tools" -> "https://img.icons8.com/color/120/wrench.png"
            "other" -> "https://img.icons8.com/color/120/dots.png"
            "social" -> "https://img.icons8.com/color/120/facebook-new.png"
            "entertainment" -> "https://img.icons8.com/color/120/youtube-play.png"
            "productivity" -> "https://img.icons8.com/color/120/checked-line.png"
            "puzzle" -> "https://img.icons8.com/color/120/puzzle.png"
            "action" -> "https://img.icons8.com/color/120/sword.png"
            "e-commerce" -> "https://img.icons8.com/color/120/shopping-mall.png"
            else -> "https://img.icons8.com/color/120/google-play.png"
        }
    }

    // Fetch and sync applications from Firebase Realtime Database
    suspend fun fetchApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val remoteApps = fetchAppsFromNetwork()
            if (remoteApps != null && remoteApps.isNotEmpty()) {
                Log.d("StoreRepository", "Fetched ${remoteApps.size} apps dynamically from Firebase RTD.")
                appDao.clearApps()
                appDao.insertApps(remoteApps.map { it.toEntity() })
                
                // Dynamically sync and cache categories too!
                val cats = generateCategoriesFromApps(remoteApps)
                categoryDao.clearCategories()
                categoryDao.insertCategories(cats.map { it.toEntity() })

                return@withContext remoteApps
            }
        } catch (e: Exception) {
            Log.e("StoreRepository", "Firebase fetchApps error - Falling back to asset/Room cache", e)
        }

        // Offline / Asset safety fallback
        var localList = emptyList<AppInfo>()
        try {
            val rawCached = snapshotLocalApps()
            if (rawCached.isNotEmpty()) {
                localList = rawCached
            } else {
                localList = loadAppsFromAsset()
                if (localList.isNotEmpty()) {
                    appDao.clearApps()
                    appDao.insertApps(localList.map { it.toEntity() })
                    
                    val cats = generateCategoriesFromApps(localList)
                    categoryDao.clearCategories()
                    categoryDao.insertCategories(cats.map { it.toEntity() })
                } else {
                    localList = getSeedApps()
                }
            }
        } catch (ex: Exception) {
            localList = getSeedApps()
        }
        return@withContext localList
    }

    // Fetch categories dynamically from apps list
    suspend fun fetchCategories(): List<Category> = withContext(Dispatchers.IO) {
        try {
            val rawCached = snapshotLocalCategories()
            if (rawCached.isNotEmpty()) {
                return@withContext rawCached
            }
        } catch (e: Exception) {
            Log.e("StoreRepository", "fetchCategories local cache error", e)
        }
        
        // If not cached, load all apps to build categories dynamically
        val apps = fetchApps()
        val cats = generateCategoriesFromApps(apps)
        if (cats.isNotEmpty()) {
            categoryDao.clearCategories()
            categoryDao.insertCategories(cats.map { it.toEntity() })
            return@withContext cats
        }
        return@withContext getSeedCategories()
    }

    // Fetch banners
    suspend fun fetchBanners(): List<FeaturedBanner> = withContext(Dispatchers.IO) {
        try {
            val remoteBanners = fetchBannersFromNetwork()
            if (remoteBanners != null && remoteBanners.isNotEmpty()) {
                return@withContext remoteBanners
            }
        } catch (e: Exception) {
            Log.e("StoreRepository", "Firebase fetchBanners network error", e)
        }
        return@withContext loadBannersFromAsset()
    }

    // Fetch Single App by details
    suspend fun fetchAppById(appId: String): AppInfo? = withContext(Dispatchers.IO) {
        try {
            val cached = snapshotLocalApps().find { it.appId == appId }
            if (cached != null) return@withContext cached

            val apps = fetchApps()
            return@withContext apps.find { it.appId == appId } ?: getSeedApps().find { it.appId == appId }
        } catch (e: Exception) {
            Log.e("StoreRepository", "Firebase fetchAppById error", e)
            snapshotLocalApps().find { it.appId == appId } ?: getSeedApps().find { it.appId == appId }
        }
    }

    // Dynamic Search System (Realtime filtering & Online remote query)
    suspend fun searchApps(query: String): List<AppInfo> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        try {
            addRecentSearch(query)
            val allApps = fetchApps()
            return@withContext allApps.filter {
                it.appName.lowercase().contains(cleanQuery) ||
                it.developerName.lowercase().contains(cleanQuery) ||
                it.category.lowercase().contains(cleanQuery) ||
                it.tags.any { tag -> tag.lowercase().contains(cleanQuery) }
            }
        } catch (e: Exception) {
            Log.e("StoreRepository", "Firebase searchApps error - using local filter fallback", e)
        }

        return@withContext snapshotLocalApps().filter {
            it.appName.lowercase().contains(cleanQuery) ||
            it.developerName.lowercase().contains(cleanQuery) ||
            it.category.lowercase().contains(cleanQuery)
        }
    }

    // Install / Uninstall Simulate (using SharedPreferences state triggers)
    fun isAppInstalledFlow(appId: String): Flow<Boolean> = flow {
        emit(sharedPrefs.getBoolean("installed_$appId", false))
        installedChanges
            .filter { it.first == appId }
            .collect { change ->
                emit(change.second)
            }
    }.flowOn(Dispatchers.IO)

    fun isAppFavoriteFlow(appId: String): Flow<Boolean> = flow {
        val favs = sharedPrefs.getStringSet("favorites", emptySet()) ?: emptySet()
        emit(favs.contains(appId))
        favoriteChanges
            .filter { it.first == appId }
            .collect { change ->
                emit(change.second)
            }
    }.flowOn(Dispatchers.IO)

    suspend fun toggleFavorite(appId: String) = withContext(Dispatchers.IO) {
        val favs = sharedPrefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
        val isFav = if (favs.contains(appId)) {
            favs.remove(appId)
            false
        } else {
            favs.add(appId)
            true
        }
        sharedPrefs.edit().putStringSet("favorites", favs).apply()
        favoriteChanges.tryEmit(Pair(appId, isFav))

        // Sync favorite with user document if user logged in
        val currentUserId = sharedPrefs.getString("user_id", "")
        if (!currentUserId.isNullOrBlank()) {
            try {
                firestore.collection("users").document(currentUserId)
                    .update("favorites", favs.toList())
            } catch (e: Exception) {
                Log.e("StoreRepository", "Failed to sync user favorites to Firestore", e)
            }
        }
    }

    suspend fun setInstalledState(appId: String, installed: Boolean) = withContext(Dispatchers.IO) {
        sharedPrefs.edit().putBoolean("installed_$appId", installed).apply()
        installedChanges.tryEmit(Pair(appId, installed))

        // Sync list of installed apps to user record
        val currentUserId = sharedPrefs.getString("user_id", "")
        if (!currentUserId.isNullOrBlank()) {
            try {
                val installedList = getSimulatedInstalledAppIds()
                firestore.collection("users").document(currentUserId)
                    .update("installedApps", installedList)
            } catch (e: Exception) {
                Log.e("StoreRepository", "Failed to sync user installedApps to Firestore", e)
            }
        }
    }

    private fun getSimulatedInstalledAppIds(): List<String> {
        val list = mutableListOf<String>()
        getSeedApps().forEach {
            if (sharedPrefs.getBoolean("installed_${it.appId}", false)) {
                list.add(it.appId)
            }
        }
        return list
    }

    // Seeding Implementations
    private suspend fun seedAppsToFirestore(apps: List<AppInfo>) {
        try {
            for (app in apps) {
                firestore.collection("apps").document(app.appId).set(app)
            }
            Log.d("StoreRepository", "Firebase collection 'apps' successfully seeded.")
        } catch (e: Exception) {
            Log.e("StoreRepository", "Firebase Apps seeding failed: ", e)
        }
    }

    private suspend fun seedCategoriesToFirestore(categories: List<Category>) {
        try {
            for (category in categories) {
                firestore.collection("categories").document(category.categoryId).set(category)
            }
            Log.d("StoreRepository", "Firebase collection 'categories' successfully seeded.")
        } catch (e: Exception) {
            Log.e("StoreRepository", "Firebase Categories seeding failed: ", e)
        }
    }

    private suspend fun seedBannersToFirestore(banners: List<FeaturedBanner>) {
        try {
            for (banner in banners) {
                firestore.collection("featured_banners").document(banner.bannerId).set(banner)
            }
            Log.d("StoreRepository", "Firebase collection 'featured_banners' successfully seeded.")
        } catch (e: Exception) {
            Log.e("StoreRepository", "Firebase Banners seeding failed: ", e)
        }
    }

    // Read direct list of apps from local Room (synchronous/blocking for repository fallbacks)
    private fun snapshotLocalApps(): List<AppInfo> {
        return try {
            // We read one-shot
            val raw = db.openHelper.readableDatabase.query("SELECT * FROM cached_apps")
            val list = mutableListOf<AppInfo>()
            if (raw.moveToFirst()) {
                do {
                    val appIdIdx = raw.getColumnIndex("appId")
                    val appNameIdx = raw.getColumnIndex("appName")
                    val appPackageIdx = raw.getColumnIndex("appPackage")
                    val appIconIdx = raw.getColumnIndex("appIcon")
                    val appBannerIdx = raw.getColumnIndex("appBanner")
                    val categoryIdx = raw.getColumnIndex("category")
                    val ratingIdx = raw.getColumnIndex("rating")
                    val downloadsIdx = raw.getColumnIndex("downloads")
                    val sizeIdx = raw.getColumnIndex("size")
                    val developerNameIdx = raw.getColumnIndex("developerName")
                    val shortDescriptionIdx = raw.getColumnIndex("shortDescription")
                    val fullDescriptionIdx = raw.getColumnIndex("fullDescription")
                    val featuredIdx = raw.getColumnIndex("featured")
                    val topChartIdx = raw.getColumnIndex("topChart")
                    val trendingIdx = raw.getColumnIndex("trending")
                    val premiumIdx = raw.getColumnIndex("premium")

                    list.add(
                        AppInfo(
                            appId = if (appIdIdx >= 0) raw.getString(appIdIdx) else "",
                            appName = if (appNameIdx >= 0) raw.getString(appNameIdx) else "",
                            appPackage = if (appPackageIdx >= 0) raw.getString(appPackageIdx) else "",
                            appIcon = if (appIconIdx >= 0) raw.getString(appIconIdx) else "",
                            appBanner = if (appBannerIdx >= 0) raw.getString(appBannerIdx) else "",
                            category = if (categoryIdx >= 0) raw.getString(categoryIdx) else "",
                            rating = if (ratingIdx >= 0) raw.getDouble(ratingIdx) else 0.0,
                            downloads = if (downloadsIdx >= 0) raw.getString(downloadsIdx) else "",
                            size = if (sizeIdx >= 0) raw.getString(sizeIdx) else "",
                            developerName = if (developerNameIdx >= 0) raw.getString(developerNameIdx) else "",
                            shortDescription = if (shortDescriptionIdx >= 0) raw.getString(shortDescriptionIdx) else "",
                            fullDescription = if (fullDescriptionIdx >= 0) raw.getString(fullDescriptionIdx) else "",
                            featured = if (featuredIdx >= 0) raw.getInt(featuredIdx) == 1 else false,
                            topChart = if (topChartIdx >= 0) raw.getInt(topChartIdx) == 1 else false,
                            trending = if (trendingIdx >= 0) raw.getInt(trendingIdx) == 1 else false,
                            premium = if (premiumIdx >= 0) raw.getInt(premiumIdx) == 1 else false
                        )
                    )
                } while (raw.moveToNext())
            }
            raw.close()
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun snapshotLocalCategories(): List<Category> {
        return try {
            val raw = db.openHelper.readableDatabase.query("SELECT * FROM cached_categories")
            val list = mutableListOf<Category>()
            if (raw.moveToFirst()) {
                do {
                    val idIdx = raw.getColumnIndex("categoryId")
                    val nameIdx = raw.getColumnIndex("categoryName")
                    val iconIdx = raw.getColumnIndex("categoryIcon")
                    list.add(
                        Category(
                            categoryId = if (idIdx >= 0) raw.getString(idIdx) else "",
                            categoryName = if (nameIdx >= 0) raw.getString(nameIdx) else "",
                            categoryIcon = if (iconIdx >= 0) raw.getString(iconIdx) else ""
                        )
                    )
                } while (raw.moveToNext())
            }
            raw.close()
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    // DTO / Entity conversion utilities
    private fun CachedAppEntity.toModel() = AppInfo(
        appId = appId,
        appName = appName,
        appPackage = appPackage,
        appIcon = appIcon,
        appBanner = appBanner,
        category = category,
        rating = rating,
        downloads = downloads,
        size = size,
        developerName = developerName,
        shortDescription = shortDescription,
        fullDescription = fullDescription,
        featured = featured,
        topChart = topChart,
        trending = trending,
        premium = premium
    )

    private fun AppInfo.toEntity() = CachedAppEntity(
        appId = appId,
        appName = appName,
        appPackage = appPackage,
        appIcon = appIcon,
        appBanner = appBanner,
        category = category,
        rating = rating,
        downloads = downloads,
        size = size,
        developerName = developerName,
        shortDescription = shortDescription,
        fullDescription = fullDescription,
        featured = featured,
        topChart = topChart,
        trending = trending,
        premium = premium
    )

    private fun CachedCategoryEntity.toModel() = Category(
        categoryId = categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon
    )

    private fun Category.toEntity() = CachedCategoryEntity(
        categoryId = categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon
    )

    // Raw Seed Generators - Inspired by Play Store look & feel
    fun getSeedApps(): List<AppInfo> {
        return listOf(
            AppInfo(
                appId = "chrono_trigger_3d",
                appName = "Chrono Trigger 3D: Echoes of Time",
                appPackage = "com.developer.chrono3d",
                appIcon = "https://picsum.photos/seed/chrono_icon/200/200",
                appBanner = "https://picsum.photos/seed/chrono_banner/600/300",
                screenshots = listOf(
                    "https://picsum.photos/seed/chrono_sc1/500/800",
                    "https://picsum.photos/seed/chrono_sc2/500/800",
                    "https://picsum.photos/seed/chrono_sc3/500/800"
                ),
                shortDescription = "The legendary RPG returns reimagined in striking 3D graphics.",
                fullDescription = "Chrono Trigger 3D: Echoes of Time is an award-winning action RPG. Journey through past, present, and the future to rescue our decaying planet! Featuring standard Material gameplay layouts, custom high-fidelity battle mechanics, orchestral score remixes, and beautiful anime-style narrative sequences that will touch your soul.",
                category = "Action",
                rating = 4.9,
                downloads = "10M+",
                version = "2.4.1",
                size = "1.2 GB",
                developerName = "Silver Horizon Games",
                updatedDate = "May 25, 2026",
                featured = true,
                topChart = true,
                trending = true,
                premium = false,
                apkUrl = "https://example.com/downloads/chrono_trigger.apk",
                playStoreUrl = "https://play.google.com/store",
                tags = listOf("Action", "RPG", "Anime", "Sci-Fi"),
                minimumAndroid = "9.0+",
                permissions = listOf("Internet", "Write External Storage", "Billing Access"),
                videoTrailer = "",
                releaseNotes = "Improved shader compilation speeds and fixed screen-resize issues on folding devices."
            ),
            AppInfo(
                appId = "apex_quantum",
                appName = "Apex Quantum: BR Mobile",
                appPackage = "com.developer.apexquantum",
                appIcon = "https://picsum.photos/seed/apex_icon/200/200",
                appBanner = "https://picsum.photos/seed/apex_banner/600/300",
                screenshots = listOf(
                    "https://picsum.photos/seed/apex_sc1/500/800",
                    "https://picsum.photos/seed/apex_sc2/500/800"
                ),
                shortDescription = "Fast-paced squad battle royale set in a cybernetic dome.",
                fullDescription = "Deploy into Apex Quantum, the definitive futuristic team battle royale. Combine specialized hero combat abilities with rapid gunplay action. Execute wall-run cascades and shield overcharges to defeat opposing teams in matches tailored for vertical mobile action.",
                category = "Action",
                rating = 4.7,
                downloads = "50M+",
                version = "1.0.8",
                size = "2.5 GB",
                developerName = "Vulkan Interactive",
                updatedDate = "May 10, 2026",
                featured = true,
                topChart = true,
                trending = false,
                premium = false,
                apkUrl = "https://example.com/downloads/apex_quantum.apk",
                playStoreUrl = "https://play.google.com/store",
                tags = listOf("Action", "Shooter", "Sci-Fi", "Multiplayer"),
                minimumAndroid = "8.0+",
                permissions = listOf("Internet", "Microphone Access", "Camera Access"),
                videoTrailer = "",
                releaseNotes = "Unveiled Season 2 Map with custom neon weather storms."
            ),
            AppInfo(
                appId = "pixel_art_pro",
                appName = "Pixel Art Pro: Vectors & Sprites",
                appPackage = "com.developer.pixelartpro",
                appIcon = "https://picsum.photos/seed/pixel_icon/200/200",
                appBanner = "https://picsum.photos/seed/pixel_banner/600/300",
                screenshots = listOf(
                    "https://picsum.photos/seed/pixel_sc1/500/800",
                    "https://picsum.photos/seed/pixel_sc2/500/800"
                ),
                shortDescription = "Professional 8-bit sprite maker and design editor.",
                fullDescription = "Pixel Art Pro provides developers, creators, and game animators with clean 8-bit canvas engines. Design breathtaking avatars, game sprites, sheet grids, and vector frames. Import png arrays easily and export animation gifs in 4k quality directly to social grids.",
                category = "Art & Design",
                rating = 4.6,
                downloads = "5M+",
                version = "4.2",
                size = "45 MB",
                developerName = "Pixelate Lab LLC",
                updatedDate = "Mar 11, 2026",
                featured = false,
                topChart = false,
                trending = true,
                premium = true,
                apkUrl = "https://example.com/downloads/pixel_art.apk",
                playStoreUrl = "https://play.google.com/store",
                tags = listOf("Art & Design", "Utility", "Creative"),
                minimumAndroid = "7.0+",
                permissions = listOf("Storage Write Permission"),
                videoTrailer = "",
                releaseNotes = "Added onion-skin layer support for character animations!"
            ),
            AppInfo(
                appId = "lightroom_studio",
                appName = "Lightroom Studio: Filters & Presets",
                appPackage = "com.developer.lightroom",
                appIcon = "https://picsum.photos/seed/photo_icon/200/200",
                appBanner = "https://picsum.photos/seed/photo_banner/600/300",
                screenshots = listOf(
                    "https://picsum.photos/seed/photo_sc1/500/800",
                    "https://picsum.photos/seed/photo_sc2/500/800"
                ),
                shortDescription = "Create breathtaking high-contrast visual filters.",
                fullDescription = "Transform your regular snapshots into publication-quality artistic imagery. Lightroom Studio provides a broad selection of AI-driven color matching, custom exposure histograms, perspective grids, and fine saturation dials. Adjust focus fields and remove background elements in 1 click.",
                category = "Photography",
                rating = 4.8,
                downloads = "100M+",
                version = "9.1",
                size = "92 MB",
                developerName = "Creative Forge Corp",
                updatedDate = "May 18, 2026",
                featured = true,
                topChart = false,
                trending = true,
                premium = false,
                apkUrl = "",
                playStoreUrl = "https://play.google.com/store",
                tags = listOf("Photography", "Filters", "AI Editor"),
                minimumAndroid = "8.0+",
                permissions = listOf("Camera Access", "Storage Read Access"),
                videoTrailer = "",
                releaseNotes = "Integrated custom bokeh neural simulation filters."
            ),
            AppInfo(
                appId = "fit_tracker",
                appName = "FitPulse Tracker: Running & Gym",
                appPackage = "com.developer.fitpulse",
                appIcon = "https://picsum.photos/seed/fit_icon/200/200",
                appBanner = "https://picsum.photos/seed/fit_banner/600/300",
                screenshots = listOf(
                    "https://picsum.photos/seed/fit_sc1/500/800"
                ),
                shortDescription = "Track running trails, calories, and weightlifting progress.",
                fullDescription = "Maintain consistent athletic discipline. FitPulse is your daily coach for physical wellness. Tracks running kilometers in real-time, designs adaptive gym weight splits, provides customizable nutritional advice, and alerts you to water intervals to optimize recovery rate.",
                category = "Health & Fitness",
                rating = 4.5,
                downloads = "2M+",
                version = "3.1.2",
                size = "34 MB",
                developerName = "Vitality Labs",
                updatedDate = "April 29, 2026",
                featured = false,
                topChart = true,
                trending = false,
                premium = false,
                apkUrl = "",
                playStoreUrl = "https://play.google.com/store",
                tags = listOf("Health", "GPS Tracker", "Gym"),
                minimumAndroid = "7.0+",
                permissions = listOf("GPS Location Tracker", "Notification Alerts"),
                videoTrailer = "",
                releaseNotes = "Improved Bluetooth coupling with smart fitness rings."
            ),
            AppInfo(
                appId = "pocket_codex",
                appName = "Pocket Codex: Learn Kotlin",
                appPackage = "com.developer.pocketcodex",
                appIcon = "https://picsum.photos/seed/code_icon/200/200",
                appBanner = "https://picsum.photos/seed/code_banner/600/300",
                screenshots = listOf(
                    "https://picsum.photos/seed/code_sc1/500/800",
                    "https://picsum.photos/seed/code_sc2/500/800"
                ),
                shortDescription = "Bite-size Kotlin challenges and real-time syntax checker.",
                fullDescription = "Start mastering Kotlin right from your notification panel! Pocket Codex guides you from standard variable declarations to advanced Coroutines flow structures using simple daily games. Build fully working visual apps inside our sandbox sandbox emulator and earn badges.",
                category = "Education",
                rating = 4.8,
                downloads = "500K+",
                version = "1.5",
                size = "22 MB",
                developerName = "Aria Learning Group",
                updatedDate = "May 01, 2026",
                featured = false,
                topChart = false,
                trending = true,
                premium = false,
                apkUrl = "",
                playStoreUrl = "https://play.google.com/store",
                tags = listOf("Education", "Programming", "Coding"),
                minimumAndroid = "6.0+",
                permissions = listOf("Internet Access"),
                videoTrailer = "",
                releaseNotes = "Added 12 new interactive Jetpack Compose styling layout lessons!"
            ),
            AppInfo(
                appId = "cosmic_racer",
                appName = "Cosmic Racer: Nebula Run",
                appPackage = "com.developer.cosmicracer",
                appIcon = "https://picsum.photos/seed/racer_icon/200/200",
                appBanner = "https://picsum.photos/seed/racer_banner/600/300",
                screenshots = listOf(
                    "https://picsum.photos/seed/racer_sc1/500/800"
                ),
                shortDescription = "Anti-gravity starship racing through spiral galaxies.",
                fullDescription = "Hold tight in Cosmic Racer, a thrilling supersonic hover racer. Dash along twisted nebula rings and black-hole bypasses in 60 frames per second. Customize rocket engines, drift across cosmic particles, and compete in the ultimate galaxy grand prix against online space champions.",
                category = "Casual & Arcade",
                rating = 4.7,
                downloads = "5M+",
                version = "1.92",
                size = "410 MB",
                developerName = "Nebula Studios",
                updatedDate = "May 22, 2026",
                featured = true,
                topChart = true,
                trending = false,
                premium = false,
                apkUrl = "",
                playStoreUrl = "https://play.google.com/store",
                tags = listOf("Casual & Arcade", "Racing", "Science Fiction"),
                minimumAndroid = "8.0+",
                permissions = listOf("Internet Access", "Vibration Motor"),
                videoTrailer = "",
                releaseNotes = "Improved gyro tilting controls and added new 'Siri-9' planet tracks."
            )
        )
    }

    fun getSeedCategories(): List<Category> {
        return listOf(
            Category("action", "Action", "https://picsum.photos/seed/cat_action/200/200", "https://picsum.photos/seed/cat_banner_action/600/200"),
            Category("photography", "Photography", "https://picsum.photos/seed/cat_photo/200/200", "https://picsum.photos/seed/cat_banner_photo/600/200"),
            Category("art_design", "Art & Design", "https://picsum.photos/seed/cat_art/200/200", "https://picsum.photos/seed/cat_banner_art/600/200"),
            Category("health_fitness", "Health & Fitness", "https://picsum.photos/seed/cat_health/200/200", "https://picsum.photos/seed/cat_banner_health/600/200"),
            Category("education", "Education", "https://picsum.photos/seed/cat_edu/200/200", "https://picsum.photos/seed/cat_banner_edu/600/200"),
            Category("casual", "Casual & Arcade", "https://picsum.photos/seed/cat_casual/200/200", "https://picsum.photos/seed/cat_banner_casual/600/200")
        )
    }

    fun getSeedBanners(): List<FeaturedBanner> {
        return listOf(
            FeaturedBanner(
                bannerId = "banner_chrono",
                imageUrl = "https://picsum.photos/seed/chrono_banner/600/300",
                targetAppId = "chrono_trigger_3d",
                title = "Legend Reborn",
                subtitle = "RPG masterpiece Chrono Trigger 3D is finally here!"
            ),
            FeaturedBanner(
                bannerId = "banner_apex",
                imageUrl = "https://picsum.photos/seed/apex_banner/600/300",
                targetAppId = "apex_quantum",
                title = "Season 2 Live",
                subtitle = "Apex Quantum unleashes Neon storm battles"
            ),
            FeaturedBanner(
                bannerId = "banner_lightroom",
                imageUrl = "https://picsum.photos/seed/photo_banner/600/300",
                targetAppId = "lightroom_studio",
                title = "AI Editing Supreme",
                subtitle = "Try Lightroom Studio perspective adjustments today!"
            )
        )
    }
}
