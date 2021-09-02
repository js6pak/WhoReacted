version = "0.1.1"
description = "Fixes an issue where mentions sometimes become invalid-user"

aliucord {
    changelog.set(
        """
        * Fixed user fetching
        * Replicating the desktop behaviour, `<@!{user.id}>` is now displayed instead of `@invalid-user`
    """.trimIndent()
    )
}