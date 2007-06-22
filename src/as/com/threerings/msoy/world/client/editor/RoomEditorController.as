//
// $Id$

package com.threerings.msoy.world.client.editor {

import mx.controls.Button;
import flash.events.Event;
import flash.events.KeyboardEvent;
import flash.events.MouseEvent;
import flash.geom.Point;
import flash.geom.Rectangle;
import flash.ui.Keyboard;

import com.threerings.msoy.client.WorldContext;
import com.threerings.msoy.world.client.FurniSprite;
import com.threerings.msoy.world.client.MsoySprite;
import com.threerings.msoy.world.client.RoomController;
import com.threerings.msoy.world.client.RoomView;


/**
 * Controller for the room editing panel. It starts up two different types of UI: one is
 * a regular Flex window with buttons like "delete" and "undo", and the other is a furni editor,
 * displayed as a border around the targetted furni with grabbable hotspots to manipulate it.
 */
public class RoomEditorController
{
    public function RoomEditorController (ctx :WorldContext, view :RoomView)
    {
        _ctx = ctx;
        _view = view;

        _edit = new FurniEditor(this);
        _hover = new FurniHighlight(this);
    }

    public function get roomView () :RoomView
    {
        return _view;
    }

    /**
     * Returns true if the room is currently being edited.
     */
    public function isEditing () :Boolean
    {
        return _view != null && _panel != null && _panel.isOpen;
    }

    /**
     * Initializes all editing UIs and starts editing the room.
     */
    public function startEditing (wrapupFn :Function) :void
    {
        if (_view == null) {
            Log.getLog(this).warning("Cannot edit a null room view!");
        }
        
        _panel = new RoomEditorPanel(_ctx, this);
        _wrapupFn = wrapupFn;

        _view.setEditing(true);
        _edit.start();
        _hover.start();

        _panel.open();
      
    }

    /**
     * Called by the room controller, cancels any current editing functions.
     */
    public function endEditing () :void
    {
        _panel.close();
        
        // note: the rest of cleanup will happen in actionEditorClosed
    }

    /**
     * Called by the room controller, to specify whether the undo stack is empty.
     */
    public function updateUndoStatus (isEmpty :Boolean) :void
    {
        // FIXME ROBERT: dim the button if empty
    }

    /**
     * Called by the room controller, to query whether the user should be allowed to move
     * around the scene.
     */
    public function isMovementEnabled() :Boolean
    {
        return isEditing() && _edit.isIdle();
    }

    /**
     * Cleans up editing actions and closes editing UIs. This function is called automatically
     * when the main editing UI is being closed (whether because the user clicked the close
     * button, or because the room controller cancelled the editing session).
     */
    public function actionEditorClosed () :void
    {
        //trace("*** actionEditorClosed");
        
        if (_panel != null && _panel.isOpen) {
            Log.getLog(this).warning("Room editor failed to close!");
        }
        
        _edit.end();
        _hover.end();
        _view.setEditing(false);
        
        _wrapupFn();
        _panel = null;
    }


    // Functions for highlighting targets and displaying the furni editing UI
    
    /** Called by the room controller, when the user rolls over or out of a valid sprite. */
    public function mouseOverSprite (sprite :MsoySprite) :void
    {
        var sprite :MsoySprite = _edit.isIdle() ? sprite : null;
        if (_hover.target != sprite &&
            (_edit.target != null ? _edit.target != sprite : true))
        {
            // either the player is hovering over a new sprite, or switching from an old
            // target to nothing at all. in either case, update!
            _hover.target = sprite as FurniSprite;
        }
    }

    /** Called by the room controller, when the user clicks on a valid sprite. */
    public function mouseClickOnSprite (sprite :MsoySprite, event :MouseEvent) :void
    {
        if (_edit.isIdle()) {
            //trace("*** SELECTING SPRITE: " + sprite);
            _hover.target = null;
            _edit.target = sprite as FurniSprite;
        }
    }

    /**
     * Called after target sprite modification, it will update all UIs to update their parameters.
     */
    public function targetSpriteUpdated () :void
    {
        _edit.updateDisplay();
        // todo: update flex panel here
    }

    protected var _ctx :WorldContext;
    protected var _view :RoomView;
    protected var _edit :FurniEditor;
    protected var _hover :FurniHighlight;
    protected var _panel :RoomEditorPanel;
    protected var _wrapupFn :Function;   // will be called when ending editing

    
}
}
