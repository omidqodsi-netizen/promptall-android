package ir.promptall.app.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface AiImageApi {
    @POST("wp-json/promptall-ai/v1/app/bootstrap")
    suspend fun bootstrap(@Body request: AppBootstrapRequest): AppBootstrapResponse

    @GET("wp-json/promptall-ai/v1/app/config")
    suspend fun config(): AiAppConfig

    @POST("wp-json/promptall-ai/v1/app/purchase/prepare")
    suspend fun preparePurchase(
        @Header("X-PromptAll-App-Token") token: String,
        @Body request: PreparePurchaseRequest,
    ): PreparePurchaseResponse

    @POST("wp-json/promptall-ai/v1/app/purchase/verify")
    suspend fun verifyPurchase(
        @Header("X-PromptAll-App-Token") token: String,
        @Body request: VerifyPurchaseRequest,
    ): BasicSuccessResponse

    @POST("wp-json/promptall-ai/v1/app/purchase/consumed")
    suspend fun confirmConsumed(
        @Header("X-PromptAll-App-Token") token: String,
        @Body request: ConsumedPurchaseRequest,
    ): BasicSuccessResponse

    @Multipart
    @POST("wp-json/promptall-ai/v1/app/generate")
    suspend fun generate(
        @Header("X-PromptAll-App-Token") token: String,
        @Part("post_id") postId: RequestBody,
        @Part("purchase_id") purchaseId: RequestBody,
        @Part referenceImage: MultipartBody.Part,
    ): GenerateImageResponse

    @GET("wp-json/promptall-ai/v1/app/history")
    suspend fun history(
        @Header("X-PromptAll-App-Token") token: String,
    ): AiHistoryResponse

    @GET("wp-json/promptall-ai/v1/app/profile")
    suspend fun profile(
        @Header("X-PromptAll-App-Token") token: String,
    ): AiProfileResponse
}

data class AppBootstrapRequest(
    @SerializedName("installation_id") val installationId: String,
    @SerializedName("install_secret") val installSecret: String,
)

data class AppBootstrapResponse(
    val success: Boolean,
    val token: String,
    val profile: AiProfile,
)

data class AiAppConfig(
    val enabled: Boolean = false,
    val bazaarEnabled: Boolean = false,
    val packageName: String = "ir.promptall.app",
    val productId: String = "ai_image_single",
    val rsaPublicKey: String = "",
    val maxUploadMb: Int = 8,
    val profileTitle: String = "تصاویر ساخته‌شده",
    val paymentNotice: String = "پرداخت امن از طریق کافه‌بازار انجام می‌شود.",
)

data class AiProfile(
    val id: Long = 0,
    val name: String = "کاربر PromptAll",
    val generatedCount: Int = 0,
)

data class PreparePurchaseRequest(
    @SerializedName("post_id") val postId: Long,
)

data class PreparePurchaseResponse(
    val success: Boolean,
    val purchaseId: Long,
    val productId: String,
    val payload: String,
)

data class VerifyPurchaseRequest(
    @SerializedName("purchase_id") val purchaseId: Long,
    @SerializedName("purchase_token") val purchaseToken: String,
    @SerializedName("order_id") val orderId: String,
    @SerializedName("developer_payload") val developerPayload: String,
)

data class ConsumedPurchaseRequest(
    @SerializedName("purchase_id") val purchaseId: Long,
    @SerializedName("purchase_token") val purchaseToken: String,
)

data class BasicSuccessResponse(
    val success: Boolean = false,
    val verified: Boolean = false,
    val consumed: Boolean = false,
    val needsConsume: Boolean = false,
)

data class GenerateImageResponse(
    val success: Boolean,
    val generationId: Long,
    val imageUrl: String,
    val ratio: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val profile: AiProfile? = null,
)

data class AiHistoryItem(
    val id: Long,
    val postId: Long,
    val title: String,
    val promptImage: String = "",
    val imageUrl: String,
    val status: String,
    val createdAt: String,
    val width: Int = 0,
    val height: Int = 0,
)

data class AiHistoryResponse(
    val success: Boolean,
    val items: List<AiHistoryItem> = emptyList(),
    val profile: AiProfile,
)

data class AiProfileResponse(
    val success: Boolean,
    val profile: AiProfile,
    val savedReference: String? = null,
)
