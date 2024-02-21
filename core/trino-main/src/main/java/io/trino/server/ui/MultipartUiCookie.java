/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.server.ui;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.server.ui.FormWebUiAuthenticationFilter.UI_LOCATION;
import static java.util.Objects.requireNonNull;

public class MultipartUiCookie
{
    // https://chromestatus.com/feature/4946713618939904
    private static final int COOKIE_LENGTH_LIMIT = 4096;

    private final String cookieName;

    public MultipartUiCookie(String cookieName)
    {
        this.cookieName = requireNonNull(cookieName, "cookieName is null");
    }

    public NewCookie[] create(String token, Instant tokenExpiration)
    {
        ImmutableList.Builder<NewCookie> cookiesToSet = ImmutableList.builder();
        int index = 0;
        for (String part : splitValueByLength(token)) {
            cookiesToSet.add(new NewCookie.Builder(cookieName(index++))
                    .value(part)
                    .path(UI_LOCATION)
                    .domain(null)
                    .expiry(Date.from(tokenExpiration))
                    .secure(true)
                    .httpOnly(true)
                    .build());
        }
        return cookiesToSet.build().toArray(new NewCookie[0]);
    }

    public Optional<String> read(Map<String, Cookie> existingCookies)
    {
        long cookiesCount = existingCookies.values().stream()
                .filter(cookie -> cookie.getName().startsWith(cookieName))
                .count();

        if (cookiesCount == 0) {
            return Optional.empty();
        }

        StringBuilder token = new StringBuilder();
        for (int i = 0; i < cookiesCount; i++) {
            Cookie cookie = existingCookies.get(cookieName(i));
            if (cookie == null) {
                return Optional.empty(); // non continuous
            }
            token.append(cookie.getValue());
        }
        return Optional.of(token.toString());
    }

    public NewCookie[] delete(Map<String, Cookie> existingCookies)
    {
        ImmutableSet.Builder<NewCookie> cookiesToDelete = ImmutableSet.builder();
        cookiesToDelete.add(deleteCookie(cookieName)); // Always add first cookie even if it doesn't exist
        for (Cookie existingCookie : existingCookies.values()) {
            if (existingCookie.getName().startsWith(cookieName)) {
                cookiesToDelete.add(deleteCookie(existingCookie.getName()));
            }
        }
        return cookiesToDelete.build().toArray(new NewCookie[0]);
    }

    private static List<String> splitValueByLength(String value)
    {
        return Splitter.fixedLength(COOKIE_LENGTH_LIMIT).splitToList(value);
    }

    @VisibleForTesting
    String cookieName(int index)
    {
        if (index == 0) {
            return cookieName;
        }

        return cookieName + '_' + index;
    }

    private static NewCookie deleteCookie(String name)
    {
        return new NewCookie.Builder(name)
                .value("delete")
                .path(UI_LOCATION)
                .domain(null)
                .maxAge(0)
                .secure(true)
                .httpOnly(true)
                .build();
    }
}
