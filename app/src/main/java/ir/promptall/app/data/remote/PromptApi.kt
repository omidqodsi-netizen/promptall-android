package ir.promptall.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
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
}
