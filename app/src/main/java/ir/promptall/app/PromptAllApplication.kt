package ir.promptall.app

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.promptall.app.data.local.PromptAllDatabase
import ir.promptall.app.data.remote.PromptApi
import ir.promptall.app.data.remote.AiImageApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class PromptAllApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, PromptAllDatabase::class.java, "promptall.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
        else HttpLoggingInterceptor.Level.NONE
    }

    private val retrofit by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor())
            .build()
        Retrofit.Builder()
            .baseUrl("https://promptall.ir/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Only the actual generation request gets a long read timeout. Config, status,
    // history and purchase calls keep normal REST timeouts so the UI never hangs for
    // minutes when the server itself is unavailable.
    private val aiImageRetrofit by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.encodedPath.endsWith("/wp-json/promptall-ai/v1/app/generate")) {
                    chain
                        .withConnectTimeout(25, TimeUnit.SECONDS)
                        .withWriteTimeout(90, TimeUnit.SECONDS)
                        .withReadTimeout(360, TimeUnit.SECONDS)
                        .proceed(request)
                } else {
                    chain.proceed(request)
                }
            }
            .addInterceptor(loggingInterceptor())
            .build()
        Retrofit.Builder()
            .baseUrl("https://promptall.ir/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: PromptApi by lazy { retrofit.create(PromptApi::class.java) }
    val aiImageApi: AiImageApi by lazy { aiImageRetrofit.create(AiImageApi::class.java) }

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `prompt_cache` (
                        `id` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `promptText` TEXT NOT NULL,
                        `imageUrl` TEXT NOT NULL,
                        `imageWidth` INTEGER NOT NULL,
                        `imageHeight` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `cachedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
