package com.aira.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import com.aira.assistant.models.ChatRequest;
import com.aira.assistant.models.ChatResponse;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

import java.util.concurrent.TimeUnit;

/**
 * Singleton HTTP client using Retrofit + OkHttp.
 * The server base URL is read from res/values/config.xml — NEVER hardcoded here.
 * All communication goes over HTTPS.
 */
public class ApiClient {

    private static final String PREF_NAME = "aira_prefs";
    private static final String KEY_SERVER_URL = "server_base_url";

    private static ApiClient instance;
    private final AiraApiService apiService;

    // Retrofit interface
    public interface AiraApiService {
        @POST("/api/v1/chat")
        Call<ChatResponse> sendChat(@Body ChatRequest request);

        @GET("/api/v1/health_check")
        Call<Object> healthCheck();
    }

    private ApiClient(Context context) {
        String baseUrl = getBaseUrl(context);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)   // Gemini can take time
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(AiraApiService.class);
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context.getApplicationContext());
        }
        return instance;
    }

    public AiraApiService getService() {
        return apiService;
    }

    private String getBaseUrl(Context context) {
        // First check user-saved URL (allows runtime configuration)
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedUrl = prefs.getString(KEY_SERVER_URL, null);
        if (savedUrl != null && !savedUrl.isEmpty()) {
            return savedUrl.endsWith("/") ? savedUrl : savedUrl + "/";
        }
        // Fall back to build-time config (res/values/config.xml)
        String configUrl = context.getString(R.string.server_base_url);
        return configUrl.endsWith("/") ? configUrl : configUrl + "/";
    }

    public static void saveServerUrl(Context context, String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
        instance = null; // Force recreation with new URL
    }
}
