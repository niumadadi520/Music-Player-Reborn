package com.mengsama.mod.mengsamanetmusic.compat;

 
final class MaidGuiLayout {
    private MaidGuiLayout() {}

    static int buttonX(int leftPos, int imageWidth, int edgeGap) {
        return leftPos + imageWidth + edgeGap;
    }

    static int alignedButtonX(int leftPos, int imageWidth, int edgeGap, java.util.List<Integer> nativeRightXs) {
        int rightEdge = leftPos + imageWidth;
        if (nativeRightXs == null) return buttonX(leftPos, imageWidth, edgeGap);
        return nativeRightXs.stream()
                .filter(java.util.Objects::nonNull)
                .filter(x -> x >= rightEdge)
                .min(Integer::compareTo)
                .orElseGet(() -> buttonX(leftPos, imageWidth, edgeGap));
    }
}
