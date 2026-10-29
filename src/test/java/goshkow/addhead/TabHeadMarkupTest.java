package goshkow.addhead;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that player-list head markup uses syntax understood by TAB in both of
 * its parsers: {@code <head_texture:HASH>}, {@code <head:UUID>} and
 * {@code <head:NAME>}.
 *
 * <p>Regression tests for issue #6. Older AddHeads builds emitted
 * {@code <head:signed_texture:VALUE;SIGNATURE>}, {@code <head:id:UUID>},
 * {@code <head:name:NAME>} and {@code <head:texture:HASH>}. TAB's parsers read
 * those as head definitions literally named "signed_texture", "id", "name" or
 * "texture", and AddHeads worked around the resulting breakage by disabling
 * MiniMessage support inside TAB's own config file.</p>
 */
class TabHeadMarkupTest {

    private static final UUID PLAYER_ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void textureHashTakesPrecedence() {
        assertEquals(
                "<head_texture:ff9bb9e56125c8227b94bbda9f6e0f862931c229255ba8f1205d13c44c1bb561>",
                Utils.buildTabHeadMarkup(PLAYER_ID, "Notch", "ff9bb9e56125c8227b94bbda9f6e0f862931c229255ba8f1205d13c44c1bb561")
        );
    }

    @Test
    void uuidIsUsedWhenNoTextureIsCached() {
        assertEquals("<head:" + PLAYER_ID + ">", Utils.buildTabHeadMarkup(PLAYER_ID, "Notch", ""));
    }

    @Test
    void nameIsUsedAsLastResort() {
        assertEquals("<head:Notch>", Utils.buildTabHeadMarkup(null, "Notch", null));
    }

    @Test
    void invalidDefinitionProducesNoMarkup() {
        assertEquals("", Utils.buildTabHeadMarkup(null, null, null));
        assertEquals("", Utils.buildTabHeadMarkup(null, "", ""));
        // TAB resolves anything longer than 16 characters as a UUID or texture,
        // so a long non-UUID name would not render a head at all.
        assertEquals("", Utils.buildTabHeadMarkup(null, "way_too_long_player_name", null));
    }

    @Test
    void markupAvoidsSyntaxTabCannotParse() {
        String texture = Utils.buildTabHeadMarkup(PLAYER_ID, "Notch", "abcdef");
        String uuid = Utils.buildTabHeadMarkup(PLAYER_ID, "Notch", null);
        String name = Utils.buildTabHeadMarkup(null, "Notch", null);

        for (String markup : new String[]{texture, uuid, name}) {
            assertFalse(markup.contains("signed_texture"), markup);
            assertFalse(markup.contains("<head:id:"), markup);
            assertFalse(markup.contains("<head:name:"), markup);
            assertFalse(markup.contains("<head:texture:"), markup);
        }
    }

    @Test
    void separatorStaysLegacySoBothParsersAcceptIt() {
        String markup = Utils.buildTabHeadSeparatorMarkup(2);

        assertTrue(markup.startsWith("&l"), markup);
        assertTrue(markup.endsWith("&r"), markup);
        assertEquals("", Utils.buildTabHeadSeparatorMarkup(0));
    }
}
