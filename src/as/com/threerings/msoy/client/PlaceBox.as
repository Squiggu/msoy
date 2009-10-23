//
// $Id$

package com.threerings.msoy.client {

import flash.display.DisplayObject;
import flash.display.InteractiveObject;
import flash.display.Shape;
import flash.events.Event;
import flash.geom.Point;
import flash.geom.Rectangle;

import mx.core.UIComponent;

import com.threerings.util.ConfigValueSetEvent;
import com.threerings.util.Log;
import com.threerings.util.StringUtil;

import com.threerings.display.DisplayUtil;

import com.threerings.crowd.client.PlaceView;
import com.threerings.msoy.client.MsoyContext;
import com.threerings.msoy.client.MsoyPlaceView;
import com.threerings.msoy.client.DeploymentConfig;

/**
 * A component that holds our place views and sets up a mask to ensure that the place view does not
 * render outside the box's bounds.
 */
public class PlaceBox extends LayeredContainer
{
    /** The layer priority of help text bubbles. */
    public static const LAYER_HELP_BUBBLES :int = 5;

    /** The layer priority of tutorial panel. */
    public static const LAYER_TUTORIAL :int = 7;

    /** The layer priority of the loading spinner. */
    public static const LAYER_ROOM_SPINNER :int = 10;

    /** The layer priority of the AVRG panel. */
    public static const LAYER_AVRG_PANEL :int = 33;

    /** The layer priority of the scrolling chat. */
    public static const LAYER_CHAT_SCROLL :int = 20;

    /** The layer priority of the occupant List. */
    public static const LAYER_CHAT_LIST :int = 25;

    /** The layer priority of non-moving chat messages. */
    public static const LAYER_CHAT_STATIC :int = 30;

    /** The layer priority of history chat messages. */
    public static const LAYER_CHAT_HISTORY :int = 35;

    /** The layer priority of the trophy award, avatar intro, and chat tip. */
    public static const LAYER_TRANSIENT :int = 50;

    /** The layer priority of the clickable featured place overlay. */
    public static const LAYER_FEATURED_PLACE :int = 100;

    public function PlaceBox (ctx :MsoyContext)
    {
        _ctx = ctx;
        rawChildren.addChild(_mask = new Shape());

        // use a weak reference
        Prefs.events.addEventListener(ConfigValueSetEvent.CONFIG_VALUE_SET,
            handlePrefsUpdated, false, 0, true);
    }

    public function getPlaceView () :PlaceView
    {
        return _placeView;
    }

    public function setPlaceView (view :PlaceView) :void
    {
        // throw an exception now if it's not a display object
        var disp :DisplayObject = DisplayObject(view);
        setBaseLayer(disp);
        _placeView = view;
        _msoyPlaceView = view as MsoyPlaceView;

        updateFrameBackgroundColor();

        // TODO: why is this type-check here? surely when the place view changes it needs to be
        // laid out regardless of type
        if (_placeView is MsoyPlaceView) {
            layoutPlaceView();
        }
    }

    /**
     * Gets the background color of the current place or black if it is not an msoy view.
     */
    public function getPlaceBackgroundColor () :uint
    {
        if (_msoyPlaceView != null) {
            return _msoyPlaceView.getBackgroundColor();
        } else {
            return 0x000000;
        }
    }

    /**
     * Gets the background color of the frame, taking into account the user settings.
     */
    public function getFrameBackgroundColor () :uint
    {
        if (Prefs.getUseCustomBackgroundColor()) {
            return Prefs.getCustomBackgroundColor();
        } else if (_msoyPlaceView != null) {
            return _msoyPlaceView.getBackgroundColor();
        } else {
            return 0xffffff;
        }
    }

    /**
     * Updates the background color.
     */
    public function updateFrameBackgroundColor () :void
    {
        setStyle("backgroundColor", "#" + StringUtil.toHex(getFrameBackgroundColor(), 6));
    }

    public function clearPlaceView (view :PlaceView) :Boolean
    {
        if ((_placeView != null) && (view == null || view == _placeView)) {
            clearBaseLayer();
            _placeView = null;
            return true;
        }
        return false;
    }

    public function setRoomBounds (r :Rectangle) :void
    {
        _roomBounds = r;
        layoutPlaceView();
    }

    /**
     * @return true if there are glyphs under the specified point.  If the glyph extends
     * InteractiveObject and the glyph sprite has mouseEnabled == false, it is not checked.
     */
    public function overlaysMousePoint (stageX :Number, stageY :Number) :Boolean
    {
        var stagePoint :Point = new Point(stageX, stageY);
        for (var ii :int = 0; ii < numChildren; ii ++) {
            var child :DisplayObject = unwrap(getChildAt(ii));
            if (child == _placeView) {
                continue;
            }
            // note that we want hitTestPoint() to be able to modify the value of the
            // child's mouseEnabled property, so do not reorder the following statements
            // in a fit of over-optimization
            if (!child.hitTestPoint(stageX, stageY, true)) {
                continue;
            }
            if (!(child is InteractiveObject) || (child as InteractiveObject).mouseEnabled) {
                return true;
            }
        }
        return false;
    }

    /**
     * This must be called on when our size is changed to allow us update our PlaceView mask and
     * resize the PlaceView itself.
     */
    override public function setActualSize (width :Number, height :Number) :void
    {
        super.setActualSize(width, height);

        if (_msoyPlaceView == null) {
            setMasked(this, 0, 0, this.width, this.height);
        }

        // any PlaceLayer layers get informed of the size change
        for (var ii :int = 0; ii < numChildren; ii ++) {
            var child :DisplayObject = unwrap(getChildAt(ii));
            if (child == _placeView) {
                continue; // we'll handle this later
            } else if (child is PlaceLayer) {
                PlaceLayer(child).setPlaceSize(width, height);
            }
        }

        layoutPlaceView();
    }

    protected function layoutPlaceView () :void
    {
        var w :Number = this.width;
        var h :Number = this.height;

        if (!_ctx.getMsoyClient().isMinimized()) {
            _lastFullSize = new Point(w, h);
        }
        var fullSize :Point = _lastFullSize;
        if (fullSize == null) {
            fullSize = new Point(w + 700, h);
        }

        _base.x = 0;
        _base.y = 0;
        if (_roomBounds != null) {
            // TODO: this is weird, why are we setting x & y of _base, but w & h of _placeView?
            // TODO: forensics on this feature, I'm not sure if anyone is using it
            if (CLEAN_BOUNDS) {
                var p :Point = DisplayUtil.fitRectInRect(_roomBounds, new Rectangle(0, 0, w, h));
                _base.x = Math.max(0, p.x);
                _base.y = Math.max(0, p.y);
                // TODO: enforce a min size?
                w = Math.min(_roomBounds.width, w);
                h = Math.min(_roomBounds.height, h);

            } else {
                _base.x = _roomBounds.x;
                _base.y = _roomBounds.y;
                w = _roomBounds.width;
                h = _roomBounds.height;
            }

        }

        // now inform the place view of its new size
        if (_msoyPlaceView != null) {
            // center the view and add margins if a clip size is given
            var size :Point = null;
            var center :Boolean = _msoyPlaceView.isCentered();
            if (center) {
                var wmargin :Number = 0;
                var hmargin :Number = 0;
                if (_ctx.getMsoyClient().getEmbedding().hasPlaceMargins()) {
                    // set the margins somewhere between 0 and 20, making sure they don't cause
                    // shrinking of an already small view
                    // TODO: softwire 700x500
                    wmargin = Math.max(0, Math.min(20, (fullSize.x - 700) / 2));
                    hmargin = Math.max(0, Math.min(20, (fullSize.y - 500) / 2));
                }
                _msoyPlaceView.setPlaceSize(w - wmargin * 2, h - hmargin * 2);

                // NOTE: getSize must be called after setPlaceSize
                size = _msoyPlaceView.getSize();
                if (size == null || isNaN(size.x) || isNaN(size.y)) {
                    center = false;
                }
            }

            var view :DisplayObject = _msoyPlaceView as DisplayObject;
            if (center) {
                view.x = Math.max((w - size.x) / 2, wmargin);
                view.y = Math.max((h - size.y) / 2, hmargin);

                if (DeploymentConfig.devDeployment) {
                    log.info("Layout place view", "sh", size.y, "sw", size.x,
                             "vx", view.x, "vy", view.y, "w", w, "h", h);
                }

                // TODO: the scrollRect in the room view takes care of cropping, we only require
                // masking if the view does *not* scroll - complicated!

                // mask it so that avatars and items don't bleed out of bounds
                size.x = Math.min(size.x, w - wmargin * 2);
                size.y = Math.min(size.y, h - hmargin * 2);
                setMasked(_base, view.x, view.y, size.x, size.y);
            } else {
                _msoyPlaceView.setPlaceSize(w, h);
                setMasked(_base, 0, 0, w, h);
            }

        } else if (_placeView is UIComponent) {
            UIComponent(_placeView).setActualSize(w, h);
        } else if (_placeView is PlaceLayer) {
            PlaceLayer(_placeView).setPlaceSize(w, h);
        } else if (_placeView != null) {
            Log.getLog(this).warning("PlaceView is not a PlaceLayer or an UIComponent.");
        }

        // TODO: bubble chat can currently overflow a restricted placeview size.
        // Fixing it was turning rabbit-holey, so I'm punting.
    }

    protected function setMasked (
        disp :DisplayObject, x :Number, y : Number, w :Number, h :Number) :void
    {
        if (DeploymentConfig.devDeployment) {
            log.info("Masking", "disp", disp, "x", x, "y", y, "w", w, "h", h);
        }

        if (_masked != disp) {
            if (_masked != null) {
                _masked.mask = null;
            }
            _masked = disp;
            if (_masked != null) {
                _masked.mask = _mask;
            }
        }
        _mask.graphics.clear();
        _mask.graphics.beginFill(0xFFFFFF);
        _mask.graphics.drawRect(x, y, w, h);
        _mask.graphics.endFill();
    }

    protected function handlePrefsUpdated (evt :ConfigValueSetEvent) :void
    {
        switch (evt.name) {
        case Prefs.USE_CUSTOM_BACKGROUND_COLOR:
        case Prefs.CUSTOM_BACKGROUND_COLOR:
            updateFrameBackgroundColor();
            break;
        }
    }

    /** The river of life. */
    protected var _ctx :MsoyContext;

    /** The mask configured on the box or view so that it doesn't overlap outside components. */
    protected var _mask :Shape = new Shape();

    /** The object currently being masked, either this or _placeView. */
    protected var _masked :DisplayObject;

    /** The current place view. */
    protected var _placeView :PlaceView;

    /** The current msoy place view (may be null if not implemented). */
    protected var _msoyPlaceView :MsoyPlaceView;

    protected var _roomBounds :Rectangle;

    /** The size of the area the last time he had an unminimized layout. */
    protected var _lastFullSize :Point;

    protected static const CLEAN_BOUNDS :Boolean = true;
}
}
