//
// $Id$

/*
 * Copyright 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package client.richedit;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.i18n.client.Constants;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HasAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.RichTextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.ToggleButton;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.orth.data.MediaDesc;

import com.threerings.gwt.ui.WidgetUtil;
import com.threerings.gwt.util.StringUtil;

import com.threerings.msoy.web.gwt.CssUtil;

import client.imagechooser.ImageChooserPopup;
import client.images.editor.RichTextToolbarImages;
import client.shell.ShellMessages;
import client.ui.BorderedPopup;
import client.ui.MsoyUI;
import client.ui.RowPanel;

/**
 * A sample toolbar for use with {@link RichTextArea}. It provides a simple UI for all rich text
 * formatting, dynamically displayed only for the available functionality.
 */
public class RichTextToolbar extends Composite
{
    /**
     * This {@link Constants} interface is used to make the toolbar's strings internationalizable.
     */
    public interface Strings extends Constants
    {
        String black ();
        String blue ();
        String bold ();
        String color ();
        String createLink ();
        String font ();
        String green ();
        String hr ();
        String indent ();
        String insertImage ();
        String italic ();
        String justifyCenter ();
        String justifyLeft ();
        String justifyRight ();
        String large ();
        String medium ();
        String normal ();
        String ol ();
        String outdent ();
        String red ();
        String removeFormat ();
        String removeLink ();
        String size ();
        String small ();
        String strikeThrough ();
        String subscript ();
        String superscript ();
        String ul ();
        String underline ();
        String white ();
        String xlarge ();
        String xsmall ();
        String xxlarge ();
        String xxsmall ();
        String yellow ();
    }

    /**
     * Creates a new toolbar that drives the given rich text area.
     *
     * @param richText the rich text area to be controlled
     */
    public RichTextToolbar (RichTextArea richText, boolean allowPanelEdit)
    {
        this.richText = richText;
        this.formatter = richText.getFormatter();

        outer.add(topPanel);
        outer.add(bottomPanel);
        topPanel.setWidth("100%");
        bottomPanel.setWidth("100%");

        initWidget(outer);
        setStyleName("gwt-RichTextToolbar");

        if (formatter == null) {
            return;
        }

        topPanel.add(bold = createToggleButton(images.bold(), strings.bold()));
        topPanel.add(italic = createToggleButton(images.italic(), strings.italic()));
        topPanel.add(underline =
            createToggleButton(images.underline(), strings.underline()));
        topPanel.add(subscript =
            createToggleButton(images.subscript(), strings.subscript()));
        topPanel.add(superscript =
            createToggleButton(images.superscript(), strings.superscript()));
        topPanel.add(justifyLeft =
            createPushButton(images.justifyLeft(), strings.justifyLeft()));
        topPanel.add(justifyCenter =
            createPushButton(images.justifyCenter(), strings.justifyCenter()));
        topPanel.add(justifyRight =
            createPushButton(images.justifyRight(), strings.justifyRight()));

        topPanel.add(strikethrough =
            createToggleButton(images.strikeThrough(), strings.strikeThrough()));
        topPanel.add(indent = createPushButton(images.indent(), strings.indent()));
        topPanel.add(outdent = createPushButton(images.outdent(), strings.outdent()));
        topPanel.add(hr = createPushButton(images.hr(), strings.hr()));
        topPanel.add(ol = createPushButton(images.ol(), strings.ol()));
        topPanel.add(ul = createPushButton(images.ul(), strings.ul()));
        topPanel.add(removeFormat =
            createPushButton(images.removeFormat(), strings.removeFormat()));
        topPanel.add(createLink =
            createPushButton(images.createLink(), strings.createLink()));
        topPanel.add(removeLink =
            createPushButton(images.removeLink(), strings.removeLink()));
        topPanel.add(insertImage =
            createPushButton(images.insertImage(), strings.insertImage()));

        bottomPanel.add(new Label("Text:"));
        bottomPanel.add(foreColors = createColorList("Foreground"));
        bottomPanel.add(fonts = createFontList());
        bottomPanel.add(fontSizes = createFontSizes());
        bottomPanel.add(blockFormats = createBlockFormats());

        if (allowPanelEdit) {
            bottomPanel.add(new Button("Panel Colors", new ClickHandler() {
                public void onClick (ClickEvent event) {
                    showPanelColorsPopup();
                }
            }), HasAlignment.ALIGN_MIDDLE);
        }

        // we only use these listeners for updating status, so don't hook them up unless at
        // least basic editing is supported.
        richText.addKeyUpHandler(handler);
        richText.addClickHandler(handler);
    }

