package com.threerings.msoy.world.client {

import flash.display.BlendMode;
import flash.display.DisplayObject;
import flash.display.Loader;
import flash.display.LoaderInfo;
import flash.display.Shape;
import flash.display.Sprite;

import flash.display.Bitmap;
import flash.display.BitmapData;

import flash.errors.IOError;

import flash.events.Event;
import flash.events.EventDispatcher;
import flash.events.IEventDispatcher;
import flash.events.IOErrorEvent;
import flash.events.MouseEvent;
import flash.events.NetStatusEvent;
import flash.events.ProgressEvent;
import flash.events.SecurityErrorEvent;
import flash.events.StatusEvent;
import flash.events.TextEvent;

import flash.geom.Point;

import flash.media.Video;

import flash.net.LocalConnection;
import flash.net.NetConnection;
import flash.net.NetStream;

import flash.system.ApplicationDomain;
import flash.system.LoaderContext;
import flash.system.SecurityDomain;

import flash.net.URLRequest;

import flash.utils.getTimer; // function import

import mx.core.Container;
import mx.core.UIComponent;

import mx.containers.Box;
import mx.controls.VideoDisplay;

import mx.effects.Glow;

import mx.events.EffectEvent;
import mx.events.VideoEvent;

import com.threerings.util.Util;
import com.threerings.util.MediaContainer;

import com.threerings.msoy.client.Prefs;

import com.threerings.msoy.item.web.MediaDesc;

import com.threerings.msoy.world.data.MsoyLocation;

import com.threerings.util.HashMap;

/**
 * A base sprite that concerns itself with the mundane details of
 * loading and communication with the loaded media content.
 */
public class MsoySprite extends MediaContainer
{
    /** The current logical coordinate of this media. */
    public const loc :MsoyLocation = new MsoyLocation();

    /**
     * Constructor.
     */
    public function MsoySprite (desc :MediaDesc)
    {
        super(null);
        autoLayout = false;
        setup(desc);
        setStyle("backgroundSize", "100%");
        setStyle("backgroundImage", _loadingImgClass);
    }

    /**
     * Set the scale of the media as affected by our location in the room.
     */ 
    public function setLocationScale (scale :Number) :void
    {
        if (scale != _locScale) {
            _locScale = scale;
            scaleUpdated();
        }
    }

    /**
     * Get the screen width of this sprite, taking into account both
     * horizontal scales.
     */
    public function getActualWidth () :Number
    {
        return getContentWidth() * _locScale;
    }

    /**
     * Get the screen height of this sprite, taking into account both
     * vertical scales.
     */
    public function getActualHeight () :Number
    {
        return getContentHeight() * _locScale;
    }

    protected function setup (desc :MediaDesc) :void
    {
        if (Util.equals(desc, _desc)) {
            return;
        }

        _desc = desc;

        setMedia(desc.getMediaPath());

        scaleUpdated();

        setEditing(false);
    }

    public function setEditing (editing :Boolean) :void
    {
        _editing = editing;
        if (editing) {
            mouseEnabled = true;
            mouseChildren = false;

            // unlisten to any current mouse handlers
//            removeEventListener(MouseEvent.ROLL_OVER, mouseOver);
//            removeEventListener(MouseEvent.ROLL_OUT, mouseOut);
//            removeEventListener(MouseEvent.CLICK, mouseClick);
            //removeEventListener(MouseEvent.MOUSE_MOVE, mouseMoved);

        } else {
            // set up mouse listeners
            if (isInteractive()) {
                mouseEnabled = false; //true;
                mouseChildren = true;

                if (hasAction()) {
//                    addEventListener(MouseEvent.ROLL_OVER, mouseOver);
//                    addEventListener(MouseEvent.ROLL_OUT, mouseOut);
//                    addEventListener(MouseEvent.CLICK, mouseClick);
                    //addEventListener(MouseEvent.MOUSE_MOVE, mouseMoved);
                }

            } else {
                mouseEnabled = false;
                mouseChildren = false;
            }
        }
    }

    override protected function setupSwfOrImage (url :String) :void
    {
        if (_desc.mimeType == MediaDesc.APPLICATION_SHOCKWAVE_FLASH) {
            // create a unique id for the media
            _id = String(getTimer()) + int(Math.random() * int.MAX_VALUE);

            // TODO
            url += "?oid=" + _id;
        }
        super.setupSwfOrImage(url);

        // then, grab a reference to the shared event dispatcher
        _dispatch = (_media as Loader).contentLoaderInfo.sharedEvents;
        addContentListeners();
    }

    /**
     * Add listeners to the event dispatch we share with our content.
     */
    protected function addContentListeners () :void
    {
        _dispatch.addEventListener("msoyQuery", handleInterfaceQuery);
    }

    protected function removeContentListeners () :void
    {
        _dispatch.removeEventListener("msoyQuery", handleInterfaceQuery);
    }

    /**
     * Unload the media we're displaying, clean up any resources.
     *
     * @param completely if true, we're going away and should stop
     * everything. Otherwise, we're just loading up new media.
     */
    override public function shutdown (completely :Boolean = true) :void
    {
        if (_media is VideoDisplay) {
            var vid :VideoDisplay = (_media as VideoDisplay);
            Prefs.setMediaPosition(
                MediaDesc.hashToString(_desc.hash), vid.playheadTime);
        }

        super.shutdown(completely);

        // clean up
        if (completely) {
            // shut down the dispatch
            if (_dispatch != null) {
                removeContentListeners();
                _dispatch = null;
            }
        }
    }

    /**
     * Get the basic hotspot that is the registration point on the media.
     * This point is not scaled.
     */
    public function getMediaHotSpot () :Point
    {
        // TODO: figure out where we're going to store hotspot info
        var p :Point = null; // TODO _desc.hotSpot;
        if (p == null) {
            // if there's no hotspot, it defaults to along the bottom
            p = new Point(_w/2, _h);

        } else {
            // return a clone of the hotspot in the descriptor
            p = p.clone();
        }
        return p;
    }

    /**
     * Get the hotspot to use for layout purposes. This point is 
     * adjusted for scale and any perspectivization.
     */
    public function getLayoutHotSpot () :Point
    {
        var p :Point = getMediaHotSpot();
        p.x = Math.abs(p.x * getMediaScaleX() * _locScale);
        p.y = Math.abs(p.y * getMediaScaleY() * _locScale);
        return p;
    }

    /**
     * Update the location (but not the orientation).
     *
     * @param newLoc may be an MsoyLocation or an Array
     */
    public function setLocation (newLoc :Object) :void
    {
        if (newLoc is MsoyLocation) {
            var mloc :MsoyLocation = (newLoc as MsoyLocation);
            loc.x = mloc.x;
            loc.y = mloc.y;
            loc.z = mloc.z;

        } else {
            var aloc :Array = (newLoc as Array);
            loc.x = aloc[0];
            loc.y = aloc[1];
            loc.z = aloc[2];
        }

        locationUpdated();
    }

    /**
     * An internal convenience method to recompute our screen
     * position when our size, location, or anything like that has
     * been updated.
     */
    protected function locationUpdated () :void
    {
        if (parent is AbstractRoomView) {
            (parent as AbstractRoomView).locationUpdated(this);
        }
    }

    protected function scaleUpdated () :void
    {
        if (!(_media is Perspectivizer)) {
            _media.scaleX = _locScale * getMediaScaleX();
            _media.scaleY = _locScale * getMediaScaleY();
        }

        updateMediaPosition();
    }

    /**
     * Should be called when the media scale or size changes to ensure
     * that the media is positioned correctly.
     */
    protected function updateMediaPosition () :void
    {
        // if scale is negative, the image is flipped and we need to move
        // the origin
        var xscale :Number = getMediaScaleX();
        var yscale :Number = getMediaScaleY();
        _media.x = (xscale >= 0) ? 0 : Math.abs(_w * xscale);
        _media.y = (yscale >= 0) ? 0 : Math.abs(_h * yscale);

        // we may need to be repositioned
        locationUpdated();
    }

    /** A callback from the move. */
    public function moveCompleted (orient :Number) :void
    {
        // nada
    }

    public function setActive (active :Boolean) :void
    {
        alpha = active ? 1.0 : 0.4;
        blendMode = active ? BlendMode.NORMAL : BlendMode.LAYER;
        mouseEnabled = active && isInteractive();
        mouseChildren = active && isInteractive();
    }

    /**
     * During editing, set the X scale of this sprite.
     */
    public function setMediaScaleX (scaleX :Number) :void
    {
        throw new Error("Cannot set scale of abstract MsoySprite");
    }

    /**
     * During editing, set the Y scale of this sprite.
     */
    public function setMediaScaleY (scaleY :Number) :void
    {
        throw new Error("Cannot set scale of abstract MsoySprite");
    }

    public function getDesc () :MediaDesc
    {
        return _desc;
    }

    /**
     * Send a result from a query down to our clientcode content.
     */
    protected function sendResult (result :String) :void
    {
        sendMessage("msoyResult", result);
    }

    /**
     * Send a message to the client swf that we're representing.
     */
    protected function sendMessage (type :String, msg :String) :void
    {
//        trace("sending [" + type + "=" + msg + "]");


// Note:
// I'm thinking that we just do not support old swfs with our interaction
// API. This makes this AVM1 nonsense just go away.
// If it turns out that this isn't possible, then perhaps we should
// assign a different mimetype to old swfs so that we know as part of the
// MediaDesc that it's AVM1

        // do it both ways for now

        // old way
        if (_oldDispatch == null) {
            _oldDispatch = new LocalConnection();
            _oldDispatch.allowDomain("*");
            _oldDispatch.addEventListener(
                StatusEvent.STATUS, onLocalConnStatus);
        }
        try {
            _oldDispatch.send("_msoy" + _id, type, msg);
        } catch (e :Error) {
            // nada
        }

        // and the new way
        // simply post an event across the security boundary
        _dispatch.dispatchEvent(new TextEvent(type, false, false, msg));
    }

    /**
     * A callback called when there is a status event from using
     * the local connection.
     */
    protected static function onLocalConnStatus (event :StatusEvent) :void
    {
        // This method exists because if we don't eat status-error messages
        // then they end up bubbling up somewhere else.

        if (event.level != "status") {
//            Log.getLog(MsoySprite).debug("Unable to communicate with media " +
//                "[event=" + event + "].");
        }
    }

    override protected function loadVideoReady (event :VideoEvent) :void
    {
        var vid :VideoDisplay = (event.currentTarget as VideoDisplay);

        // TODO: this seems broken, check it
        // set the position of the media to the specified timestamp
        vid.playheadTime = Prefs.getMediaPosition(
            MediaDesc.hashToString(_desc.hash));

        super.loadVideoReady(event);
    }

    override protected function contentDimensionsUpdated () :void
    {
        super.contentDimensionsUpdated();

        // even if we don't have strange (negative) scaling, we should do this
        // because it ends up calling locationUpdated().
        updateMediaPosition();
    }

    override protected function updateLoadingProgress (
            soFar :Number, total :Number) :void
    {
        var prog :Number = (total == 0) ? 0 : (soFar / total);
        if (prog >= 1) {
            clearStyle("backgroundImage");
        }

        /*
        ** old style progress updating. We may want to do something like this
        ** again to show progress
        **
        graphics.clear();
        if (prog >= 1) {
            if (parent != null) {
                (parent as Container).invalidateDisplayList();
            }
            return; // once we're 100% loaded, we display no progress biz
        }

        var radius :Number = .5 * Math.min(contentWidth, contentHeight);

        graphics.beginFill(0x000000, .5);
        graphics.drawCircle(radius, radius, radius);
        graphics.beginFill(0xFFFFFF, .5);
        graphics.drawCircle(radius, radius, radius * prog);
        */
    }

    public function isInteractive () :Boolean
    {
        return _desc.isInteractive();
    }

    public function hasAction () :Boolean
    {
        return false;
    }

    protected function getHoverColor () :uint
    {
        return 0x40e0e0;
    }

    // TODO: this isn't really needed, because this method is
    // not used for our mouseOver hittesting.
    // But: it boggles my mind that the standard hitTestPoint() on a 
    // Bitmap doesn't seem to actually test the pixels in its BitmapData!!
    override public function hitTestPoint
        (x :Number, y :Number, shapeFlag :Boolean = false) :Boolean
    {
        try {
            if (_media is Loader && Loader(_media).content is Bitmap) {
                var b :Bitmap = Bitmap(Loader(_media).content);
                var p :Point = b.globalToLocal(new Point(x, y));
                return b.bitmapData.hitTest(new Point(0, 0), 0xFF, p);
            }
        } catch (err :Error) {
            // nada
        }
        return super.hitTestPoint(x, y, shapeFlag);
    }

    /**
     * Callback function.
     */
    protected function mouseOver (event :MouseEvent) :void
    {
        setGlow(true);
    }

    // TODO: remove. This was used as an alternate to mouseOver/mouseOut,
    // to try to fix transparent pixels, but the problem is that even if
    // we decide not to glow here, no other sprite will receive
    // the same mouse event to make a decision about it
    protected function mouseMoved (event :MouseEvent) :void
    {
        setGlow(hitTestPoint(event.stageX, event.stageY, true));
    }

/**
  The following is attempting to make us only glow when the mouse is
  over a non-transparent pixel. However, there seems to be a bug in
  hitTestPoint that is either considering transparent pixels 'hit', or 
  the shapeFlag paramter is being ignored.
  Either way, I'm going to operate on the assumption that mouseOver is doing
  proper hit-test stuff (since it seems to for swfs) and that the bug with
  mouseOver triggering over transparent pixels is the same bug that is
  preventing the following workaround to work. I'll leave everything simple
  and hope that it just works correctly in the future when this bug is fixed.

    protected function mouseMoved (event :MouseEvent) :void
    {
        var disp :DisplayObject = _media;
        if (disp is Loader) {
            try {
                disp = (disp as Loader).content;
                trace("Got happy disp : " + disp);
            } catch (err :Error) {
                trace("couldn't access content");
            }
        }

        var hit :Boolean = disp.hitTestPoint(event.stageX, event.stageY, true);
        trace("No shit, point (" + event.localX + ", " + event.localY +
            ") hits " + hit);
        setGlow(hit);
    }
*/

    /**
     * Callback function.
     */
    protected function mouseOut (event :MouseEvent) :void
    {
        setGlow(false);
    }

    /**
     * Turn on or off the glow surrounding this sprite.
     */
    public function setGlow (doGlow :Boolean) :void
    {
        // if things are already in the proper state, do nothing
        if (doGlow == (_glow != null)) {
            return;
        }

        // otherwise, enable or disable the glow
        if (doGlow) {
            _glow = new Glow(this);
            _glow.alphaFrom = 0;
            _glow.alphaTo = 1;
            _glow.blurXFrom = 0;
            _glow.blurXTo = 20;
            _glow.blurYFrom = 0;
            _glow.blurYTo = 20;
            _glow.color = getHoverColor();
            _glow.duration = 200;
            _glow.play();

        } else {
            _glow.end();
            _glow = null;

            // remove the GlowFilter that is added
            // TODO: maybe ensure there are no other filters that
            // need preserving
            filters = new Array();
        }
    }

    /**
     * Callback function.
     */
    public function mouseClick (event :MouseEvent) :void
    {
        // nada
    }

     /*
    protected function mouseClickCap (event :MouseEvent) :void
    {
        if (!stopClicks) {
            return;
        }
        trace("mouse clicked, will kibosh. target=" + event.target +
            ", phase=" + event.eventPhase +
            ", bubbles=" + event.bubbles);
        event.stopImmediatePropagation();

        var timer :Timer = new Timer(1000, 1);
        timer.addEventListener(TimerEvent.TIMER, function (evt :Event) :void {
            var mousey :MouseEvent = new MouseEvent(
                MouseEvent.CLICK, event.bubbles, event.cancelable,
                event.localX, event.localY, event.relatedObject);
            trace("now dispatching alternate click");
            var oldMouseChildren :Boolean = mouseChildren;
            mouseChildren = true;
            stopClicks = false;
            (event.target as IEventDispatcher).dispatchEvent(mousey);
            //_dispatch.dispatchEvent(mousey);
            mousey = new MouseEvent(
                MouseEvent.CLICK, true, false,
                30, 30, _loader);
            _loader.dispatchEvent(mousey);
            //_loader.dispatchEvent(mousey);
            stopClicks = true;
            mouseChildren = oldMouseChildren;
        });
        timer.start();
    }

    var stopClicks :Boolean = true;
    */

/*
    protected function tick (event :Event) :void
    {
        if (!mouseEnabled) {
            trace("mouse was disabled on media: " + _desc.URL);
            mouseEnabled = true;
        }
        if (mouseChildren) {
            trace("mousechildren enabled on " + _desc.URL);
            // setting this to false makes swfs not capture mouse input
            // so that mouse hover, etc, work.
            mouseChildren = false;
        }
    }
*/

    /**
     * Handle a query from our usercode content.
     */
    protected function handleInterfaceQuery (event :TextEvent) :void
    {
        log.warning("Unknown query from usercode: " + event.text);
    }

    /** An id (hopefully unique on this machine) used to communicate with
     * AVM1 swfs over a LocalConnection. */
    protected var _id :String;

    /** Our Media descripter. */
    protected var _desc :MediaDesc;

    /** The 'location' scale of the media: the scaling that is the result of
     * emulating perspective while we move around the room. */
    protected var _locScale :Number = 1;

    /** Are we being edited? */
    protected var _editing :Boolean;

    /** Used to dispatch events down to the swf we contain. */
    protected var _dispatch :EventDispatcher;

    /** The glow effect used for mouse hovering. */
    protected var _glow :Glow;

    /** A single LocalConnection used to communicate with all AVM1 media. */
    protected static var _oldDispatch :LocalConnection;

    [Embed(source="../../../../../../../rsrc/media/indian_h.png")]
    protected static const _loadingImgClass :Class;
}
}
