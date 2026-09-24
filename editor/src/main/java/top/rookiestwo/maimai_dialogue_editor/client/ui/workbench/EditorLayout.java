package top.rookiestwo.maimai_dialogue_editor.client.ui.workbench;

// 计算像素尺寸；所有收缩仅作用于本次布局，保留用户未受钳制的原始偏好。
record EditorLayout(
        int width, int height, int toolbar, int status,
        int left, int center, int right, int horizontalGap,
        int document, int preview, int actions, int verticalGap,
        boolean leftCollapsed, boolean rightCollapsed
) {
    static final int TOOLBAR_DP = 28;
    static final int STATUS_DP = 20;
    static final int HEADER_DP = 28;
    static final int LEFT_MIN_DP = 160;
    static final int RIGHT_MIN_DP = 200;
    static final int CENTER_MIN_DP = 320;
    static final int PREVIEW_MIN_DP = 180;

    static EditorLayout calculate(EditorLayoutState state, int width, int height, float density) {
        width = Math.max(0, width);
        height = Math.max(0, height);
        int toolbar = Math.min(px(TOOLBAR_DP, density), height);
        int status = Math.min(px(STATUS_DP, density), height - toolbar);
        int horizontalGap = Math.min(px(6, density), width / 4);
        int columnSpace = width - horizontalGap * 2;
        int rail = Math.min(px(28, density), columnSpace / 3);
        int leftMin = px(LEFT_MIN_DP, density);
        int rightMin = px(RIGHT_MIN_DP, density);
        int centerMin = px(CENTER_MIN_DP, density);

        boolean leftCollapsed = state.leftCollapsed;
        boolean rightCollapsed = state.rightCollapsed;
        if ((leftCollapsed ? rail : leftMin) + (rightCollapsed ? rail : rightMin)
                + centerMin > columnSpace) {
            rightCollapsed = true;
        }
        if ((leftCollapsed ? rail : leftMin) + (rightCollapsed ? rail : rightMin)
                + centerMin > columnSpace) {
            leftCollapsed = true;
        }

        int left;
        int right;
        if (leftCollapsed && rightCollapsed) {
            left = rail;
            right = rail;
        } else {
            int minLeft = leftCollapsed ? rail : leftMin;
            int minRight = rightCollapsed ? rail : rightMin;
            int wantedLeft = leftCollapsed ? rail : Math.max(leftMin, (int) Math.round(columnSpace * state.leftFraction));
            int wantedRight = rightCollapsed ? rail : Math.max(rightMin, (int) Math.round(columnSpace * state.rightFraction));
            int[] widths = fitPair(wantedLeft, wantedRight, minLeft, minRight, columnSpace - centerMin);
            left = widths[0];
            right = widths[1];
        }

        int workHeight = height - toolbar - status;
        int document = Math.min(px(28, density), workHeight / 3);
        int verticalGap = Math.min(px(6, density), (workHeight - document) / 3);
        int rowSpace = workHeight - document - verticalGap;
        int header = Math.min(px(HEADER_DP, density), rowSpace / 2);
        int previewMin = Math.min(px(PREVIEW_MIN_DP, density), rowSpace - header);
        int actions = Math.clamp(px(state.actionsDp, density), header, rowSpace - previewMin);
        return new EditorLayout(width, height, toolbar, status,
                left, columnSpace - left - right, right, horizontalGap,
                document, rowSpace - actions, actions, verticalGap,
                leftCollapsed, rightCollapsed);
    }

    // 空间不足时按可压缩部分等比收缩，标题栏和预览的保留空间由调用方保证。
    private static int[] fitPair(int wantedFirst, int wantedSecond, int minFirst, int minSecond, int budget) {
        int first = Math.max(minFirst, wantedFirst);
        int second = Math.max(minSecond, wantedSecond);
        if ((long) first + second <= budget) {
            return new int[]{first, second};
        }
        int available = Math.max(0, budget - minFirst - minSecond);
        long extra = (long) first - minFirst + second - minSecond;
        int firstExtra = extra == 0 ? 0 : (int) (available * ((double) first - minFirst) / extra);
        return new int[]{minFirst + firstExtra, minSecond + available - firstExtra};
    }

    static int px(double dp, float density) {
        return Math.max(0, (int) Math.round(dp * density));
    }

    int workHeight() {
        return height - toolbar - status;
    }

    int centerX() {
        return left + horizontalGap;
    }

    int previewY() {
        return toolbar + document;
    }

    int actionsY() {
        return previewY() + preview + verticalGap;
    }

    int columnSpace() {
        return left + center + right;
    }
}
