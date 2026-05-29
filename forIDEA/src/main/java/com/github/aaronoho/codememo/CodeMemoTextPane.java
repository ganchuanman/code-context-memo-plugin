package com.github.aaronoho.codememo;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.JViewport;
import javax.swing.JTextPane;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.StyledDocument;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

final class CodeMemoTextPane extends JTextPane {
    private final SimpleAttributeSet bodyStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet headerStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet fileStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet codeStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet taskStyle = new SimpleAttributeSet();

    CodeMemoTextPane(String text) {
        setEditorKit(new WrapEditorKit());
        setText(text);
        setFont(UIUtil.getLabelFont().deriveFont(14f));
        setEditable(true);
        setMargin(JBUI.insets(8));
        initStyles();
        applyMemoStyles();
    }

    void applyMemoStyles() {
        StyledDocument document = getStyledDocument();
        int length = document.getLength();
        if (length == 0) {
            return;
        }

        try {
            String text = document.getText(0, length);
            document.setCharacterAttributes(0, length, bodyStyle, true);
            styleLines(document, text);
        } catch (BadLocationException ignored) {
        }
    }

    @Override
    public void copy() {
        String selectedText = getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            return;
        }
        StringSelection selection = new StringSelection(selectedText);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public void setSize(Dimension size) {
        Container parent = getParent();
        if (parent instanceof JViewport) {
            size = new Dimension(parent.getWidth(), size.height);
        }
        super.setSize(size);
    }

    private void initStyles() {
        Font uiFont = UIUtil.getLabelFont();
        String uiFamily = uiFont.getFamily();

        StyleConstants.setFontFamily(bodyStyle, uiFamily);
        StyleConstants.setFontSize(bodyStyle, 14);
        StyleConstants.setForeground(bodyStyle, UIUtil.getTextFieldForeground());

        StyleConstants.setFontFamily(headerStyle, uiFamily);
        StyleConstants.setFontSize(headerStyle, 13);
        StyleConstants.setBold(headerStyle, true);
        StyleConstants.setForeground(headerStyle, UIUtil.getLabelForeground());

        StyleConstants.setFontFamily(fileStyle, Font.MONOSPACED);
        StyleConstants.setFontSize(fileStyle, 12);
        StyleConstants.setForeground(fileStyle, new JBColor(0x245AA5, 0x89B4FA));

        StyleConstants.setFontFamily(codeStyle, Font.MONOSPACED);
        StyleConstants.setFontSize(codeStyle, 12);
        StyleConstants.setForeground(codeStyle, new JBColor(0x1F6B38, 0xA6D189));

        StyleConstants.setFontFamily(taskStyle, uiFamily);
        StyleConstants.setFontSize(taskStyle, 14);
        StyleConstants.setForeground(taskStyle, new JBColor(0x7A4B00, 0xE5C07B));

    }

    private void styleLines(StyledDocument document, String text) {
        boolean inCode = false;
        boolean inTask = false;
        int offset = 0;

        while (offset <= text.length()) {
            int lineEnd = text.indexOf('\n', offset);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            int lineLength = lineEnd - offset;
            String line = text.substring(offset, lineEnd);

            if (line.startsWith("File:") || line.startsWith("Line:") || line.startsWith("Location:")
                    || line.startsWith("文件:") || line.startsWith("行号:") || line.startsWith("位置:")) {
                document.setCharacterAttributes(offset, lineLength, fileStyle, true);
                inTask = false;
            } else if ("Key Code:".equals(line) || "关键代码:".equals(line)) {
                document.setCharacterAttributes(offset, lineLength, headerStyle, true);
                inTask = false;
            } else if (line.startsWith("```")) {
                document.setCharacterAttributes(offset, lineLength, codeStyle, true);
                inCode = !inCode;
            } else if ("What to do:".equals(line) || "要做什么:".equals(line)) {
                document.setCharacterAttributes(offset, lineLength, headerStyle, true);
                inTask = true;
            } else if (inCode) {
                document.setCharacterAttributes(offset, lineLength, codeStyle, true);
            } else if (inTask) {
                document.setCharacterAttributes(offset, lineLength, taskStyle, true);
            }

            if (lineEnd == text.length()) {
                break;
            }
            offset = lineEnd + 1;
        }
    }

    private static final class WrapEditorKit extends StyledEditorKit {
        private final ViewFactory viewFactory = new WrapViewFactory();

        @Override
        public ViewFactory getViewFactory() {
            return viewFactory;
        }
    }

    private static final class WrapViewFactory implements ViewFactory {
        @Override
        public View create(Element element) {
            String kind = element.getName();
            if (kind != null) {
                switch (kind) {
                    case AbstractDocument.ContentElementName:
                        return new WrapLabelView(element);
                    case AbstractDocument.ParagraphElementName:
                        return new ParagraphView(element);
                    case AbstractDocument.SectionElementName:
                        return new BoxView(element, View.Y_AXIS);
                    case StyleConstants.ComponentElementName:
                        return new ComponentView(element);
                    case StyleConstants.IconElementName:
                        return new IconView(element);
                    default:
                        break;
                }
            }
            return new LabelView(element);
        }
    }

    private static final class WrapLabelView extends LabelView {
        WrapLabelView(Element element) {
            super(element);
        }

        @Override
        public float getMinimumSpan(int axis) {
            if (axis == View.X_AXIS) {
                return 0;
            }
            return super.getMinimumSpan(axis);
        }
    }
}
