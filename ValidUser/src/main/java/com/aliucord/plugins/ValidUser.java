/*
 * aliucord-plugins
 * Copyright (C) 2021 js6pak
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.aliucord.plugins;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.style.*;
import android.util.Pair;

import com.aliucord.Logger;
import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.PinePrePatchFn;
import com.aliucord.utils.RxUtils;
import com.discord.models.user.CoreUser;
import com.discord.models.user.User;
import com.discord.stores.StoreStream;
import com.discord.utilities.color.ColorCompat;
import com.discord.utilities.rest.RestAPI;
import com.discord.utilities.textprocessing.node.UserMentionNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import retrofit2.HttpException;
import top.canyie.pine.Pine;

@AliucordPlugin
public class ValidUser extends Plugin {
    private final Logger logger = new Logger("ValidUser");

    private final HashSet<Long> invalidUsers = new HashSet<>();

    @Override
    public void start(Context context) throws NoSuchMethodException {
        patcher.patch(
                UserMentionNode.class.getDeclaredMethod("renderUserMention", SpannableStringBuilder.class, UserMentionNode.RenderContext.class),
                new PinePrePatchFn(callFrame -> {
                    @SuppressWarnings("unchecked") UserMentionNode<UserMentionNode.RenderContext> _this = (UserMentionNode<UserMentionNode.RenderContext>) callFrame.thisObject;
                    SpannableStringBuilder spannableStringBuilder = (SpannableStringBuilder) callFrame.args[0];
                    UserMentionNode.RenderContext renderContext = (UserMentionNode.RenderContext) callFrame.args[1];

                    long userId = _this.getUserId();

                    if (invalidUsers.contains(userId)) {
                        setInvalidUser(renderContext.getContext(), callFrame, spannableStringBuilder, userId);
                        return;
                    }

                    if (!renderContext.getUserNames().containsKey(userId)) {
                        Map<Long, User> users = StoreStream.Companion.getUsers().getUsers();

                        if (!users.containsKey(userId)) {
                            // TODO find a way to make this non blocking
                            AtomicReference<Pair<com.discord.api.user.User, Throwable>> resultBlockingReference = new AtomicReference<>();

                            try {
                                var thread = new Thread(() -> resultBlockingReference.set(RxUtils.getResultBlocking(RestAPI.Companion.getApi().userGet(userId))));
                                thread.start();
                                thread.join();
                            } catch (InterruptedException e) {
                                logger.error(e);
                                return;
                            }

                            Pair<com.discord.api.user.User, Throwable> resultBlocking = resultBlockingReference.get();

                            if (resultBlocking == null) {
                                logger.error("Failed to fetch the user - thread failed", null);
                                return;
                            }

                            if (resultBlocking.second != null) {
                                if (resultBlocking.second instanceof HttpException && ((HttpException) resultBlocking.second).a() == 404) {
                                    invalidUsers.add(userId);
                                    setInvalidUser(renderContext.getContext(), callFrame, spannableStringBuilder, userId);
                                    return;
                                }

                                logger.error("Failed to fetch the user", resultBlocking.second);
                                return;
                            }

                            CoreUser user = new CoreUser(resultBlocking.first);
                            logger.verbose("Fixed invalid-user for " + user);
                            users.put(userId, user);
                        }

                        renderContext.getUserNames().put(userId, Objects.requireNonNull(users.get(userId)).getUsername());
                    }
                })
        );
    }

    private void setInvalidUser(Context context, Pine.CallFrame callFrame, SpannableStringBuilder spannableStringBuilder, long userId) {
        ArrayList<CharacterStyle> arrayList = new ArrayList<>();
        int length = spannableStringBuilder.length();
        arrayList.add(new StyleSpan(1));
        arrayList.add(new BackgroundColorSpan(ColorCompat.getThemedColor(context, com.lytefast.flexinput.R.b.theme_chat_mention_background)));
        arrayList.add(new ForegroundColorSpan(ColorCompat.getThemedColor(context, com.lytefast.flexinput.R.b.theme_chat_mention_foreground)));
        spannableStringBuilder.append("<@!").append(String.valueOf(userId)).append(">");
        for (CharacterStyle characterStyle : arrayList) {
            spannableStringBuilder.setSpan(characterStyle, length, spannableStringBuilder.length(), 33);
        }

        callFrame.setResult(null);
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
    }
}
