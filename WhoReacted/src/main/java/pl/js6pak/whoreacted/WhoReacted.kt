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
package pl.js6pak.whoreacted

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import c.a.y.a0
import c.f.g.f.c
import com.aliucord.Logger
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.PinePatchFn
import com.aliucord.utils.RxUtils
import com.aliucord.utils.RxUtils.subscribe
import com.discord.stores.StoreMessageReactions
import com.discord.stores.StoreStream
import com.discord.utilities.color.ColorCompat
import com.discord.utilities.icon.IconUtils
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemReactions
import com.discord.widgets.chat.list.entries.ReactionsEntry
import com.facebook.drawee.view.SimpleDraweeView
import com.google.android.material.chip.Chip
import com.lytefast.flexinput.R
import rx.Subscription
import top.canyie.pine.Pine.CallFrame
import java.util.concurrent.atomic.AtomicReference

@AliucordPlugin
class WhoReacted : Plugin() {
    override fun start(context: Context) {
        val logger = Logger(getName())

        patcher.patch(
            WidgetChatListAdapterItemReactions::class.java.getDeclaredMethod(
                "processReactions",
                ReactionsEntry::class.java
            ),
            PinePatchFn patch@{ callFrame: CallFrame ->
                val binding = (callFrame.thisObject as WidgetChatListAdapterItemReactions).binding
                val reactionsEntry = callFrame.args[0] as ReactionsEntry
                val message = reactionsEntry.message

                logger.verbose("processReactions started (reactionsEntry: {$reactionsEntry})")

                if (message.reactions.size > 10) {
                    logger.verbose("skipped because message had too much reaction emojis")
                    return@patch
                }

                var i = 0
                for (messageReaction in message.reactions) {
                    if (messageReaction.a() >= 100) {
                        logger.verbose("skipped reaction had too much users")
                        continue
                    }

                    val a0Var = binding.d.getChildAt(i++) as a0
                    val store: StoreMessageReactions = StoreStream.getMessageReactions()
                    val reactions = store.reactions

                    val refresh = refresh@{ users: StoreMessageReactions.EmojiResults.Users ->
                        val c1Var = a0Var.l
                        val layout = c1Var.root as LinearLayout

                        if (layout.childCount > 2) {
                            layout.removeViews(2, layout.childCount - 2)
                        }

                        val size = users.users.size
                        if (size <= 0 || size >= 100) {
                            return@refresh
                        }

                        val roundingParams = c() // RoundingParams
                        roundingParams.b = true // setRoundAsCircle

                        var x = 0

                        val avatarSize = (layout.getChildAt(0).height * 1.1).toInt()

                        for (user in users.users.values) {
                            if (x >= 5) {
                                val chip = Chip(layout.context)

                                @SuppressLint("SetTextI18n")
                                chip.text = "+" + (size - x)
                                chip.chipBackgroundColor = ColorStateList.valueOf(
                                    ColorCompat.getThemedColor(
                                        chip,
                                        R.b.colorBackgroundTertiary
                                    )
                                )

                                val params = LinearLayout.LayoutParams(-1, -1)
                                val chipSize = avatarSize / 4
                                params.setMargins(-16, -chipSize, 0, -chipSize)
                                chip.layoutParams = params
                                layout.addView(chip)

                                break
                            }

                            // TODO figure out how to apply this mask: https://discord.com/assets/2ad33adc723eef7837aa4432d8b8e1be.svg
                            val simpleDraweeView = SimpleDraweeView(layout.context)
                            simpleDraweeView.hierarchy.s(roundingParams) // setRoundingParams

                            simpleDraweeView.minimumWidth = avatarSize
                            simpleDraweeView.minimumHeight = avatarSize
                            IconUtils.setIcon(simpleDraweeView, user)

                            val params = LinearLayout.LayoutParams(-1, -1)
                            params.setMargins(if (x == 0) 16 else -16, 0, 0, 0)
                            simpleDraweeView.layoutParams = params
                            layout.addView(simpleDraweeView)

                            x++
                        }
                    }

                    var results: StoreMessageReactions.EmojiResults? = null

                    if (reactions.containsKey(message.id)) {
                        val messageReactions = reactions[message.id]
                        if (messageReactions != null && messageReactions.containsKey(
                                messageReaction.b().c()
                            )
                        ) {
                            results = messageReactions[messageReaction.b().c()]
                        }
                    }

                    if (results !is StoreMessageReactions.EmojiResults.Users) {
                        val subscriptionReference = AtomicReference<Subscription>()
                        subscriptionReference.set(
                            StoreStream.getMessageReactions().observeMessageReactions(
                                message.channelId,
                                message.id,
                                messageReaction.b()
                            ).subscribe(
                                RxUtils.createActionSubscriber(
                                    { result: StoreMessageReactions.EmojiResults? ->
                                        if (result is StoreMessageReactions.EmojiResults.Users) {
                                            if (result.channelId != message.channelId || result.component3() != message.id || result.emoji != messageReaction.b()) {
                                                return@createActionSubscriber
                                            }

                                            logger.verbose("Fetched reaction users: $result")

                                            val subscription = subscriptionReference.get()
                                            subscription?.unsubscribe()

                                            Handler(Looper.getMainLooper()).post {
                                                refresh.invoke(
                                                    result
                                                )
                                            }
                                        }
                                    },
                                    { ex: Throwable? ->
                                        logger.error(
                                            "Error during reaction fetch",
                                            ex
                                        )
                                    }
                                )
                            )
                        )
                    } else {
                        logger.verbose("Got reaction users from cache: $results")
                        refresh.invoke(results)
                    }
                }
            }
        )
    }

    override fun stop(context: Context) = patcher.unpatchAll()
}