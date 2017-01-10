package io.logz.apollo.clients;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.logz.apollo.configuration.ApolloConfiguration;
import io.logz.apollo.exceptions.ApolloClientException;
import io.logz.apollo.exceptions.ApolloCouldNotLoginException;
import io.logz.apollo.helpers.Common;
import io.logz.apollo.helpers.RestResponse;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/**
 * Created by roiravhon on 11/24/16.
 */
public class GenericApolloClient {

    private static final Logger logger = LoggerFactory.getLogger(GenericApolloClient.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String userName;
    private final String plainPassword;
    private final ApolloConfiguration apolloConfiguration;
    private final ObjectMapper mapper;
    private String token;

    GenericApolloClient(String userName, String plainPassword, ApolloConfiguration apolloConfiguration) {

        client = new OkHttpClient();
        this.userName = userName;
        this.plainPassword = plainPassword;
        this.apolloConfiguration = apolloConfiguration;
        this.mapper = new ObjectMapper();
    }

    void login() throws ApolloCouldNotLoginException {

        try {
            RestResponse response = post("/_login", generateLoginJson());

            if (!isLoginSuccessful(response)) {
                throw new ApolloCouldNotLoginException("Could not login user " + userName + " with password " + plainPassword);
            }

            token = getTokenFromResponse(response);

        } catch (IOException | ApolloClientException e) {
            throw new ApolloCouldNotLoginException("Could not login user due to unexpected error!", e);
        }
    }

    RestResponse get(String url) throws IOException {
        Request request = new Request.Builder().url(getFullUrlWithToken(url)).build();
        Response response = client.newCall(request).execute();
        return new RestResponse(response.code(), response.body().string());
    }

    RestResponse post(String url, String json) throws IOException {
        RequestBody requestBody = RequestBody.create(JSON, json);
        Request request = new Request.Builder().url(getFullUrlWithToken(url)).post(requestBody).build();
        Response response = client.newCall(request).execute();
        return new RestResponse(response.code(), response.body().string());
    }

    <T> T getResult(String url, TypeReference<T> responseType) throws ApolloClientException {
        return runAndGetResult(url, Optional.empty(), responseType);
    }

    <T> T postAndGetResult(String url, String body, TypeReference<T> responseType) throws ApolloClientException {
        return runAndGetResult(url, Optional.of(body), responseType);
    }

    private <T> T runAndGetResult(String url, Optional<String> body, TypeReference<T> responseType) throws ApolloClientException {
        try {
            RestResponse restResponse;

            if (body.isPresent()) {
                restResponse = post(url, body.get());
                if (restResponse.getCode() != 201) {
                    throw new ApolloClientException("Got HTTP return code " + restResponse.getCode() + " with text: " + restResponse.getBody());
                }
            } else {
                restResponse = get(url);
                if (restResponse.getCode() != 200) {
                    throw new ApolloClientException("Got HTTP return code " + restResponse.getCode() + " with text: " + restResponse.getBody());
                }
            }
            return mapper.readValue(restResponse.getBody(), responseType);

        } catch (IOException e) {
            throw new ApolloClientException("Could not reach Apollo API!", e);
        }
    }

    private String getFullUrlWithToken(String url) {
        return "http://localhost:" + apolloConfiguration.getApiPort() + url + "?_token=" + token;
    }

    private String generateLoginJson() {
        return Common.generateJson("username", userName, "password", plainPassword);
    }

    private String getTokenFromResponse(RestResponse restResponse) throws ApolloClientException {
        try {
            JsonObject jsonObject = new JsonParser().parse(restResponse.getBody()).getAsJsonObject();
            return jsonObject.get("token").getAsString();
        } catch (Exception e) {
            throw new ApolloClientException("Could not get token from response!", e);
        }
    }

    private boolean isLoginSuccessful(RestResponse restResponse) throws ApolloClientException {
        try {
            JsonObject jsonObject = new JsonParser().parse(restResponse.getBody()).getAsJsonObject();
            return jsonObject.get("success").getAsBoolean();
        } catch (Exception e) {
            throw new ApolloClientException("Could not determine if login succeeded", e);
        }
    }
}