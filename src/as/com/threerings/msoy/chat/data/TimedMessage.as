//
// $Id$

package com.threerings.msoy.chat.data {

import flash.utils.getTimer; // function import

import com.threerings.crowd.chat.data.ChatMessage;

/**
 * A class to keep track of a given Chat Message and the time at which it was shown to the player.
 */
public class TimedMessage
{
    public function TimedMessage (msg :ChatMessage) 
    {
        _msg = msg;
    }

    public function get msg () :ChatMessage
    {
        return _msg;
    }

    /**
     * Returns the time at which this message was displayed, or -1 if it has not yet been displayed.
     */
    public function get displayedAt () :int
    {
        return _timestamp;
    }

    public function showingNow () :void
    {
        _timestamp = getTimer();
    }

    protected var _msg :ChatMessage;
    protected var _timestamp :int = -1;
}
}
