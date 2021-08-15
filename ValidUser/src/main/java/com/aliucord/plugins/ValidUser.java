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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.aliucord.plugins;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.aliucord.Logger;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.PinePrePatchFn;
import com.aliucord.utils.RxUtils;
import com.discord.models.user.CoreUser;
import com.discord.models.user.User;
import com.discord.stores.StoreStream;
import com.discord.utilities.rest.RestAPI;
import com.discord.utilities.textprocessing.node.UserMentionNode;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import retrofit2.HttpException;

public class ValidUser extends Plugin {
    private final Logger logger = new Logger("ValidUser");

    private final HashSet<Long> invalidUsers = new HashSet<>();

    @NonNull
    @Override
    public Manifest getManifest() {
        var manifest = new Manifest();
        manifest.authors = new Manifest.Author[]{new Manifest.Author("6pak", 141580516380901376L)};
        manifest.description = "ValidUser";
        manifest.version = "0.1.0";
        manifest.description = "Fixes an issue where mentions sometimes become invalid-user";
        manifest.updateUrl = "https://raw.githubusercontent.com/js6pak/aliucord-plugins/builds/updater.json";
        return manifest;
    }

    @Override
    public void start(Context context) throws NoSuchMethodException {
        patcher.patch(
                UserMentionNode.class.getDeclaredMethod("renderUserMention", SpannableStringBuilder.class, UserMentionNode.RenderContext.class),
                new PinePrePatchFn(callFrame -> {
                    @SuppressWarnings("unchecked") UserMentionNode<UserMentionNode.RenderContext> _this = (UserMentionNode<UserMentionNode.RenderContext>) callFrame.thisObject;
                    UserMentionNode.RenderContext renderContext = (UserMentionNode.RenderContext) callFrame.args[1];

                    long userId = _this.getUserId();

                    if (invalidUsers.contains(userId)) {
                        return;
                    }

                    if (!renderContext.getUserNames().containsKey(userId)) {
                        Map<Long, User> users = StoreStream.Companion.getUsers().getUsers();

                        if (!users.containsKey(userId)) {
                            // TODO find a way to make this non blocking
                            AtomicReference<Pair<com.discord.api.user.User, Throwable>> resultBlockingReference = new AtomicReference<>();
                            CountDownLatch latch = new CountDownLatch(1);

                            try {
                                new Thread(() -> {
                                    resultBlockingReference.set(RxUtils.getResultBlocking(RestAPI.Companion.getApi().userGet(userId)));
                                    latch.countDown();
                                }).start();

                                latch.await();
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

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
    }
}