    public String getTextColor ()
    {
        return _tcolor;
    }

    public String getBackgroundColor ()
    {
        return _bgcolor;
    }

    public void setPanelColors (String tcolor, String bgcolor)
    {
        _tcolor = tcolor;
        _bgcolor = bgcolor;

        // this may be called before we're added to the DOM, so we need to wait until our inner
        // iframe is created before trying to set its background color, etc.
        new Timer() {
            public void run () {
                if (richText.getElement() != null) {
                    setPanelColorsImpl(richText.getElement(),
                        StringUtil.getOr(_tcolor, ""), StringUtil.getOr(_bgcolor, "none"));
                } else {
                    schedule(100);
                }
            }
        }.schedule(100);
    }

    public void setBlockFormat (String format)
    {
        setBlockFormatImpl(richText.getElement(), format);
    }

    @Override // from Widget
    protected void onAttach ()
    {
        super.onAttach();
        // yes, we have to wait 100ms before we configure our iframe, no you don't want to know
        // why; just walk away and think happy thoughts
        new Timer() {
            public void run () {
                configureIFrame(richText.getElement(), CssUtil.GLOBAL_PATH);
            }
        }.schedule(100);
    }

    protected ListBox createColorList (String caption)
    {
        ListBox lb = new ListBox();
        lb.addChangeHandler(handler);
        lb.setVisibleItemCount(1);

        lb.addItem(caption);
        lb.addItem(strings.white(), "white");
        lb.addItem(strings.black(), "black");
        lb.addItem(strings.red(), "red");
        lb.addItem(strings.green(), "green");
        lb.addItem(strings.yellow(), "yellow");
        lb.addItem(strings.blue(), "blue");
        return lb;
    }

    protected ListBox createFontList ()
    {
        ListBox lb = new ListBox();
        lb.addChangeHandler(handler);
        lb.setVisibleItemCount(1);

        lb.addItem(strings.font(), "");
        lb.addItem(strings.normal(), "");
        lb.addItem("Times New Roman", "Times New Roman");
        lb.addItem("Arial", "Arial");
        lb.addItem("Courier New", "Courier New");
        lb.addItem("Georgia", "Georgia");
        lb.addItem("Trebuchet", "Trebuchet");
        lb.addItem("Verdana", "Verdana");
        return lb;
    }

    protected ListBox createFontSizes ()
    {
        ListBox lb = new ListBox();
        lb.addChangeHandler(handler);
        lb.setVisibleItemCount(1);

        lb.addItem(strings.size());
        lb.addItem(strings.xxsmall());
        lb.addItem(strings.xsmall());
        lb.addItem(strings.small());
        lb.addItem(strings.medium());
        lb.addItem(strings.large());
        lb.addItem(strings.xlarge());
        lb.addItem(strings.xxlarge());
        return lb;
    }

    protected ListBox createBlockFormats ()
    {
        ListBox lb = new ListBox();
        lb.addChangeHandler(handler);
        lb.setVisibleItemCount(1);

        lb.addItem("Format");
        lb.addItem("Normal");
        lb.addItem("Code");
        lb.addItem("Header 1");
        lb.addItem("Header 2");
        lb.addItem("Header 3");
        lb.addItem("Header 4");
        lb.addItem("Header 5");
        lb.addItem("Header 6");
        return lb;
    }

    protected PushButton createPushButton (ImageResource img, String tip)
    {
        PushButton pb = new PushButton(new Image(img));
        pb.addClickHandler(handler);
        pb.setTitle(tip);
        return pb;
    }

    protected ToggleButton createToggleButton (ImageResource img, String tip)
    {
        ToggleButton tb = new ToggleButton(new Image(img));
        tb.addClickHandler(handler);
        tb.setTitle(tip);
        return tb;
    }

