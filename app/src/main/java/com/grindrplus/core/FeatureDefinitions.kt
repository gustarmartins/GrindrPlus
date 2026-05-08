package com.grindrplus.core

import com.grindrplus.manager.settings.FeatureState

data class FeatureDef(
    val name: String,
    val assignmentKey: String,
    val defaultState: FeatureState,
    val description: String,
    val category: String
)

object FeatureDefinitions {
    val ALL: List<FeatureDef> = listOf(
        FeatureDef("AdBackfill", "ad-backfill", FeatureState.OFF, "Ad backfill", "Ads"),
        FeatureDef("AdIdentifierFeatureFlag", "ad-identifier", FeatureState.OFF, "Ad identifier tracking", "Ads"),
        FeatureDef("AdLatency", "ad-latency-measurement", FeatureState.OFF, "Ad latency measurement", "Ads"),
        FeatureDef("AdsInboxSecondTpaOffsetFeatureFlag", "ads_inbox_second_tpa_offset_ff", FeatureState.OFF, "Inbox 2nd TPA offset", "Ads"),
        FeatureDef("AppLovinInitializationKillSwitch", "ads-applovin-initialization-kill-switch", FeatureState.ON, "Kill AppLovin initialization", "Ads"),
        FeatureDef("BannerChatroomTopFeatureFlag", "banner-chatroom-top", FeatureState.OFF, "Banner ad at chatroom top", "Ads"),
        FeatureDef("BottomBannerInboxFeatureFlag", "bottom-banner-inbox", FeatureState.OFF, "Bottom banner in inbox", "Ads"),
        FeatureDef("BreadcrumbLogFlag", "ads-logs", FeatureState.OFF, "Ads logging/breadcrumbs", "Ads"),
        FeatureDef("CmpInitializationForBanUserKillSwitch", "ads-cmp-init-ban-users", FeatureState.OFF, "CMP init ban users", "Ads"),
        FeatureDef("CmpRetrySystemKillSwitch", "ads-cmp-retry-system", FeatureState.OFF, "CMP retry system", "Ads"),
        FeatureDef("DnsBasedAdBlockerDetector", "ads-dns-based-ad-blocker-detector", FeatureState.OFF, "DNS ad blocker detector", "Ads"),
        FeatureDef("DoubleVerifyFeatureFlag", "double-verify-enabled", FeatureState.OFF, "DoubleVerify ad verification", "Ads"),
        FeatureDef("DoubleVerifyUseVastPropertyInterstitial", "double-verify-use-vast-property-interstitial", FeatureState.OFF, "DoubleVerify VAST interstitial", "Ads"),
        FeatureDef("FirstCascadeMrecFeatureFlag", "mrec-cascade-first", FeatureState.OFF, "MREC ad in cascade (1st)", "Ads"),
        FeatureDef("ForceApplovinOptOut", "force-applovin-opt-out", FeatureState.ON, "Force AppLovin opt-out", "Ads"),
        FeatureDef("HideBannerOnMicroSession", "hide-persistent-banner-on-micro-session", FeatureState.ON, "Hide banner on micro session", "Ads"),
        FeatureDef("InterstitialConfig", "ad-interstitial-config", FeatureState.OFF, "Ad interstitial config", "Ads"),
        FeatureDef("KetchPopupLogicKillSwitch", "ketch-popup-logic-gate", FeatureState.OFF, "Ketch consent popup gate", "Ads"),
        FeatureDef("LiftoffShareSignalFeatureFlag", "liftoff-signal-sharing-phase0", FeatureState.OFF, "Liftoff signal sharing", "Ads"),
        FeatureDef("LtoGamPriorityFeatureFlag", "ads-lto-gam-priority", FeatureState.OFF, "LTO GAM priority", "Ads"),
        FeatureDef("NewApplovinPrivacyFlags", "ads-new-applovin-privacy-flags-enabled", FeatureState.OFF, "AppLovin privacy flags", "Ads"),
        FeatureDef("NonChatEnvironmentAdBannerFeatureFlag", "banner-non-chat-environment", FeatureState.OFF, "Non-chat ad banners", "Ads"),
        FeatureDef("ObserveFeatureUpdates", "ads-observe-features-updates", FeatureState.OFF, "Ads observe feature updates", "Ads"),
        FeatureDef("OpenMeasurementExtraAnalyticEvents", "om-extra-analytic-events", FeatureState.OFF, "OM extra analytics", "Ads"),
        FeatureDef("QualityEdu", "ads-quality-edu", FeatureState.OFF, "Ads quality education", "Ads"),
        FeatureDef("RewardedAdsMpuConfig", "rewarded-ads-mpu-config", FeatureState.OFF, "Rewarded ads MPU config", "Ads"),
        FeatureDef("SecondCascadeMrecFeatureFlag", "mrec-cascade-second", FeatureState.OFF, "MREC ad in cascade (2nd)", "Ads"),
        FeatureDef("ThirdCascadeMrecFeatureFlag", "mrec-cascade-third", FeatureState.OFF, "MREC ad in cascade (3rd)", "Ads"),
        FeatureDef("TpaNativeInboxAdsFirst", "tpa-native-inbox-ads-first", FeatureState.OFF, "TPA native inbox ads", "Ads"),
        FeatureDef("VerboseAdAnalyticsFeatureFlag", "verbose-ad-analytics", FeatureState.OFF, "Verbose ad analytics", "Ads"),
        FeatureDef("VerveConsentUpdateFeatureFlag", "activate-verve-consent-update-android", FeatureState.OFF, "Verve consent update", "Ads"),
        FeatureDef("MetaSdkFeatureFlag", "activate-meta-consent-update", FeatureState.OFF, "Meta consent SDK", "Ads"),
        FeatureDef("FixAdsPeriodicalRefresher", "fix-ads-periodical-refresher", FeatureState.OFF, "Fix ads periodical refresher", "Ads"),
        // InterstitialUpsellFeatureFlag key

        FeatureDef("AnalyticsBatch", "android-analytics-batch", FeatureState.OFF, "Analytics batching", "Telemetry"),
        FeatureDef("CustomAppsFlyerEventLogging", "appsflyer-event-logging", FeatureState.OFF, "AppsFlyer event logging", "Telemetry"),
        FeatureDef("SiftKillSwitch", "sift-kill-switch", FeatureState.ON, "Kill Sift fraud detection", "Telemetry"),

        FeatureDef("BlockScreenshot", "disable-screenshot-for-photos", FeatureState.OFF, "Screenshot block for photos", "Safety"),
        FeatureDef("ApproximateDistanceFeatureFlag", "approximate-distance", FeatureState.OFF, "Approximate distance (hide precise)", "Safety"),
        FeatureDef("PasswordComplexity", "sk-pipa-password", FeatureState.OFF, "Password complexity enforcement", "Safety"),

        FeatureDef("GenderFlag", "gender-filter", FeatureState.ON, "Gender filter", "UI"),
        FeatureDef("CalendarUi", "calendar-ui", FeatureState.ON, "Calendar UI", "UI"),
        FeatureDef("ComposeRework", "albums-compose-rework", FeatureState.ON, "Albums Compose rework", "UI"),
        FeatureDef("ProfileClearUnreadCount", "cascade-profile-clear-unread-count", FeatureState.ON, "Clear unread on profile view", "UI"),
        FeatureDef("ShowNoNetworkAvailable", "show-no-network-available-banner", FeatureState.OFF, "Show no-network banner", "UI"),
        FeatureDef("TakeMeHome", "takemehome-button", FeatureState.ON, "Take Me Home button", "UI"),
        FeatureDef("UseOnlineUntilUpdates", "online-until-updates", FeatureState.ON, "Online-until status updates", "UI"),
        FeatureDef("DiscreetNotificationSound", "discreet_notification_sound", FeatureState.ON, "Discreet notification sound", "UI"),
        FeatureDef("WingmanSummaries", "enable-chat-summaries", FeatureState.ON, "AI chat summaries (Wingman)", "UI"),
        FeatureDef("MessageUiV2", "chat-ui-rewrite-android", FeatureState.ON, "Chat UI rewrite", "UI"),
        FeatureDef("DiscoverProfileButtonsV2", "enable-profile-action-buttons-v2", FeatureState.ON, "Profile action buttons V2", "UI"),

        FeatureDef("VipBadgeFlag", "vip-badge-enabled", FeatureState.ON, "VIP badge", "Premium"),
        FeatureDef("VipMatchmakerConfig", "vip-matchmaker-config", FeatureState.ON, "VIP Matchmaker", "Premium"),
        FeatureDef("SettingsInsightsView", "settings-insights-view", FeatureState.ON, "Settings insights view", "Premium"),
        FeatureDef("BoostEdgeFlag", "edge-boost", FeatureState.ON, "Edge boost", "Premium"),
        FeatureDef("EdgeTier", "edge_tier", FeatureState.ON, "Edge tier access", "Premium"),
        FeatureDef("WebPageMerchandising", "store-page-grindr-web-merchandising", FeatureState.OFF, "Store web merchandising", "Premium"),
        FeatureDef("V1ShopMigration", "v1-shop-migration", FeatureState.OFF, "V1 shop migration", "Premium"),
        FeatureDef("RemoveHardcodedBoostIds", "remove-hardcoded-boost-product-ids", FeatureState.ON, "Remove hardcoded boost IDs", "Premium"),
        FeatureDef("RoleBasedSubscriptionPurchase", "remove-hardcoded-subs-product-ids-2026-01", FeatureState.ON, "Remove hardcoded sub IDs", "Premium"),
        FeatureDef("UseConsumablesCacheApproach", "use-consumables-cache-approach", FeatureState.ON, "Consumables cache approach", "Premium"),

        FeatureDef("DiscoverV4Api", "discover-v4-api", FeatureState.ON, "Discover V4 API", "Discover"),
        FeatureDef("DiscoverLoadingFtuxV2", "enable-discover-loading-ftux-v2", FeatureState.ON, "Discover loading FTUX V2", "Discover"),
        FeatureDef("DiscoverPreferencesModal", "enable-discover-preferences-modal", FeatureState.ON, "Discover preferences modal", "Discover"),
        FeatureDef("DiscoverQuickReplyEnabled", "android-discover-quick-reply-enabled", FeatureState.ON, "Discover quick reply", "Discover"),

        FeatureDef("RightNowMap", "right-now-map", FeatureState.ON, "Right Now map", "Right Now"),
        FeatureDef("RightNowDiscreet", "right-now-discreet", FeatureState.ON, "Right Now discreet mode", "Right Now"),
        FeatureDef("ExplicitImages", "right-now-explicit-images", FeatureState.ON, "Right Now explicit images", "Right Now"),
        FeatureDef("IntentionalActivation", "right-now-intentional-activation-20240910", FeatureState.ON, "Right Now intentional activation", "Right Now"),
        FeatureDef("LiveViewerCount", "right-now-live-viewer-count", FeatureState.ON, "Right Now live viewer count", "Right Now"),
        FeatureDef("RightNowModeration", "right-now-moderation", FeatureState.OFF, "Right Now moderation", "Right Now"),
        FeatureDef("NameChangeIntent", "right-now-name-change-intent", FeatureState.ON, "Right Now name change intent", "Right Now"),
        FeatureDef("PositionFilter", "right-now-position-filter", FeatureState.ON, "Right Now position filter", "Right Now"),
        FeatureDef("PostCompletionV1", "right-now-post-completion-v1", FeatureState.ON, "Right Now post completion", "Right Now"),
        FeatureDef("RightNowPhotoMerge", "right-now-merged-photos", FeatureState.ON, "Right Now merged photos", "Right Now"),
        FeatureDef("RightNowSessionManagerSheet", "right-now-session-manager-sheet", FeatureState.ON, "Right Now session manager", "Right Now"),
        FeatureDef("FloatingBarCascade", "disable-right-now-floating-bar-cascade", FeatureState.OFF, "Disable floating bar on cascade", "Right Now"),

        FeatureDef("BoostSomewhereElseExploreModeBoostFabFlag", "boost-somewhere-else-exploremode-boost-fab-on-cascade", FeatureState.ON, "Boost FAB in explore cascade", "Boost"),
        FeatureDef("BoostSomewhereElseLocationNewTag", "boost-somewhere-else-location-new", FeatureState.ON, "Boost somewhere else (new)", "Boost"),

        FeatureDef("BanterFeatureGate", "relationships-banter", FeatureState.ON, "Relationships banter", "Social"),
        FeatureDef("ChatSuggestionsConsent", "chat-suggestions-opt-out", FeatureState.ON, "Turn ON to OPT OUT of chat suggestions (ON = no suggestions)", "Social"),
        FeatureDef("CrescendoChat", "crescendo-chat-bot", FeatureState.OFF, "Crescendo AI chat bot", "Social"),
        FeatureDef("SpankBankShareToFreshFeatureFlag", "spank-bank-share-to-fresh", FeatureState.ON, "Album share to Fresh", "Social"),
        FeatureDef("UniversalLinkSharingEnabled", "content-hub-universal-sharing-enabled-android", FeatureState.ON, "Content hub universal sharing", "Social"),

        FeatureDef("TravelStoreIsVisiting", "travel-store-isvisiting", FeatureState.ON, "Travel visiting indicator", "Travel"),

        FeatureDef("DatabaseRework", "chat-db-rework-android", FeatureState.ON, "Chat DB rework", "Chat"),
        FeatureDef("MessageDoubleSendFixKillswitch", "android-chat-double-send-fix-killswitch", FeatureState.ON, "Chat double-send fix", "Chat"),
        FeatureDef("InboxWebsocketOnlyRefresh", "inbox-websocket-only-refresh", FeatureState.ON, "Inbox WebSocket-only refresh", "Chat"),

        FeatureDef("NeverPayerTrialPaywall", "never-payer-trial-paywall-ff", FeatureState.OFF, "Never-payer trial paywall", "Restrictions"),

        FeatureDef("HealthCenterConfig", "health-center-config", FeatureState.ON, "Health center config", "Health"),

        FeatureDef("LegalAgreementV3", "legal-agreements-v3", FeatureState.ON, "Legal agreements V3", "Legal"),
        FeatureDef("SKPersonalInfoProtectionFlag", "sk-privacy-policy-20230130", FeatureState.OFF, "SK privacy policy", "Legal"),

        FeatureDef("GrindrPlus", "grindr-plus-enabled", FeatureState.ON, "GrindrPlus subscription detection flag. This is not the mod detection.", "Infra"),
        FeatureDef("DisableReconnectLoopFix", "disable-android-websocket-reconnect-loop-fix", FeatureState.OFF, "Disable WS reconnect fix", "Infra"),
        FeatureDef("LocationPermissionKillSwitch", "location-permission-kill-switch", FeatureState.OFF, "Location permission kill switch", "Infra"),
        FeatureDef("PlayServicesAvailabilityCheckKillSwitch", "play-services-check-killswitch", FeatureState.OFF, "Play Services check kill switch", "Infra"),
        FeatureDef("ThirdPartyAccountCreationFixKillSwitch", "third-party-account-creation-fix-kill-switch", FeatureState.OFF, "3rd-party account fix", "Infra"),

        // ── Content Hub (shared toString with Right Now) ────────────────────
        // Note: Two flags share the toString "FeatureGate" but have different keys.
        // The hook can only match on toString, so both will get the same value.
        // content-hub is useful so we enable it; right-now-statsig is also useful.
        //  both resolve to "FeatureGate"

        FeatureDef("FeatureGate", "content-hub", FeatureState.ON, "Content Hub and Right Now Statsig gate", "Social"),
    )

    const val CONFIG_PREFIX = "feature_flag_"

    fun configKey(featureName: String): String = "$CONFIG_PREFIX$featureName"

    val CATEGORIES: List<String> by lazy {
        ALL.map { it.category }.distinct().sorted()
    }
}
