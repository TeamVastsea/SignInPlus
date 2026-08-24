package cc.vastsea.signinplus.rewards

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemRewardParserTest {
    @Test
    fun `parses vanilla items and modern give components`() {
        assertEquals(
            ParsedItemReward("minecraft:apple", 64),
            ItemRewardParser.parse("apple 64")
        )
        assertEquals(
            ParsedItemReward(
                "minecraft:diamond_sword[minecraft:enchantments={levels:{\"minecraft:sharpness\": 3}}]",
                1
            ),
            ItemRewardParser.parse(
                "diamond_sword 1 [minecraft:enchantments={levels:{\"minecraft:sharpness\": 3}}]"
            )
        )
    }

    @Test
    fun `rejects legacy raw NBT and unsafe input`() {
        assertNull(ItemRewardParser.parse("diamond 1 {display:{Name:'legacy'}}"))
        assertNull(ItemRewardParser.parse("diamond 0"))
        assertNull(ItemRewardParser.parse("diamond 1 force=true"))
        assertNull(ItemRewardParser.parse("diamond 1\n[components]"))
    }
}
