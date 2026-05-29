package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.cache.RecentSearchEntity
import com.example.data.model.*
import com.example.ui.viewmodel.*
import com.example.ui.theme.PlayGreenLight
import com.example.ui.theme.RatingGold
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Custom self-contained skeleton shimmer background modifier for Material 3
@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslation"
    )

    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f),
    )

    return this.background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = androidx.compose.ui.geometry.Offset(translateAnim - 200f, translateAnim - 200f),
            end = androidx.compose.ui.geometry.Offset(translateAnim, translateAnim)
        )
    )
}

// -----------------------------------------------------------------------------
// MAIN HOME FEED SCREEN
// -----------------------------------------------------------------------------
@Composable
fun HomeFeedScreen(
    viewModel: StoreViewModel,
    onAppClick: (String) -> Unit,
    onCategorySelected: (String) -> Unit
) {
    val homeState by viewModel.homeUiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_feed_screen")
    ) {
        when (val state = homeState) {
            is HomeUiState.Loading -> {
                HomeShimmerLoading()
            }
            is HomeUiState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    SleekSearchHeader(
                        onSearchClick = { onCategorySelected("") },
                        onAvatarClick = { /* Clicked avatar */ }
                    )
                    SleekSecondaryTabs()

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                    // 1. Featured Auto-sliding Banners Slider
                    if (state.banners.isNotEmpty()) {
                        item {
                            FeaturedSliderSection(banners = state.banners, onAppClick = onAppClick)
                        }
                    }

                    // 2. Interactive Quick Categories
                    if (state.categories.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            CategoriesRowSection(
                                categories = state.categories,
                                onCategorySelected = onCategorySelected
                            )
                        }
                    }

                    // 3. Featured Games & Apps (Action Section)
                    if (state.featuredApps.isNotEmpty()) {
                        item {
                            AppHorizontalShowcase(
                                title = "Top Action Games",
                                apps = state.featuredApps,
                                onAppClick = onAppClick
                            )
                        }
                    }

                    // 4. Recommendation Module
                    if (state.recommendedApps.isNotEmpty()) {
                        item {
                            AppGridShowcase(
                                title = "Recommended For You",
                                apps = state.recommendedApps,
                                onAppClick = onAppClick
                            )
                        }
                    }

                    // 5. High-fidelity Top Charts List
                    if (state.topChartApps.isNotEmpty()) {
                        item {
                            TopChartsSection(
                                title = "Top Charts",
                                apps = state.topChartApps,
                                onAppClick = onAppClick
                            )
                        }
                    }

                    // 6. Trending Applications
                    if (state.trendingApps.isNotEmpty()) {
                        item {
                            AppHorizontalShowcase(
                                title = "Trending Apps",
                                apps = state.trendingApps,
                                onAppClick = onAppClick
                            )
                        }
                    }

                    // 7. Premium App Listings
                    if (state.premiumApps.isNotEmpty()) {
                        item {
                            AppHorizontalShowcase(
                                title = "Premium Outlets",
                                apps = state.premiumApps,
                                onAppClick = onAppClick
                            )
                        }
                    }
                }
                }
            }
            is HomeUiState.Error -> {
                ErrorStateView(message = state.message) {
                    viewModel.loadHomeData()
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// FEATURED AUTO-SLIDING BANNER SLIDER RESILIENT VIEW
// -----------------------------------------------------------------------------
@Composable
fun FeaturedSliderSection(
    banners: List<FeaturedBanner>,
    onAppClick: (String) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }

    // Auto-slide effect every 5 seconds
    LaunchedEffect(banners) {
        if (banners.isNotEmpty()) {
            while (true) {
                delay(5000)
                currentIndex = (currentIndex + 1) % banners.size
            }
        }
    }

    if (banners.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        val activeBanner = banners[currentIndex]

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    if (activeBanner.targetAppId.isNotEmpty()) {
                        onAppClick(activeBanner.targetAppId)
                    }
                }
                .testTag("featured_banner_slider")
        ) {
            // Async Image with gradient overlay and text descriptions
            AsyncImage(
                model = activeBanner.imageUrl,
                contentDescription = activeBanner.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Scrim overlay to make text highly legible
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 100f
                        )
                    )
            )

            // Banner Information Layout
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = activeBanner.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = activeBanner.subtitle,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }

        // Horizontal dot indicators below slider
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            banners.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(horizontal = 2.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == currentIndex) MaterialTheme.colorScheme.primary
                            else Color.Gray.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// HORIZONTAL CATEGORIES ROW SECTION
