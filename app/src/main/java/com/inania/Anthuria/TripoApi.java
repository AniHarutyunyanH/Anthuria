package com.inania.Anthuria;

import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface TripoApi {
    @Headers({
            "Content-Type: application/json",
            "Accept: application/json"
    })
    @POST("v2/openapi/task")
        // Using Map<String, Object> ensures a clean JSON structure
    Call<ResponseBody> createTask(
            @Header("Authorization") String authHeader,
            @Body Map<String, Object> body
    );

    @GET("v2/openapi/task/{task_id}")
    Call<ResponseBody> getTaskStatus(
            @Header("Authorization") String authHeader,
            @Path("task_id") String taskId
    );
}