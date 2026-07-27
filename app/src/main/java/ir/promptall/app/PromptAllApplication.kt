package ir.promptall.app

import android.app.Application
import androidx.room.Room
import ir.promptall.app.data.local.PromptAllDatabase
import ir.promptall.app.data.remote.PromptApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PromptAllApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, PromptAllDatabase::class.java, "promptall.db").build()
    }

    val api: PromptApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            })
            .build()
        Retrofit.Builder()
            .baseUrl("https://promptall.ir/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PromptApi::class.java)
    }
}
