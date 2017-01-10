/**
 * Copyright 2005-2016 hdiv.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hdiv.urlProcessor;

import org.hdiv.config.HDIVConfig;

public interface UrlData {

	String OBFUSCATION_PATH = "oBfT";

	String getUrlWithoutContextPath();

	String getUrlParams();

	boolean containsParams();

	String getUriTemplate();

	boolean isInternal();

	String getContextPathRelativeUrl();

	void setComposedUrlParams(String composeParams);

	String getProcessedUrlWithHdivState(StringBuilder sb, String hdivParameter, String stateParam);

	String getProcessedUrl(StringBuilder sb);

	boolean isHdivStateNecessary(HDIVConfig config);

}
