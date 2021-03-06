package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.http.Response;
import com.appdynamics.extensions.http.SimpleHttpClient;
import com.appdynamics.extensions.http.UrlBuilder;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Created by abey.tom on 4/1/15.
 */
public class TcpSocketDataFetcher implements DataFetcher {
    public static final Logger logger = LoggerFactory.getLogger(TcpSocketDataFetcher.class);
    protected static final ObjectMapper objectMapper = new ObjectMapper();

    private final SimpleHttpClient client;
    private final Map<String, String> argsMap;


    public TcpSocketDataFetcher(SimpleHttpClient client, Map<String, String> argsMap) {
        this.client = client;
        this.argsMap = argsMap;
    }

    public <T> T fetchData(String resourcePath, Class<T> clazz, boolean readFully) {
        String path = UrlBuilder.builder(argsMap).path(resourcePath).build();
        if (readFully) {
            return getJsonFull(path, clazz);
        } else {
            return getJsonPartial(path, clazz);
        }
    }

    protected <T> T getJsonFull(String url, Class<T> clazz) {
        Response response = client.target(url).get();
        if (response.getStatus() == 200) {
            T json = response.json(clazz);
            if (logger.isDebugEnabled()) {
                logger.debug("The url {} responded with a json {}", url, json);
            }
            return json;
        } else {
            return onHttpError(url, response);
        }
    }

    private <T> T onHttpError(String url, Response response) {
        String error = response.string();
        logger.error("The url {} returned a status code of {} with data {}", url, response.getStatus(), error);
        return null;
    }

    private <T> T getJsonPartial(String url, Class<T> clazz) {
        Response response = client.target(url).get();
        if(response.getStatus() == 200){
            try {
                InputStream in = response.inputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                int count = 0;
                while (++count <= 3) {
                    String jsonStr = br.readLine();
                    logger.debug("The url {} returned the streaming data {}", url, jsonStr);
                    try {
                        return objectMapper.readValue(jsonStr, clazz);
                    } catch (Exception e) {
                        logger.error("Error while reading {}, response is {}", url, jsonStr);
                        logger.error("", e);
                    }
                }
            } catch (IOException e) {
                logger.error("Error while reading " + url, e);
            } finally {
                response.abort();
            }
        } else{
            return onHttpError(url, response);
        }
        return null;
    }
}
