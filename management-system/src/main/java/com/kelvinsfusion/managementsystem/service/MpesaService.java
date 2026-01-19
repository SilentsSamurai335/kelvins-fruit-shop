package com.kelvinsfusion.managementsystem.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

@Service
public class MpesaService {

    @Value("${mpesa.consumer.key}") private String consumerKey;
    @Value("${mpesa.consumer.secret}") private String consumerSecret;
    @Value("${mpesa.shortcode}") private String shortCode;
    @Value("${mpesa.passkey}") private String passkey;
    @Value("${mpesa.stkpush.url}") private String stkPushUrl;
    @Value("${mpesa.auth.url}") private String authUrl;

    private final OkHttpClient client = new OkHttpClient();

    public String getAccessToken() throws IOException {
        String credentials = consumerKey + ":" + consumerSecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        Request request = new Request.Builder().url(authUrl).get()
                .addHeader("Authorization", "Basic " + encodedCredentials).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            return JsonParser.parseString(response.body().string()).getAsJsonObject().get("access_token").getAsString();
        }
    }

    public String initiateStkPush(String phoneNumber, Double amount) {
        try {
            String token = getAccessToken();
            if (token == null) return "Error: Could not get Access Token";

            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String password = Base64.getEncoder().encodeToString((shortCode + passkey + timestamp).getBytes());

            JsonObject json = new JsonObject();
            json.addProperty("BusinessShortCode", shortCode);
            json.addProperty("Password", password);
            json.addProperty("Timestamp", timestamp);
            json.addProperty("TransactionType", "CustomerPayBillOnline");
            json.addProperty("Amount", amount.intValue());
            json.addProperty("PartyA", phoneNumber);
            json.addProperty("PartyB", shortCode);
            json.addProperty("PhoneNumber", phoneNumber);
            json.addProperty("CallBackURL", "https://mydomain.com/callback"); // Placeholder
            json.addProperty("AccountReference", "FruityFusion");
            json.addProperty("TransactionDesc", "Payment");

            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder().url(stkPushUrl).post(body)
                    .addHeader("Authorization", "Bearer " + token).build();

            try (Response response = client.newCall(request).execute()) {
                return response.body().string();
            }
        } catch (Exception e) { e.printStackTrace(); return null; }
    }
}