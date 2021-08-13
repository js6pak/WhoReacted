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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.aliucord.Logger;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.PinePatchFn;
import com.aliucord.utils.RxUtils;
import com.discord.api.message.reaction.MessageReaction;
import com.discord.databinding.WidgetChatListAdapterItemReactionsBinding;
import com.discord.models.message.Message;
import com.discord.models.user.User;
import com.discord.stores.StoreMessageReactions;
import com.discord.stores.StoreStream;
import com.discord.utilities.color.ColorCompat;
import com.discord.utilities.icon.IconUtils;
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemReactions;
import com.discord.widgets.chat.list.entries.ReactionsEntry;
import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.material.chip.Chip;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

import c.a.i.c1;
import c.a.y.a0;
import c.f.g.f.c;
import rx.functions.Action1;

public class WhoReacted extends Plugin {
    private final Logger logger = new Logger("WhoReacted");

    @NonNull
    @Override
    public Manifest getManifest() {
        var manifest = new Manifest();
        manifest.authors = new Manifest.Author[]{new Manifest.Author("6pak", 141580516380901376L)};
        manifest.description = "WhoReacted";
        manifest.version = "0.1.0";
        manifest.description = "See the avatars of the users who reacted to a message.";
        manifest.updateUrl = "https://raw.githubusercontent.com/js6pak/aliucord-plugins/builds/updater.json";
        return manifest;
    }

    @Override
    public void start(Context context) throws NoSuchFieldException {
        Field bindingField = WidgetChatListAdapterItemReactions.class.getDeclaredField("binding");
        bindingField.setAccessible(true);

        Field reactionsField = StoreMessageReactions.class.getDeclaredField("reactions");
        reactionsField.setAccessible(true);

        patcher.patch(
                WidgetChatListAdapterItemReactions.class,
                "processReactions",
                new Class<?>[]{ReactionsEntry.class},
                new PinePatchFn(callFrame -> {
                    WidgetChatListAdapterItemReactions _this = (WidgetChatListAdapterItemReactions) callFrame.thisObject;
                    ReactionsEntry reactionsEntry = (ReactionsEntry) callFrame.args[0];

                    Message message = reactionsEntry.getMessage();

                    if (message.getReactionsMap().size() > 10) {
                        return;
                    }

                    WidgetChatListAdapterItemReactionsBinding binding;
                    try {
                        binding = Objects.requireNonNull((WidgetChatListAdapterItemReactionsBinding) bindingField.get(_this));
                    } catch (IllegalAccessException e) {
                        logger.error(e);
                        return;
                    }

                    int i = 0;
                    for (MessageReaction messageReaction : message.getReactionsMap().values()) {
                        if (messageReaction.a() >= 100) {
                            continue;
                        }

                        a0 a0Var = (a0) binding.d.getChildAt(i++);

                        StoreMessageReactions store = StoreStream.Companion.getMessageReactions();

                        Map<Long, Map<String, StoreMessageReactions.EmojiResults>> reactions;
                        try {
                            //noinspection unchecked
                            reactions = Objects.requireNonNull((Map<Long, Map<String, StoreMessageReactions.EmojiResults>>) reactionsField.get(store));
                        } catch (IllegalAccessException e) {
                            logger.error(e);
                            return;
                        }

                        @SuppressLint("SetTextI18n") Action1<StoreMessageReactions.EmojiResults.Users> refresh = (StoreMessageReactions.EmojiResults.Users users) -> {
                            c1 c1Var = a0Var.l;

                            LinearLayout layout = (LinearLayout) c1Var.getRoot();

                            if (layout.getChildCount() > 2) {
                                layout.removeViews(2, layout.getChildCount() - 2);
                            }

                            c roundingParams = new c(); // RoundingParams
                            roundingParams.b = true; // setRoundAsCircle

                            int x = 0;

                            int size = users.getUsers().size();
                            if (size >= 100) {
                                return;
                            }

                            for (User user : users.getUsers().values()) {
                                if (x >= 5) {
                                    Chip chip = new Chip(layout.getContext());

                                    chip.setText("+" + (size - x));
                                    chip.setChipBackgroundColor(ColorStateList.valueOf(ColorCompat.getThemedColor(chip, com.lytefast.flexinput.R.b.colorBackgroundTertiary)));

                                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -1);
                                    params.setMargins(-16, -16, 0, -16);
                                    chip.setLayoutParams(params);
                                    layout.addView(chip);

                                    break;
                                }

                                // TODO figure out how to apply this mask: https://discord.com/assets/2ad33adc723eef7837aa4432d8b8e1be.svg
                                SimpleDraweeView simpleDraweeView = new SimpleDraweeView(layout.getContext());
                                simpleDraweeView.getHierarchy().r(roundingParams); // setRoundingParams

                                final int avatarSize = 64;
                                simpleDraweeView.setMinimumWidth(avatarSize);
                                simpleDraweeView.setMinimumHeight(avatarSize);
                                IconUtils.setIcon(simpleDraweeView, user);

                                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -1);
                                params.setMargins(x == 0 ? 16 : -16, 0, 0, 0);
                                simpleDraweeView.setLayoutParams(params);
                                layout.addView(simpleDraweeView);

                                x++;
                            }
                        };

                        StoreMessageReactions.EmojiResults results = null;

                        if (reactions.containsKey(message.getId())) {
                            Map<String, StoreMessageReactions.EmojiResults> messageReactions = reactions.get(message.getId());

                            if (messageReactions != null && messageReactions.containsKey(messageReaction.b().d())) {
                                results = messageReactions.get(messageReaction.b().d());
                            }
                        }

                        if (results == null) {
                            RxUtils.subscribe(StoreStream.Companion.getMessageReactions().observeMessageReactions(message.getChannelId(), message.getId(), messageReaction.b()), RxUtils.createActionSubscriber(result -> {
                                if (result instanceof StoreMessageReactions.EmojiResults.Users) {
                                    logger.debug("Fetched reaction users");
                                    new Handler(Looper.getMainLooper()).post(() -> refresh.call((StoreMessageReactions.EmojiResults.Users) result));
                                }
                            }));
                        } else if (results instanceof StoreMessageReactions.EmojiResults.Users) {
                            logger.debug("Got reaction users from cache");
                            refresh.call((StoreMessageReactions.EmojiResults.Users) results);
                        }
                    }
                })
        );
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
    }
}
