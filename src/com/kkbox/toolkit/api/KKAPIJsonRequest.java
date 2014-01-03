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
package com.kkbox.toolkit.api;

import android.content.Context;

import com.kkbox.toolkit.internal.api.APIRequest;
import com.kkbox.toolkit.internal.api.KKAPIJsonRequestListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

public class KKAPIJsonRequest extends APIRequest {

	private KKAPIJsonRequestListener jsonRequestListener;

	public KKAPIJsonRequest(String url, Cipher cipher, long reloadPeriod, Context context) {
		super(url, cipher, reloadPeriod, context);
	}

	public KKAPIJsonRequest(String url, Cipher cipher) {
		super(url, cipher);
	}

	public KKAPIJsonRequest(String url, Cipher cipher, int socketTimeout) {
		super(url, cipher, socketTimeout);
	}

	@Override
	public Void doInBackground(Object... params) {
		jsonRequestListener = (KKAPIJsonRequestListener) params[0];
		return super.doInBackground(params);
	}

	@Override
	protected void readDataFromInputStream(ByteArrayOutputStream data) throws IOException {}

	@Override
	protected void preCompleteAndCachedAPI(ByteArrayOutputStream data, File cacheFile) throws BadPaddingException, IllegalBlockSizeException, IOException {
		jsonRequestListener.onStreamPreComplete(is);
		if (cacheTimeOut > 0) {
			try {
				int readLength;
				byte[] buffer = new byte[128];
				while ((readLength = is.read(buffer, 0, buffer.length)) != -1) {
					data.write(buffer, 0, readLength);
				}
				data.flush();
				FileOutputStream fileOutputStream = new FileOutputStream(cacheFile);
				OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
				outputStreamWriter.write(data.toString());
				outputStreamWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}