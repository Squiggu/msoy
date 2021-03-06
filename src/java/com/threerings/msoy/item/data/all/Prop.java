//
// $Id$

package com.threerings.msoy.item.data.all;

import com.threerings.orth.data.MediaDesc;

/**
 * Contains the runtime data for a Prop item. A prop is smart furniture that is associated with an
 * AVRG.
 */
public class Prop extends IdentGameItem
{
    /** The id of the game with which we're associated. */
    public int gameId;

    @Override // from Item
    public MsoyItemType getType ()
    {
        return MsoyItemType.PROP;
    }

    @Override
    public boolean isConsistent ()
    {
        return super.isConsistent() && nonBlank(name, MAX_NAME_LENGTH) && (_furniMedia != null);
    }

    @Override // from Item
    public MediaDesc getPreviewMedia ()
    {
        return getFurniMedia();
    }

    @Override // from Item
    protected MediaDesc getDefaultFurniMedia ()
    {
        return null; // there is no default
    }
}
