package com.github.aaronoho.codememo;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

final class CodeMemoWrapLayout extends FlowLayout {
    CodeMemoWrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        return layoutSize(target);
    }

    private Dimension layoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getWidth();
            if (targetWidth <= 0 && target.getParent() != null) {
                targetWidth = target.getParent().getWidth();
            }
            if (targetWidth <= 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            Insets insets = target.getInsets();
            int maxWidth = targetWidth - insets.left - insets.right - getHgap() * 2;
            int rowWidth = 0;
            int rowHeight = 0;
            int preferredWidth = 0;
            int preferredHeight = getVgap();

            for (Component component : target.getComponents()) {
                if (!component.isVisible()) {
                    continue;
                }

                Dimension size = component.getPreferredSize();
                int nextWidth = rowWidth == 0 ? size.width : rowWidth + getHgap() + size.width;
                if (nextWidth > maxWidth && rowWidth > 0) {
                    preferredWidth = Math.max(preferredWidth, rowWidth);
                    preferredHeight += rowHeight + getVgap();
                    rowWidth = size.width;
                    rowHeight = size.height;
                } else {
                    rowWidth = nextWidth;
                    rowHeight = Math.max(rowHeight, size.height);
                }
            }

            preferredWidth = Math.max(preferredWidth, rowWidth);
            preferredHeight += rowHeight + getVgap();
            return new Dimension(
                    preferredWidth + insets.left + insets.right + getHgap() * 2,
                    preferredHeight + insets.top + insets.bottom
            );
        }
    }
}
