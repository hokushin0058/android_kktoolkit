/* Copyright (C) 2014 KKBOX Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * ​http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kkbox.toolkit.internal.api;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;

import com.kkbox.toolkit.utils.KKDebug;
import com.kkbox.toolkit.utils.StringUtils;
import com.kkbox.toolkit.utils.UserTask;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.net.ssl.SSLException;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public abstract class APIRequest extends UserTask<Object, Void, Void> {

    public enum HttpMethod {
        GET, POST, PUT, DELETE;
    }

    private HttpMethod httpMethod = HttpMethod.GET;
    public final static int DEFAULT_RETRY_LIMIT = 3;
    private APIRequestListener listener;
    private String getParams = "";
    private final String url;
    private static OkHttpClient.Builder okHttpBuilder = new OkHttpClient.Builder();
    private static OkHttpClient httpClient = null;
    private boolean isNetworkError = false;
    private boolean isHttpStatusError = false;
    private String errorMessage = "";
    private int httpStatusCode = 0;
    private Request.Builder requestBuilder;
    private FormBody.Builder requestBodyBuilder;
    private MultipartBody.Builder multipartBuilder;
    private Cipher cipher = null;
    private Context context = null;
    private long cacheTimeOut = -1;
    private InputStream is = null;
    private Response response;
    private Call call;
    private int retryLimit = DEFAULT_RETRY_LIMIT;

    public APIRequest(HttpMethod httpMethod, String url, Cipher cipher, long cacheTimeOut, Context context) {
        this(httpMethod, url, cipher, 10000);
        this.cacheTimeOut = cacheTimeOut;
        this.context = context;
    }

    public APIRequest(HttpMethod httpMethod, String url, Cipher cipher) {
        this(httpMethod, url, cipher, 10000);
    }

    public APIRequest(HttpMethod httpMethod, String url, Cipher cipher, int socketTimeout) {
        okHttpBuilder = new OkHttpClient.Builder();

        okHttpBuilder.connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS));
        okHttpBuilder.connectTimeout(10, TimeUnit.SECONDS);
        okHttpBuilder.readTimeout(socketTimeout, TimeUnit.MILLISECONDS);

        requestBuilder = new Request.Builder();
        getParams = TextUtils.isEmpty(Uri.parse(url).getQuery()) ? "" : "?" + Uri.parse(url).getQuery();
        this.httpMethod = httpMethod;
        this.url = url.split("\\?")[0];
        this.cipher = cipher;

        if (httpMethod == HttpMethod.GET) {
            requestBuilder.get();
        } else if (httpMethod == HttpMethod.DELETE) {
            requestBuilder.delete();
        }
    }

    static public void setCache(File directory, long size) {
        Cache cache = new Cache(directory, size);
        okHttpBuilder.cache(cache);
    }

    public void addGetParam(String key, String value) {
        if (TextUtils.isEmpty(getParams)) {
            getParams = "?";
        } else if (!getParams.endsWith("&")) {
            getParams += "&";
        }
        getParams += key + "=" + value;
    }

    public void addGetParam(String parameter) {
        if (TextUtils.isEmpty(getParams)) {
            getParams = "?";
        } else if (!getParams.endsWith("&")) {
            getParams += "&";
        }
        getParams += parameter;
    }

    public void addEmptyPostParam() {
        if (requestBodyBuilder == null) {
            requestBodyBuilder = new FormBody.Builder();
        }
    }

    public void addPostParam(String key, String value) {
        if (requestBodyBuilder == null) {
            requestBodyBuilder = new FormBody.Builder();
        }
        requestBodyBuilder.add(key, value);
    }

    public void addHeaderParam(String key, String value) {
        requestBuilder.addHeader(key, value);
    }

    public void addMultiPartPostParam(String key, String fileName, RequestBody requestBody) {
        if (multipartBuilder == null) {
            multipartBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        }
        multipartBuilder.addFormDataPart(key, fileName, requestBody);
    }

    public void addMultiPartPostParam(String key, String value) {
        if (multipartBuilder == null) {
            multipartBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        }
        multipartBuilder.addFormDataPart(key, value);
    }

    public void addStringPostParam(String data) {
        MediaType mediaType = MediaType.parse("text/plain");
        if (httpMethod == HttpMethod.PUT) {
            requestBuilder.put(RequestBody.create(mediaType, data));
        } else {
            requestBuilder.post(RequestBody.create(mediaType, data));
        }
    }

    public void addFilePostParam(String path) {
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8");
        if (httpMethod == HttpMethod.PUT) {
            requestBuilder.put(RequestBody.create(mediaType, new File(path)));
        } else {
            requestBuilder.post(RequestBody.create(mediaType, new File(path)));
        }
    }

    public void addByteArrayPostParam(final byte[] data) {
        MediaType mediaType = MediaType.parse("application/octet-stream");
        RequestBody requestBody = RequestBody.create(mediaType, data);
        if (httpMethod == HttpMethod.PUT) {
            requestBuilder.put(requestBody);
        } else {
            requestBuilder.post(requestBody);
        }
    }

    public void addJSONPostParam(JSONObject jsonObject) {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(mediaType, jsonObject.toString());
        if (httpMethod == HttpMethod.PUT) {
            requestBuilder.put(requestBody);
        } else {
            requestBuilder.post(requestBody);
        }
    }

    public void setRetryCount(int retryLimit) {
        this.retryLimit = retryLimit;
    }

    public void cancel() {
        listener = null;
        // TODO: https://github.com/square/okhttp/issues/1592
        if (httpClient != null) {
            httpClient.dispatcher().executorService().execute(new Runnable() {
                @Override
                public void run() {
                    if (call != null) {
                        call.cancel();
                    }
                }
            });
        }
        this.cancel(true);
    }

    protected abstract void parseInputStream(InputStream inputStream, Cipher cipher) throws IOException, BadPaddingException, IllegalBlockSizeException;

    @Override
    public Void doInBackground(Object... params) {
        int readLength;
        final byte[] buffer = new byte[128];
        listener = (APIRequestListener) params[0];
        int retryTimes = 0;
        File cacheFile = null;
        ConnectivityManager connectivityManager = null;
        if (context != null) {
            final File cacheDir = new File(context.getCacheDir().getAbsolutePath() + File.separator + "api");
            if (!cacheDir.exists()) {
                cacheDir.mkdir();
            }
            cacheFile = new File(cacheDir.getAbsolutePath() + File.separator + StringUtils.getMd5Hash(url + getParams));
            connectivityManager = (ConnectivityManager) context.getSystemService(context.CONNECTIVITY_SERVICE);
        }

        if (context != null && cacheTimeOut > 0 && cacheFile.exists()
                && ((System.currentTimeMillis() - cacheFile.lastModified() < cacheTimeOut)
                || connectivityManager.getActiveNetworkInfo() == null)) {
            try {
                parseInputStream(new FileInputStream(cacheFile), cipher);
            } catch (IOException e) {
                isNetworkError = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            do {
                try {
                    KKDebug.i("Connect API url " + url + getParams);
                    if (requestBodyBuilder != null) {
                        if (httpMethod == HttpMethod.PUT) {
                            requestBuilder.put(requestBodyBuilder.build());
                        } else {
                            requestBuilder.post(requestBodyBuilder.build());
                        }
                    }
                    if (multipartBuilder != null) {
                        if (httpMethod == HttpMethod.PUT) {
                            requestBuilder.put(multipartBuilder.build());
                        } else {
                            requestBuilder.post(multipartBuilder.build());
                        }
                    }

                    if (TextUtils.isEmpty(getParams)) {
                        requestBuilder.url(url);
                    } else {
                        requestBuilder.url(url + getParams);
                    }

                    Request request = requestBuilder.build();

                    httpClient = okHttpBuilder.build();
                    call = okHttpBuilder.build().newCall(request);
                    response = call.execute();

                    httpStatusCode = response.code();
                    int httpStatusType = httpStatusCode / 100;
                    switch (httpStatusType) {
                        case 2:
                            is = response.body().byteStream();
                            isNetworkError = false;
                            break;
                        case 4:
                            KKDebug.w("Get client error " + httpStatusCode + " with connection : " + url + getParams);
                            is = response.body().byteStream();
                            isHttpStatusError = true;
                            isNetworkError = false;
                            break;
                        case 5:
                            KKDebug.w("Get server error " + httpStatusCode + " with connection : " + url + getParams);
                            is = response.body().byteStream();
                            isHttpStatusError = true;
                            isNetworkError = false;
                            break;
                        default:
                            KKDebug.w("connection to " + url + getParams + " returns " + httpStatusCode);
                            retryTimes++;
                            isNetworkError = true;
                            SystemClock.sleep(1000);
                            break;
                    }
                } catch (final SSLException e) {
                    KKDebug.w("connection to " + url + getParams + " failed with " + e.getClass().getName());
                    isNetworkError = true;
                    errorMessage = e.getClass().getName();
                    return null;
                } catch (final Exception e) {
                    KKDebug.w("connection to " + url + getParams + " failed!");
                    retryTimes++;
                    isNetworkError = true;
                    SystemClock.sleep(1000);
                }
            } while (isNetworkError && retryTimes < retryLimit);

            try {
                if (!isNetworkError && !isHttpStatusError && listener != null) {
                    if (cacheTimeOut > 0) {
                        FileOutputStream fileOutputStream = new FileOutputStream(cacheFile);
                        while ((readLength = is.read(buffer, 0, buffer.length)) != -1) {
                            fileOutputStream.write(buffer, 0, readLength);
                        }
                        fileOutputStream.close();
                        parseInputStream(new FileInputStream(cacheFile), cipher);
                    } else {
                        parseInputStream(is, cipher);
                    }
                } else if (isHttpStatusError) {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    while ((readLength = is.read(buffer, 0, buffer.length)) != -1) {
                        byteArrayOutputStream.write(buffer, 0, readLength);
                    }
                    byteArrayOutputStream.flush();
                    errorMessage = byteArrayOutputStream.toString();
                }
            } catch (IOException e) {
                isNetworkError = true;
            } catch (Exception e) {
            }
        }
        return null;
    }

    public void onPostExecute(Void v) {
        if (isHttpStatusError) {
            if (listener != null) {
                listener.onHttpStatusError(httpStatusCode);
            }
            if (listener != null) {
                listener.onHttpStatusError(httpStatusCode, errorMessage);
            }
        } else if (isNetworkError) {
            if (listener != null) {
                listener.onNetworkError(errorMessage);
            }
        } else {
            if (listener != null) {
                listener.onComplete();
            }
        }
    }
}
