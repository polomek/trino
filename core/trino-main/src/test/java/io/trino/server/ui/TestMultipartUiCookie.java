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

import com.google.common.collect.ImmutableMap;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestMultipartUiCookie
{
    private static final String COOKIE_NAME = "__UI_Cookie";

    private static final MultipartUiCookie COOKIE = new MultipartUiCookie(COOKIE_NAME);

    @Test
    public void testShortToken()
    {
        String longToken = "123456789".repeat(100); // < 4096 cookie value length limit
        NewCookie[] newCookies = COOKIE.create(longToken, Instant.EPOCH);
        assertThat(newCookies).hasSize(1);
        assertThat(newCookies).extracting(NewCookie::getName).hasSameElementsAs(List.of("__UI_Cookie"));
        assertThat(COOKIE.read(cookies(newCookies))).contains(longToken);

        NewCookie[] deleteCookies = COOKIE.delete(cookies(newCookies));
        assertThat(deleteCookies).hasSize(1);
        assertThat(deleteCookies).extracting(NewCookie::getName).hasSameElementsAs(List.of("__UI_Cookie"));
    }

    @Test
    public void testLongToken()
    {
        String longToken = "123456789".repeat(1000); // > 4096 cookie value length limit
        NewCookie[] newCookies = COOKIE.create(longToken, Instant.EPOCH);
        assertThat(newCookies).hasSize(3);
        assertThat(newCookies).extracting(NewCookie::getName).hasSameElementsAs(List.of("__UI_Cookie", "__UI_Cookie_1", "__UI_Cookie_2"));
        assertThat(COOKIE.read(cookies(newCookies))).contains(longToken);

        NewCookie[] deleteCookies = COOKIE.delete(cookies(newCookies));
        assertThat(deleteCookies).hasSize(3);
        assertThat(deleteCookies).extracting(NewCookie::getName).hasSameElementsAs(List.of("__UI_Cookie", "__UI_Cookie_1", "__UI_Cookie_2"));
    }

    @Test
    public void testNonContinuousToken()
    {
        assertThat(COOKIE.read(cookies(cookie(0, "a"), cookie(2, "a")))).isEmpty();
        assertThat(COOKIE.read(cookies(cookie(0, "a"), cookie(1, "a"), cookie(3, "a")))).isEmpty();
    }

    @Test
    public void testCookieDelete()
    {
        assertThat(COOKIE.delete(cookies(cookie(0, "a"), cookie(2, "a")))).hasSize(2);
        assertThat(COOKIE.delete(cookies(cookie(0, "a"), cookie(2, "a"), cookie("some-other-cookie", "some-other-value")))).hasSize(2);
    }

    private static Map<String, Cookie> cookies(NewCookie... cookies)
    {
        ImmutableMap.Builder<String, Cookie> result = ImmutableMap.builderWithExpectedSize(cookies.length);
        for (NewCookie cookie : cookies) {
            result.put(cookie.getName(), cookie);
        }
        return result.buildOrThrow();
    }

    private static NewCookie cookie(int index, String value)
    {
        return cookie(COOKIE.cookieName(index), value);
    }

    private static NewCookie cookie(String name, String value)
    {
        return new NewCookie.Builder(name)
                .value(value)
                .build();
    }
}
