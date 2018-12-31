package com.example.dell.rxjavaapp.network;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.example.dell.rxjavaapp.app.Const;
import com.example.dell.rxjavaapp.utils.PrefUtils;
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static Retrofit retrofit = null;
    private static OkHttpClient okHttpClient;

    public static Retrofit getClient(Context context) {
        if (okHttpClient == null)
            initOkHttp(context);
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(Const.BASE_URL)
                    .client(okHttpClient)
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    private static void initOkHttp(final Context context) {
        int REQUEST_TIMEOUT = 60;
        OkHttpClient.Builder httpClient = new OkHttpClient().newBuilder()
                .connectTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS);
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        httpClient.addInterceptor(interceptor);
        httpClient.addInterceptor(new Interceptor() {
            @NonNull
            @Override
            public Response intercept(@NonNull Chain chain) throws IOException {
                Request original = chain.request();
                Request.Builder requestBuilder = original.newBuilder()
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/json");
                if (!TextUtils.isEmpty(PrefUtils.getApiKey(context))) {
                    requestBuilder.addHeader("Authorization", PrefUtils.getApiKey(context));
                }
                Request request = requestBuilder.build();
                return chain.proceed(request);
            }
        });
        okHttpClient = httpClient.build();
    }
}
