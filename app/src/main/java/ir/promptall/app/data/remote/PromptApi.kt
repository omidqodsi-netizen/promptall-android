package ir.promptall.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class PromptImage(
    val url: String,
    val width: Int = 1,
    val height: Int = 1,
)

data class PromptDto(
    val id: Long,
    val title: String,
    @SerializedName("prompt_text") val promptText: String,
    val image: PromptImage,
)

data class PromptPage(
    val items: List<PromptDto>,
    val page: Int,
    @SerializedName("total_pages") val totalPages: Int,
    @SerializedName("has_more") val hasMore: Boolean,
)

data class PromptCategory(
    val id: Long,
    val name: String,
    val slug: String,
    val count: Int,
)

data class PromptCategories(
    val items: List<PromptCategory>,
)


data class ImageSearchStatus(
    val enabled: Boolean = false,
    @SerializedName("daily_limit") val dailyLimit: Int = 3,
    val used: Int = 0,
    val remaining: Int = 0,
    @SerializedName("index_count") val indexCount: Int = 0,
    @SerializedName("queue_count") val queueCount: Int = 0,
    val ready: Boolean = false,
    @SerializedName("rebuild_in_progress") val rebuildInProgress: Boolean = false,
    @SerializedName("rebuild_total") val rebuildTotal: Int = 0,
    @SerializedName("min_app_version") val minAppVersion: Int = 0,
    val algorithm: String = "hybrid-v1",
    @SerializedName("image_uploads_accepted") val imageUploadsAccepted: Boolean = false,
    @SerializedName("ai_fallback_enabled") val aiFallbackEnabled: Boolean = false,
    @SerializedName("ai_client_direct") val aiClientDirect: Boolean = false,
    @SerializedName("ai_client_key") val aiClientKey: String = "",
    @SerializedName("ai_models") val aiModels: List<String> = emptyList(),
    @SerializedName("ai_vpn_note") val aiVpnNote: String = "",
    @SerializedName("ai_retry_message") val aiRetryMessage: String = "",
    @SerializedName("ai_button_label") val aiButtonLabel: String = "بررسی با هوش مصنوعی",
)


data class ImageSearchLabel(
    val text: String,
    val confidence: Double,
)

data class ImageSearchRequest(
    @SerializedName("installation_id") val installationId: String,
    @SerializedName("client_version") val clientVersion: Int,
    val phash: String,
    val dhash: String,
    val ahash: String,
    val hist: List<Int>,
    val labels: List<ImageSearchLabel> = emptyList(),
    @SerializedName("client_type") val clientType: String = "app",
)

data class ImageSearchItem(
    val id: Long,
    val title: String,
    @SerializedName("prompt_text") val promptText: String,
    val image: PromptImage,
    val score: Double = 0.0,
    @SerializedName("similarity_percent") val similarityPercent: Int = 0,
    @SerializedName("match_type") val matchType: String = "similar",
) {
    fun toPrompt() = PromptDto(id = id, title = title, promptText = promptText, image = image)
}

data class ImageSearchResponse(
    val code: String? = null,
    val message: String? = null,
    @SerializedName("daily_limit") val dailyLimit: Int = 3,
    val used: Int = 0,
    val remaining: Int = 0,
    @SerializedName("query_saved") val querySaved: Boolean = false,
    @SerializedName("image_uploaded") val imageUploaded: Boolean = false,
    val algorithm: String = "hybrid-v1",
    val items: List<ImageSearchItem> = emptyList(),
)

data class ImageSearchAiAnalysis(
    @SerializedName("short_description") val shortDescription: String = "",
    val keywords: List<String> = emptyList(),
    @SerializedName("persian_keywords") val persianKeywords: List<String> = emptyList(),
    @SerializedName("english_prompt") val englishPrompt: String = "",
    @SerializedName("persian_prompt") val persianPrompt: String = "",
)

data class ImageSearchAiRequest(
    @SerializedName("installation_id") val installationId: String,
    @SerializedName("client_version") val clientVersion: Int,
    @SerializedName("analysis_payload") val analysisPayload: ImageSearchAiAnalysis,
)

data class ImageSearchGeneratedPrompt(
    val title: String = "پرامپت ساخته‌شده برای این تصویر",
    val persian: String = "",
    val english: String = "",
)

data class ImageSearchAiResponse(
    val mode: String = "prompt",
    val message: String = "",
    val items: List<ImageSearchItem> = emptyList(),
    val analysis: ImageSearchAiAnalysis? = null,
    @SerializedName("generated_prompt") val generatedPrompt: ImageSearchGeneratedPrompt? = null,
    @SerializedName("verification_required") val verificationRequired: Boolean = false,
)

interface PromptApi {
    @GET("wp-json/promptall/v1/prompts")
    suspend fun prompts(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 20,
        @Query("search") search: String? = null,
        @Query("category") category: String? = null,
    ): PromptPage

    @GET("wp-json/promptall/v1/categories")
    suspend fun categories(): PromptCategories

    @GET("wp-json/promptall-search/v1/status")
    suspend fun imageSearchStatus(
        @Query("installation_id") installationId: String,
    ): ImageSearchStatus

    @POST("wp-json/promptall-search/v1/search")
    suspend fun imageSearch(
        @Body request: ImageSearchRequest,
    ): ImageSearchResponse

    @POST("wp-json/promptall-search/v1/ai-fallback")
    suspend fun imageSearchAiFallback(
        @Body request: ImageSearchAiRequest,
    ): ImageSearchAiResponse
}