    /**
     * Updates the status of all the stateful buttons.
     */
    protected void updateStatus ()
    {
        if (formatter != null) {
            bold.setDown(formatter.isBold());
            italic.setDown(formatter.isItalic());
            underline.setDown(formatter.isUnderlined());
            subscript.setDown(formatter.isSubscript());
            superscript.setDown(formatter.isSuperscript());

            strikethrough.setDown(formatter.isStrikethrough());
        }
    }

    protected void showPanelColorsPopup ()
    {
        final BorderedPopup popup = new BorderedPopup();
        FlexTable contents = new FlexTable();
        contents.setCellSpacing(5);
        contents.setCellPadding(0);
        contents.setText(0, 0, "Enter panel colors (in hex ASCII format, e.g. #FFCC99):");
        contents.getFlexCellFormatter().setColSpan(0, 0, 2);

        contents.setText(1, 0, "Text color:");
        final TextBox tcolor = MsoyUI.createTextBox(_tcolor, 7, 7);
        contents.setWidget(1, 1, tcolor);

        contents.setText(2, 0, "Background color:");
        final TextBox bgcolor = MsoyUI.createTextBox(_bgcolor, 7, 7);
        contents.setWidget(2, 1, bgcolor);

        HorizontalPanel buttons = new HorizontalPanel();
        buttons.add(new Button(_cmsgs.cancel(), new ClickHandler() {
            public void onClick (ClickEvent event) {
                popup.hide();
            }
        }));
        buttons.add(WidgetUtil.makeShim(5, 5));
        buttons.add(new Button(_cmsgs.update(), new ClickHandler() {
            public void onClick (ClickEvent event) {
                setPanelColors(tcolor.getText().trim().toLowerCase(),
                               bgcolor.getText().trim().toLowerCase());
                popup.hide();
            }
        }));
        contents.setWidget(3, 0, buttons);
        contents.getFlexCellFormatter().setColSpan(3, 0, 2);
        contents.getFlexCellFormatter().setHorizontalAlignment(3, 0, HasAlignment.ALIGN_RIGHT);

        popup.setWidget(contents);
        popup.show();
    }

    protected static native void configureIFrame (Element elem, String globalCss) /*-{
        var hostport = $wnd.location.hostname + ":" + $wnd.location.port;
        var head = elem.contentWindow.document.getElementsByTagName("head")[0];
        var ss = elem.contentWindow.document.createElement("link");
        ss.type = "text/css";
        ss.rel = "stylesheet";
        ss.href = globalCss;
        head.appendChild(ss);
    }-*/;

    protected static native void setPanelColorsImpl (
        Element elem, String tcolor, String bgcolor) /*-{
        elem.contentWindow.document.body.style['color'] = tcolor;
        elem.contentWindow.document.body.style['background'] = bgcolor;
    }-*/;

    protected static native void setBlockFormatImpl (Element elem, String format) /*-{
        elem.contentWindow.document.execCommand("FormatBlock", false, format);
    }-*/;

    /**
     * We use an inner EventHandler class to avoid exposing event methods on the
     * RichTextToolbar itself.
     */
    protected class EventHandler implements ClickHandler, ChangeHandler, KeyUpHandler
    {
        public void onChange (ChangeEvent event) {
            Widget sender = (Widget)event.getSource();
            if (sender == foreColors) {
                formatter.setForeColor(foreColors.getValue(foreColors.getSelectedIndex()));
                foreColors.setSelectedIndex(0);
            } else if (sender == fonts) {
                formatter.setFontName(fonts.getValue(fonts.getSelectedIndex()));
                fonts.setSelectedIndex(0);
            } else if (sender == fontSizes) {
                formatter.setFontSize(fontSizesConstants[fontSizes.getSelectedIndex() - 1]);
                fontSizes.setSelectedIndex(0);
            } else if (sender == blockFormats) {
                setBlockFormat(blockFormatConstants[blockFormats.getSelectedIndex() - 1]);
                blockFormats.setSelectedIndex(0);
            }
        }

