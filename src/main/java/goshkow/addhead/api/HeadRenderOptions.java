package goshkow.addhead.api;

/**
 * Rendering options for exported AddHeads components and string formats.
 *
 * @param target target context for the rendered head
 * @param spacing number of separator symbols after the head, clamped to 0..10
 * @param shadowEnabled whether the head should keep its text shadow
 */
public record HeadRenderOptions(HeadRenderTarget target, int spacing, boolean shadowEnabled) {

    public HeadRenderOptions {
        if (target == null) {
            target = HeadRenderTarget.CUSTOM;
        }
        spacing = Math.max(0, Math.min(10, spacing));
    }

    public static HeadRenderOptions chat(int spacing, boolean shadowEnabled) {
        return new HeadRenderOptions(HeadRenderTarget.CHAT, spacing, shadowEnabled);
    }

    public static HeadRenderOptions tab(int spacing, boolean shadowEnabled) {
        return new HeadRenderOptions(HeadRenderTarget.TAB, spacing, shadowEnabled);
    }

    public static HeadRenderOptions custom(int spacing, boolean shadowEnabled) {
        return new HeadRenderOptions(HeadRenderTarget.CUSTOM, spacing, shadowEnabled);
    }
}
