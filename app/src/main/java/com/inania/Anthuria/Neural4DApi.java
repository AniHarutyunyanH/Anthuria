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

public interface Neural4DApi {

    // Эндпоинт для создания задачи генерации (Text-to-3D или Image-to-3D)
    @Headers({
            "Content-Type: application/json"
    })
    @POST("v1/text-to-3d")
    Call<ResponseBody> createTask(
            @Header("Authorization") String bearerToken, // "Bearer msy_..."
            @Body Map<String, Object> body
    );

    // Получение статуса задачи по ID
    @GET("v1/text-to-3d/{task_id}")
    Call<ResponseBody> getTaskStatus(
            @Header("Authorization") String bearerToken,
            @Path("task_id") String taskId
    );
}
