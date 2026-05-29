package com.example.data.model

data class AppInfo(
    var appId: String = "",
    var appName: String = "",
    var appPackage: String = "",
    var appIcon: String = "",
    var appBanner: String = "",
    var screenshots: List<String> = emptyList(),
    var shortDescription: String = "",
    var fullDescription: String = "",
    var category: String = "",
    var rating: Double = 0.0,
    var downloads: String = "",
    var version: String = "",
    var size: String = "",
    var developerName: String = "",
    var updatedDate: String = "",
    var featured: Boolean = false,
    var topChart: Boolean = false,
    var trending: Boolean = false,
    var premium: Boolean = false,
    var apkUrl: String = "",
    var playStoreUrl: String = "",
    var tags: List<String> = emptyList(),
    var minimumAndroid: String = "",
    var permissions: List<String> = emptyList(),
    var videoTrailer: String = "",
    var releaseNotes: String = ""
)

data class Category(
    var categoryId: String = "",
    var categoryName: String = "",
    var categoryIcon: String = "",
    var banner: String = ""
)

data class FeaturedBanner(
    var bannerId: String = "",
    var imageUrl: String = "",
    var targetAppId: String = "",
    var title: String = "",
    var subtitle: String = ""
)

data class UserProfile(
    var userId: String = "",
    var name: String = "",
    var email: String = "",
    var photo: String = "",
    var favorites: List<String> = emptyList(),
    var downloads: List<String> = emptyList(),
    var installedApps: List<String> = emptyList(),
    var createdAt: Long = 0L
)

data class Review(
    var reviewId: String = "",
    var userDisplayName: String = "",
    var rating: Double = 0.0,
    var comment: String = "",
    var dateString: String = ""
)
