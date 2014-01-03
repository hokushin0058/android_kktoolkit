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
package com.kkbox.toolkit.internal.dialog;

import com.kkbox.toolkit.dialog.KKDialog;


public abstract interface KKDialogManagerListener {
	public abstract void onNotification(KKDialog dialog);
	
	public abstract void onCancelNotification();

	public abstract void onAllNotificationEnded();
}