        public void onClick (ClickEvent event) {
            Widget sender = (Widget)event.getSource();
            if (sender == bold) {
                formatter.toggleBold();
            } else if (sender == italic) {
                formatter.toggleItalic();
            } else if (sender == underline) {
                formatter.toggleUnderline();
            } else if (sender == subscript) {
                formatter.toggleSubscript();
            } else if (sender == superscript) {
                formatter.toggleSuperscript();
            } else if (sender == strikethrough) {
                formatter.toggleStrikethrough();
            } else if (sender == indent) {
                formatter.rightIndent();
            } else if (sender == outdent) {
                formatter.leftIndent();
            } else if (sender == justifyLeft) {
                formatter.setJustification(RichTextArea.Justification.LEFT);
            } else if (sender == justifyCenter) {
                formatter.setJustification(RichTextArea.Justification.CENTER);
            } else if (sender == justifyRight) {
                formatter.setJustification(RichTextArea.Justification.RIGHT);
            } else if (sender == insertImage) {
                ImageChooserPopup.displayImageChooser(false, new AsyncCallback<MediaDesc>() {
                    public void onSuccess (MediaDesc image) {
                        if (image != null) {
                            formatter.insertImage(image.getMediaPath());
                        }
                    }
                    public void onFailure (Throwable t) {
                        // not used
                    }
                });
            } else if (sender == createLink) {
                String url = Window.prompt("Enter a link URL:", "http://");
                if (url != null) {
                    formatter.createLink(url);
                }
            } else if (sender == removeLink) {
                formatter.removeLink();
            } else if (sender == hr) {
                formatter.insertHorizontalRule();
            } else if (sender == ol) {
                formatter.insertOrderedList();
            } else if (sender == ul) {
                formatter.insertUnorderedList();
            } else if (sender == removeFormat) {
                formatter.removeFormat();
            } else if (sender == richText) {
                // We use the RichTextArea's onKeyUp event to update the toolbar status.
                // This will catch any cases where the user moves the cursur using the
                // keyboard, or uses one of the browser's built-in keyboard shortcuts.
                updateStatus();
            }
        }

        public void onKeyUp (KeyUpEvent event) {
            Widget sender = (Widget)event.getSource();
            if (sender == richText) {
                // We use the RichTextArea's onKeyUp event to update the toolbar status.
                // This will catch any cases where the user moves the cursur using the
                // keyboard, or uses one of the browser's built-in keyboard shortcuts.
                updateStatus();
            }
        }
    }

    protected RichTextToolbarImages images = (RichTextToolbarImages)
        GWT.create(RichTextToolbarImages.class);
    protected Strings strings = (Strings) GWT.create(Strings.class);
    protected EventHandler handler = new EventHandler();

    protected RichTextArea richText;
    protected RichTextArea.Formatter formatter;

    protected VerticalPanel outer = new VerticalPanel();
    protected HorizontalPanel topPanel = new HorizontalPanel();
    protected RowPanel bottomPanel = new RowPanel();
    protected ToggleButton bold;
    protected ToggleButton italic;
    protected ToggleButton underline;
    protected ToggleButton subscript;
    protected ToggleButton superscript;
    protected ToggleButton strikethrough;
    protected PushButton indent;
    protected PushButton outdent;
    protected PushButton justifyLeft;
    protected PushButton justifyCenter;
    protected PushButton justifyRight;
    protected PushButton hr;
    protected PushButton ol;
    protected PushButton ul;
    protected PushButton insertImage;
    protected PushButton createLink;
    protected PushButton removeLink;
    protected PushButton removeFormat;

    protected ListBox foreColors;
    protected ListBox fonts;
    protected ListBox fontSizes;
    protected ListBox blockFormats;

    protected String _tcolor, _bgcolor;

    protected static final ShellMessages _cmsgs = GWT.create(ShellMessages.class);

    protected static final RichTextArea.FontSize[] fontSizesConstants =
        new RichTextArea.FontSize[] {
        RichTextArea.FontSize.XX_SMALL, RichTextArea.FontSize.X_SMALL,
        RichTextArea.FontSize.SMALL, RichTextArea.FontSize.MEDIUM,
        RichTextArea.FontSize.LARGE, RichTextArea.FontSize.X_LARGE,
        RichTextArea.FontSize.XX_LARGE};

    protected static final String[] blockFormatConstants = new String[] {
        "<p>", "<pre>", "<h1>", "<h2>", "<h3>", "<h4>", "<h5>", "<h6>"
    };
}
