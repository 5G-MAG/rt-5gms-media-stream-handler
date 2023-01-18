package com.example.a5gms_mediastreamhandler.network

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface ServiceAccessInformationApi {

    @GET("service-access-information/{provisioningSessionId}")
    fun fetchServiceAccessInformation(@Path("provisioningSessionId") provisioningSessionId: String?): Call<ServiceAccessInformation>?

}