// -----------------------------------------------------------------------------
@Composable
fun CategoriesRowSection(
    categories: List<Category>,
    onCategorySelected: (String) -> Unit
) {
    Column {
        Text(
            text = "Categories",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                Card(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .clickable { onCategorySelected(category.categoryName) }
                        .testTag("category_chip_${category.categoryId}"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(19.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Standard generic icon mappings to keep rendering functional without network
                        val categoryIcon: ImageVector = when (category.categoryId) {
                            "action" -> Icons.Default.PlayArrow
                            "photography" -> Icons.Default.CheckCircle
                            "art_design" -> Icons.Default.Edit
                            "health_fitness" -> Icons.Default.Favorite
                            "education" -> Icons.Default.Search
                            else -> Icons.Default.Star
                        }

                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = category.categoryName,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = category.categoryName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// APP HORIZONTAL LIST COMPONENT WITH ROUNDED CARDS
// -----------------------------------------------------------------------------
@Composable
fun AppHorizontalShowcase(
    title: String,
    apps: List<AppInfo>,
    onAppClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(apps) { app ->
                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .clickable { onAppClick(app.appId) }
                        .testTag("app_item_${app.appId}")
                ) {
                    // App icon with beautiful rounded corner shapes (Google Play look modified for Sleek card aesthetic)
                    Card(
                        modifier = Modifier.size(100.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            AsyncImage(
                                model = app.appIcon,
                                contentDescription = app.appName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = app.appName,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = app.rating.toString(),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = RatingGold,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = app.size,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// APP GRID RECOMMENDATION MODULE
// -----------------------------------------------------------------------------
@Composable
fun AppGridShowcase(
    title: String,
    apps: List<AppInfo>,
    onAppClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Show 2 rows of apps
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            apps.chunked(2).forEach { rowApps ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowApps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAppClick(app.appId) }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Card(
                                modifier = Modifier.size(64.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    AsyncImage(
                                        model = app.appIcon,
                                        contentDescription = app.appName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = app.appName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.category,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${app.rating}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = RatingGold,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                    // Handle odd collections by centering spacer
                    if (rowApps.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TOP CHARTS LIST SUBSECTION WITH NUMBERS
// -----------------------------------------------------------------------------
@Composable
fun TopChartsSection(
    title: String,
    apps: List<AppInfo>,
    onAppClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            apps.take(5).forEachIndexed { index, app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppClick(app.appId) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chart Index number
                    Text(
                        text = (index + 1).toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center
                    )

                    Card(
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            AsyncImage(
                                model = app.appIcon,
                                contentDescription = app.appName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.developerName,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${app.size} • ${app.downloads} downloads",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }

                    // Rating Badge
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = app.rating.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = RatingGold,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SKELETON LOADING EXPERIENCE FOR PERFECT FEEL
// -----------------------------------------------------------------------------
@Composable
fun HomeShimmerLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Shimmer slider box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .shimmerEffect()
        )

        // Shimmer categories tags
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .shimmerEffect()
                )
            }
        }

        // Shimmer Horizontal row heading
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(20.dp)
                .shimmerEffect()
        )

        // Shimmer horizontal row apps lists
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(3) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .shimmerEffect()
                    )
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(14.dp)
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// HIGH PROFILE APP DETAILS PAGE
// -----------------------------------------------------------------------------
@Composable
fun AppDetailScreen(
    appId: String,
    viewModel: StoreViewModel,
    onBack: () -> Unit,
    onRelatedAppClick: (String) -> Unit
) {
    val detailState by viewModel.detailUiState.collectAsState()

    // Trigger select app inside model scope
    LaunchedEffect(appId) {
        viewModel.selectAppDetail(appId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_details_page")
    ) {
        when (val state = detailState) {
            is DetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlayGreenLight)
                }
            }
            is DetailUiState.Success -> {
                val app = state.app
                val context = LocalContext.current

                Column(modifier = Modifier.fillMaxSize()) {
                    // Custom Tool Bar with back links
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.toggleWishlist(app.appId) }) {
                            Icon(
                                imageVector = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                tint = if (state.isFavorite) Color.Red else LocalContentColor.current,
                                contentDescription = "Favorite/Wishlist"
                            )
                        }
                        IconButton(onClick = {
                            // Native share action
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, app.appName)
                                putExtra(android.content.Intent.EXTRA_TEXT, "Checkout ${app.appName} on Aria Store!")
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share via"))
                        }) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share App")
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // Header Box: Icon, Title, Developer, etc.
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color.LightGray.copy(alpha = 0.2f))
                                ) {
                                    AsyncImage(
                                        model = app.appIcon,
                                        contentDescription = app.appName,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 24.sp
                                    )
                                    Text(
                                        text = app.developerName,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Contains Ads • In-app purchases",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // App Store Metrics Rows
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rating
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.rating.toString(),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = RatingGold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(text = "Reviews", fontSize = 11.sp, color = Color.Gray)
                                }

                                VerticalDivider(modifier = Modifier.height(28.dp))

                                // Dynamic Size
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(text = app.size, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                VerticalDivider(modifier = Modifier.height(28.dp))

                                // Downloads
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = app.downloads,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(text = "Downloads", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        // Play Store High fidelity morphing Action Install Button
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            InstallSimulatorButton(
                                isInstalled = state.isInstalled,
                                apkUrl = app.apkUrl,
                                onInstallStart = { viewModel.installSimulation(app.appId) },
                                onUninstall = { viewModel.uninstallSimulation(app.appId) },
                                onOpen = {
                                    // Trigger simple visual message
                                    android.widget.Toast.makeText(context, "Opening ${app.appName}!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // App Horizontal Screenshots Slider
                        if (app.screenshots.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Screenshots",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(app.screenshots) { screenshot ->
                                        Box(
                                            modifier = Modifier
                                                .width(135.dp)
                                                .height(240.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.LightGray.copy(alpha = 0.2f))
                                        ) {
                                            AsyncImage(
                                                model = screenshot,
                                                contentDescription = "App Screenshot",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // App About Text / full Description accordion
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "About this game",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = app.shortDescription,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = app.fullDescription,
                                fontSize = 13.sp,
                                color = Color.Gray,
                                lineHeight = 18.sp
                            )
                        }

                        // Game tags
                        if (app.tags.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                FlowRowCustom(
                                    tags = app.tags,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Build details grid
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Specifications",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            SpecRow("Version", app.version)
                            SpecRow("Updated On", app.updatedDate)
                            SpecRow("Requires Android", app.minimumAndroid)
                        }

                        // Dynamic reviews and stars simulator
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            ReviewsListSection(appRating = app.rating)
                        }

                        // Related Apps / Games Section
                        if (state.relatedApps.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                AppHorizontalShowcase(
                                    title = "Related Items",
                                    apps = state.relatedApps,
                                    onAppClick = onRelatedAppClick
                                )
                            }
                        }
                    }
                }
            }
            is DetailUiState.Error -> {
                ErrorStateView(message = state.message) {
                    viewModel.selectAppDetail(appId)
                }
            }
        }
    }
}

// Minimal FlowRow drawing for tags using custom Compose layout loops
@Composable
fun FlowRowCustom(tags: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.LightGray.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = tag, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SpecRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, fontSize = 13.sp, color = Color.Gray)
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Color.LightGray.copy(alpha = 0.4f))
    }
}

// -----------------------------------------------------------------------------
// APP INSTALL PROGRESS SIMULATION BUTTON COMPOSE
// -----------------------------------------------------------------------------
@Composable
fun InstallSimulatorButton(
    isInstalled: Boolean,
    apkUrl: String = "",
    onInstallStart: () -> Unit,
    onUninstall: () -> Unit,
    onOpen: () -> Unit
) {
    var installProgress by remember { mutableIntStateOf(-1) }
    var isInstalling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(isInstalled) {
        if (!isInstalled) {
            installProgress = -1
            isInstalling = false
        }
    }

    if (isInstalled) {
        // App is installed: Show Open & Uninstall Action grids side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onUninstall,
                modifier = Modifier.weight(1f).height(44.dp).testTag("uninstall_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
            ) {
                Text(text = "Uninstall", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Button(
                onClick = onOpen,
                modifier = Modifier.weight(1f).height(44.dp).testTag("open_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PlayGreenLight)
            ) {
                Text(text = "Open", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            }
        }
    } else if (isInstalling) {
        // App is installing: show animated circular bar or text indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.LightGray.copy(alpha = 0.3f))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    progress = { installProgress.toFloat() / 100f },
                    modifier = Modifier.size(20.dp),
                    color = PlayGreenLight,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Installing...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "$installProgress%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        // Not installed and not installing: Show Install button
        Button(
            onClick = {
                if (apkUrl.isNotEmpty()) {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(apkUrl)
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                isInstalling = true
                installProgress = 0
                scope.launch {
                    // Gradual installment progression simulation loops
                    while (installProgress < 100) {
                        delay(400)
                        installProgress += (5..20).random()
                        if (installProgress > 100) installProgress = 100
                    }
                    onInstallStart()
                    isInstalling = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("install_button"),
            colors = ButtonDefaults.buttonColors(containerColor = PlayGreenLight)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download Info Icon",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Install", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// REVIEWS ACCORDION SECTION
// -----------------------------------------------------------------------------
@Composable
fun ReviewsListSection(appRating: Double) {
    var reviews by remember { 
        mutableStateOf(
            listOf(
                Triple("Marcus Vance", 5.0, "This is incredibly fast! The UI is slick and looks exactly like the actual app store. Works perfectly offline too."),
                Triple("Sophia Reed", 4.0, "Beautiful layout adjustments! Dynamic database loading features are stellar. Highly recommended emulator build.")
            )
        )
    }

    var newReviewText by remember { mutableStateOf("") }
    var newReviewRating by remember { mutableDoubleStateOf(5.0) }
    var isReviewing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Ratings and Reviews",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Mock Summary Metrics
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Text(text = appRating.toString(), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Row {
                    repeat(5) { i ->
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (i < appRating.toInt()) RatingGold else Color.LightGray,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(text = "Verified reviews", fontSize = 10.sp, color = Color.Gray)
            }

            // Small horizontal bar distribution
            Column(modifier = Modifier.weight(1f)) {
                repeat(5) { i ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    ) {
                        Text(text = (5 - i).toString(), fontSize = 8.sp, modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.LightGray.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(if (i == 0) 0.8f else if (i == 1) 0.15f else 0.05f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(PlayGreenLight)
                            )
                        }
                    }
                }
            }
        }

        if (isReviewing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray.copy(alpha = 0.1f))
                    .padding(12.dp)
                    .padding(bottom = 8.dp)
            ) {
                Text("Write a Review", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(5) { i ->
                        Icon(
                            imageVector = if (i < newReviewRating.toInt()) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Rate ${i + 1} stars",
                            modifier = Modifier
                                .clickable { newReviewRating = (i + 1).toDouble() }
                                .padding(4.dp)
                                .size(28.dp),
                            tint = RatingGold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = newReviewText,
                    onValueChange = { newReviewText = it },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    placeholder = { Text("Describe your experience (optional)") },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { 
                        isReviewing = false
                        newReviewText = ""
                        newReviewRating = 5.0
                    }) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            reviews = listOf(Triple("You", newReviewRating, newReviewText)) + reviews
                            isReviewing = false
                            newReviewText = ""
                            newReviewRating = 5.0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PlayGreenLight)
                    ) {
                        Text("Submit", color = Color.White)
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = { isReviewing = true },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text("Write a Review", color = PlayGreenLight)
            }
        }

        reviews.forEach { (user, starResult, comment) ->
            ReviewRow(user, starResult, comment)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ReviewRow(user: String, rating: Double, comment: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray.copy(alpha = 0.15f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = user, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row {
                repeat(5) { i ->
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (i < rating.toInt()) RatingGold else Color.LightGray,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = comment, fontSize = 11.sp, color = Color.DarkGray)
    }
}

// -----------------------------------------------------------------------------
// SEARCH SCREEN PANEL WITH REAL-TIME FILTERINGS
// -----------------------------------------------------------------------------
@Composable
fun SearchScreen(
    viewModel: StoreViewModel,
    onAppClick: (String) -> Unit
) {
    val searchState by viewModel.searchUiState.collectAsState()
    var searchInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_view_screen")
    ) {
        // Google Play style search bar header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchInput,
                onValueChange = {
                    searchInput = it
                    viewModel.performSearch(it)
                },
                placeholder = { Text("Search apps & games") },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("search_text_input"),
                singleLine = true,
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search Icon")
                },
                trailingIcon = {
                    if (searchInput.isNotEmpty()) {
                        IconButton(onClick = {
                            searchInput = ""
                            viewModel.performSearch("")
                        }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { viewModel.performSearch(searchInput) }
                ),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            when (val state = searchState) {
                is SearchUiState.Idle -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (state.recentSearches.isEmpty()) {
                            // Centered empty state
                            Column(
                                modifier = Modifier.fillMaxSize().padding(top = 100.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Store",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Ready to discover games?",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Search by titles, category tags or builders above",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Recent Searches", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Clear history",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { viewModel.clearSearchHistory() }
                                )
                            }

                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(state.recentSearches) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searchInput = item.query
                                                viewModel.performSearch(item.query)
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "History",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(text = item.query, fontSize = 14.sp)
                                        }

                                        IconButton(onClick = { viewModel.removeRecentSearchItem(item.query) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is SearchUiState.Searching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PlayGreenLight)
                    }
                }
                is SearchUiState.Results -> {
                    if (state.apps.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "No Results",
                                tint = Color.LightGray,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "No apps found matching '$searchInput'", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(state.apps) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAppClick(app.appId) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.LightGray.copy(alpha = 0.2f))
                                    ) {
                                        AsyncImage(
                                            model = app.appIcon,
                                            contentDescription = app.appName,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = app.appName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(text = "${app.developerName} • ${app.category}", fontSize = 12.sp, color = Color.Gray)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = app.rating.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(11.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = app.downloads, fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is SearchUiState.Error -> {
                    ErrorStateView(message = state.message) {
                        viewModel.performSearch(searchInput)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// WISHLIST / SIMULATED LIBRARY SCREEN
// -----------------------------------------------------------------------------
@Composable
fun WishlistScreen(
    viewModel: StoreViewModel,
    onAppClick: (String) -> Unit
) {
    val homeState by viewModel.homeUiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("wishlist_view_screen")
    ) {
        Text(
            text = "My Wishlist & Favorites",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            when (val state = homeState) {
                is HomeUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PlayGreenLight)
                    }
                }
                is HomeUiState.Success -> {
                    // Filter wishlist dynamically or simulated favorites
                    // Prefs based tracking of favorites can also be gathered. Let's show favorites!
                    // Let's list a few recommended / premium items if items are unmapped, or show favorites list
                    // Since favorites are kept internally, we filter our Success apps
                    // We can observe favorites dynamically
                    val sharedPrefs = context.getSharedPreferences("aria_store_prefs", Context.MODE_PRIVATE)
                    val favSetByPrefs = sharedPrefs.getStringSet("favorites", emptySet()) ?: emptySet()
                    val favoriteAppsList = state.featuredApps.plus(state.topChartApps).plus(state.trendingApps)
                        .distinctBy { it.appId }
                        .filter { favSetByPrefs.contains(it.appId) }

                    if (favoriteAppsList.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(top = 100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FavoriteBorder,
                                contentDescription = "Empty Favorites",
                                tint = Color.LightGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "Your Wishlist is Empty", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Bookmark apps using the favorite icon on dynamic details screens to preview them here.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(favoriteAppsList) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAppClick(app.appId) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.LightGray.copy(alpha = 0.2f))
                                    ) {
                                        AsyncImage(
                                            model = app.appIcon,
                                            contentDescription = app.appName,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = app.appName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(text = "${app.developerName} • ${app.category}", fontSize = 12.sp, color = Color.Gray)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = app.rating.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(11.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = app.size, fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                    IconButton(onClick = {
                                        viewModel.toggleWishlist(app.appId)
                                    }) {
                                        Icon(imageVector = Icons.Default.Favorite, tint = Color.Red, contentDescription = "Unlike")
                                    }
                                }
                            }
                        }
                    }
                }
                is HomeUiState.Error -> {
                    ErrorStateView(message = state.message) {
                        viewModel.loadHomeData()
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// USER SETTINGS & FIRBASE AUTH SECTION SCREEN
// -----------------------------------------------------------------------------
@Composable
fun AccountScreen(
    viewModel: StoreViewModel,
    onAdminClick: () -> Unit
) {
    val userState by viewModel.userState.collectAsState()
    val context = LocalContext.current
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var authErrorMsg by remember { mutableStateOf<String?>(null) }
    var showAuthDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("account_screen")
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Upper Profile Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "User avatar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (userState.isLoggedIn) {
                    Text(
                        text = userState.displayName ?: "Aria Explorer",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (userState.isGuest) "Guest Account partner" else (userState.email ?: ""),
                        color = Color.Gray,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.signOut() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Sign Out", color = Color.White)
                    }
                } else {
                    Text(
                        text = "Sign in to Aria Store",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sync downloads, favorited games & app specs dynamically",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.signInGuest(
                                    onSuccess = {
                                        android.widget.Toast.makeText(context, "Signed in as Guest Partner!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err -> authErrorMsg = err }
                                )
                            }
                        ) {
                            Text("Guest Mode")
                        }

                        Button(
                            onClick = { showAuthDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PlayGreenLight)
                        ) {
                            Text("Sign In with Email", color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions and Configuration parameters
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Admin & Developer Options", fontSize = 16.sp, fontWeight = FontWeight.Bold)

            Button(
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://wajidtechtube.free.nf")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_panel_trigger"),
                colors = ButtonDefaults.buttonColors(containerColor = PlayGreenLight)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Developer Console (Upload Apps)", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            if (userState.isLoggedIn) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "My Uploaded Apps", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                
                // Simulated Uploaded Apps
                val homeState = viewModel.homeUiState.value
                val uploadedApps = if (homeState is com.example.ui.viewmodel.HomeUiState.Success) {
                    homeState.trendingApps.take(2)
                } else {
                    emptyList()
                }

                if (uploadedApps.isEmpty()) {
                    Text(text = "No apps uploaded yet.", fontSize = 14.sp, color = Color.Gray)
                } else {
                    uploadedApps.forEach { app ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Menu, contentDescription = null, tint = PlayGreenLight)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(app.appName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Text(text = "Downloads: ${app.downloads}  •  Reviews: ${(100..400).random()}", fontSize = 12.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://wajidtechtube.free.nf")
                                            )
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Edit App", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "App version", fontSize = 13.sp, color = Color.Gray)
            Text(text = "Aria Store Engine - v1.0.0-PRO (Build 2026-05)", fontSize = 12.sp, color = Color.Gray)
        }

        // Firebase Auth Input Form Dialog
        if (showAuthDialog) {
            Dialog(onDismissRequest = { showAuthDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "Sign In / Register Account", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Enter any email to sign in. Registered emails are authenticated dynamically with Firebase Auth.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email identifier") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Secret Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )

                        if (authErrorMsg != null) {
                            Text(text = authErrorMsg!!, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAuthDialog = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Discard")
                            }

                            Button(
                                onClick = {
                                    if (emailInput.isNotBlank() && passwordInput.length >= 6) {
                                        viewModel.signInEmail(
                                            email = emailInput.trim(),
                                            password = passwordInput,
                                            onSuccess = {
                                                android.widget.Toast.makeText(context, "Authentication Complete!", android.widget.Toast.LENGTH_SHORT).show()
                                                showAuthDialog = false
                                                authErrorMsg = null
                                            },
                                            onError = { err -> authErrorMsg = err }
                                        )
                                    } else {
                                        authErrorMsg = "Enter valid email and 6+ character password."
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PlayGreenLight)
                            ) {
                                Text("Access", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// DEVELOPER / ADMIN PANEL SCREEN (ADMIN READY INTERFACES)
// -----------------------------------------------------------------------------
@Composable
fun AdminPortalScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    var appNameInput by remember { mutableStateOf("") }
    var appIdInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("Action") }
    var developerInput by remember { mutableStateOf("") }
    var descriptionInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_screen_view")
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Developer Admin Console", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Seeding custom applications directly to Firestore Console collection.", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = appNameInput,
            onValueChange = {
                appNameInput = it
                appIdInput = it.lowercase().replace(" ", "_").filter { c -> c.isLetterOrDigit() || c == '_' }
            },
            label = { Text("App/Game Name") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = appIdInput,
            onValueChange = { appIdInput = it },
            label = { Text("Unique application Identifier (Firestore Doc ID)") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = developerInput,
            onValueChange = { developerInput = it },
            label = { Text("Developer/Studio Name") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = descriptionInput,
            onValueChange = { descriptionInput = it },
            label = { Text("Full App Description details") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Submit action Button
        Button(
            onClick = {
                if (appNameInput.isNotBlank() && appIdInput.isNotBlank() && developerInput.isNotBlank()) {
                    isSubmitting = true
                    val mockNewApp = AppInfo(
                        appId = appIdInput.trim(),
                        appName = appNameInput.trim(),
                        appPackage = "com.developer.${appIdInput.trim()}",
                        appIcon = "https://picsum.photos/seed/${appIdInput.trim()}/200/200",
                        appBanner = "https://picsum.photos/seed/${appIdInput.trim()}_banner/600/300",
                        shortDescription = "Developer submitted game profiles.",
                        fullDescription = descriptionInput.trim(),
                        category = categoryInput,
                        rating = (40..50).random().toDouble() / 10.0,
                        downloads = "10K+",
                        size = "${(20..150).random()} MB",
                        developerName = developerInput.trim(),
                        updatedDate = "May 2026",
                        featured = true,
                        topChart = false,
                        trending = true,
                        tags = listOf(categoryInput, "Indie", "New"),
                        minimumAndroid = "8.0+"
                    )

                    viewModel.addNewAppToFirebase(mockNewApp) { success ->
                        isSubmitting = false
                        if (success) {
                            android.widget.Toast.makeText(context, "$appNameInput seeded successfully to Firebase!", android.widget.Toast.LENGTH_LONG).show()
                            onBack()
                        } else {
                            android.widget.Toast.makeText(context, "Firestore seed request failed (check internet status).", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    android.widget.Toast.makeText(context, "Fill in all mandatory parameters.", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("admin_submit_button"),
            colors = ButtonDefaults.buttonColors(containerColor = PlayGreenLight),
            enabled = !isSubmitting
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Seed App docs to Firebase", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SECURE ERROR VIEW RESILIENT RECONNECT BUTTON
// -----------------------------------------------------------------------------
@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Syncing Error icon",
            tint = Color.Red.copy(alpha = 0.8f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Content Loading Error", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = PlayGreenLight)
        ) {
            Text("Retry sync connection", color = Color.White)
        }
    }
}

// -----------------------------------------------------------------------------
// SLEEK INTERFACE THEME DECORATIVE TOP COMPONENTS
// -----------------------------------------------------------------------------
@Composable
fun SleekSearchHeader(
    onSearchClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(26.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp))
            .clickable(onClick = onSearchClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search icon",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Search apps & games",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        
        // JD Initial Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable { onAvatarClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "JD",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SleekSecondaryTabs() {
    var selectedTab by remember { mutableStateOf("Games") }
    val tabs = listOf("Games", "Apps", "Offers", "Books")

    Column {
        TabRow(
            selectedTabIndex = tabs.indexOf(selectedTab),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                val currentTab = tabPositions[tabs.indexOf(selectedTab)]
                Box(
                    modifier = Modifier
                        .tabIndicatorOffset(currentTab)
                        .height(3.dp)
                        .padding(horizontal = 48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                        )
                )
            },
            divider = {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                )
            }
        }
    }
}
