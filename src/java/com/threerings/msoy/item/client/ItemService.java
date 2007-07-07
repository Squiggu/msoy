//
// $Id$

package com.threerings.msoy.item.client;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;

import com.threerings.msoy.item.data.all.ItemIdent;

/**
 * Provides services related to items.
 */
public interface ItemService extends InvocationService
{
    /**
     * Get the item's name. This can be called by any user for any item.
     */
    public void getItemName (Client client, ItemIdent item, ResultListener listener);

    /**
     * Load the specified item from the user's inventory for short-term examination.
     * An InvocationException will be thrown if the specified item does not belong to the user.
     */
    public void peepItem (Client client, ItemIdent item, ResultListener listener);

    /** 
     * Cause this item to become unused, removing it from the room that its in.
     */
    public void reclaimItem (
        Client client, ItemIdent item, ConfirmListener listener);
}
