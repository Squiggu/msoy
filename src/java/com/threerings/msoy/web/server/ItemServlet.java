//
// $Id$

package com.threerings.msoy.web.server;

import static com.threerings.msoy.Log.log;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import com.samskivert.io.PersistenceException;
import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.IntSet;

import com.threerings.msoy.person.server.persist.MailMessageRecord;
import com.threerings.msoy.server.MsoyServer;
import com.threerings.msoy.server.persist.MemberRecord;

import com.threerings.msoy.item.data.ItemCodes;
import com.threerings.msoy.item.data.all.Avatar;
import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.msoy.item.data.gwt.ItemDetail;
import com.threerings.msoy.item.server.persist.CloneRecord;
import com.threerings.msoy.item.server.persist.ItemRecord;
import com.threerings.msoy.item.server.persist.ItemRepository;

import com.threerings.msoy.web.client.ItemService;
import com.threerings.msoy.web.data.MailFolder;
import com.threerings.msoy.web.data.ServiceException;
import com.threerings.msoy.web.data.WebIdent;
import com.threerings.msoy.web.data.TagHistory;
import com.threerings.presents.data.InvocationCodes;

/**
 * Provides the server implementation of {@link ItemService}.
 */
public class ItemServlet extends MsoyServiceServlet
    implements ItemService
{
    // from interface ItemService
    public int createItem (WebIdent ident, final Item item)
        throws ServiceException
    {
        final MemberRecord memrec = requireAuthedUser(ident);

        // validate the item
        if (!item.isConsistent()) {
            // TODO?
            throw new ServiceException(ServiceException.INTERNAL_ERROR);
        }

        // TODO: validate anything else?

        // configure the item's creator and owner
        item.creatorId = memrec.memberId;
        item.ownerId = memrec.memberId;

        // write the item to the database
        final ItemRecord record = ItemRecord.newRecord(item);
        ItemRepository<ItemRecord, ?, ?, ?> repo = MsoyServer.itemMan.getRepository(item.getType());
        try {
            repo.insertOriginalItem(record);
        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Failed to create item " + item + ".", pe);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // let the item manager know that we've created this item
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.itemCreated(record);
            }
        });

        return record.itemId;
    }

    // from interface ItemService
    public void updateItem (WebIdent ident, Item item)
        throws ServiceException
    {
        // TODO: validate this user's ident

        // validate the item
        if (!item.isConsistent()) {
            // TODO?
            throw new ServiceException(ServiceException.INTERNAL_ERROR);
        }

        // TODO: validate anything else?

        // write the item to the database
        final ItemRecord record = ItemRecord.newRecord(item);
        ItemRepository<ItemRecord, ?, ?, ?> repo = MsoyServer.itemMan.getRepository(item.getType());
        try {
            repo.updateOriginalItem(record);
        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Failed to update item " + item + ".", pe);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // let the item manager know that we've updated this item
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.itemUpdated(record);
            }
        });
    }

    // from interface ItemService
    public Item loadItem (WebIdent ident, ItemIdent item)
        throws ServiceException
    {
        ItemRepository<ItemRecord, ?, ?, ?> repo = MsoyServer.itemMan.getRepository(item.type);
        try {
            ItemRecord irec = repo.loadItem(item.itemId);
            return (irec == null) ? null : irec.toItem();

        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Failed to load item [id=" + item + "].", pe);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }
    }

    // from interface ItemService
    public ItemDetail loadItemDetail (WebIdent ident, final ItemIdent item)
        throws ServiceException
    {
        final ServletWaiter<ItemDetail> waiter = new ServletWaiter<ItemDetail>(
            "loadItem[" + item + "]");
        final int memberId = getMemberId(ident);
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.getItemDetail(item, memberId, waiter);
            }
        });
        return waiter.waitForResult();
    }

    // from interface ItemService
    public void scaleAvatar (WebIdent ident, final int avatarId, final float newScale)
        throws ServiceException
    {
        final MemberRecord memrec = requireAuthedUser(ident);
        final ServletWaiter<Avatar> waiter = new ServletWaiter<Avatar>(
            "scaleAvatar[" + memrec.memberId + ", " + avatarId + ", " + newScale + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.scaleAvatar(memrec.memberId, avatarId, newScale, waiter);
            }
        });
        waiter.waitForResult();
    }

    // from interface ItemService
    public Item remixItem (WebIdent ident, final ItemIdent item)
        throws ServiceException
    {
        final ServletWaiter<Item> waiter = new ServletWaiter<Item>("remixItem[" + item + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.remixItem(item, waiter);
            }
        });
        return waiter.waitForResult();
    }

    // from interface ItemService
    public void deleteItem (final WebIdent ident, final ItemIdent item)
        throws ServiceException
    {
        final MemberRecord memrec = requireAuthedUser(ident);
        final ServletWaiter<Void> waiter = new ServletWaiter<Void>("deleteItem[" + item + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.deleteItemFor(memrec.memberId, item, waiter);
            }
        });
        waiter.waitForResult();
    }

    // from interface ItemService
    public byte getRating (WebIdent ident, final ItemIdent item, final int memberId)
        throws ServiceException
    {
        final ServletWaiter<Byte> waiter = new ServletWaiter<Byte>("getRating[" + item + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.getRating(item, memberId, waiter);
            }
        });
        return waiter.waitForResult();
    }

    // from interface ItemService
    public float rateItem (final WebIdent ident, final ItemIdent item, final byte rating)
        throws ServiceException
    {
        final MemberRecord memrec = requireAuthedUser(ident);
        final ServletWaiter<Float> waiter = new ServletWaiter<Float>("rateItem[" + item + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.rateItem(item, memrec.memberId, rating, waiter);
            }
        });
        return waiter.waitForResult();
    }

    // from interface ItemService
    public Collection<String> getTags (WebIdent ident, final ItemIdent item)
        throws ServiceException
    {
        final ServletWaiter<Collection<String>> waiter =
            new ServletWaiter<Collection<String>>("getTags[" + item + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.getTags(item, waiter);
            }
        });
        return waiter.waitForResult();
    }

    // from interface ItemService
    public Collection<TagHistory> getTagHistory (WebIdent ident, final ItemIdent item)
        throws ServiceException
    {
        final ServletWaiter<Collection<TagHistory>> waiter =
            new ServletWaiter<Collection<TagHistory>>("getTagHistory[" + item + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.getTagHistory(item, waiter);
            }
        });
        return waiter.waitForResult();
    }

    // from interface ItemService
    public Collection<TagHistory> getRecentTags (WebIdent ident)
        throws ServiceException
    {
        final int memberId = getMemberId(ident);
        final ServletWaiter<Collection<TagHistory>> waiter =
            new ServletWaiter<Collection<TagHistory>>("getTagHistory[" + memberId + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.getRecentTags(memberId, waiter);
            }
        });
        return waiter.waitForResult();
    }

    // from interface ItemService
    public TagHistory tagItem (final WebIdent ident, final ItemIdent item, final String tag,
                               final boolean set)
        throws ServiceException
    {
        final MemberRecord memrec = requireAuthedUser(ident);
        final ServletWaiter<TagHistory> waiter = new ServletWaiter<TagHistory>(
            "tagItem[" + item + ", " + set + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.tagItem(item, memrec.memberId, tag, set, waiter);
            }
        });
        return waiter.waitForResult();
    }

    // from interface ItemService
    public void wrapItem (WebIdent ident, ItemIdent iident, boolean wrap)
        throws ServiceException
    {
        MemberRecord memrec = requireAuthedUser(ident);
        byte type = iident.type;
        ItemRepository<ItemRecord, ?, ?, ?> repo = MsoyServer.itemMan.getRepository(type);
        try {
            ItemRecord item = repo.loadItem(iident.itemId);
            if (item == null) {
                log.warning("Trying to " + (wrap ? "" : "un") + "wrap non-existent item " +
                            "[ident=" + ident + ", item=" + iident + "]");
                throw new ServiceException(InvocationCodes.INTERNAL_ERROR);
            }
            if (wrap) {
                if (item.ownerId != memrec.memberId) {
                    log.warning("Trying to wrap un-owned item [ident=" + ident +
                                ", item=" + iident + "]");
                    throw new ServiceException(InvocationCodes.INTERNAL_ERROR);
                }
                repo.updateOwnerId(item, 0);

            } else {
                if (item.ownerId != 0) {
                    log.warning("Trying to unwrap owned item [ident=" + ident +
                                ", item=" + iident + "]");
                    throw new ServiceException(InvocationCodes.INTERNAL_ERROR);
                }
                repo.updateOwnerId(item, memrec.memberId);
            }

        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Failed to wrap item [item=" + iident +
                    ", wrap=" + wrap + "]", pe);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }
    }

    // from interface ItemService
    public void setMature (WebIdent ident, ItemIdent iid, boolean value)
        throws ServiceException
    {
        MemberRecord mRec = requireAuthedUser(ident);
        if (!mRec.isSupport()) {
            throw new ServiceException(ItemCodes.ACCESS_DENIED);
        }

        try {
            ItemRepository<ItemRecord, ?, ?, ?> repo = MsoyServer.itemMan.getRepository(iid.type);
            // TODO: If things get really tight, this could use updatePartial() later.
            ItemRecord item = repo.loadItem(iid.itemId);
            if (item == null) {
                log.warning("Missing item for setFlags() [item=" + iid + ", value=" + value + "].");
                throw new ServiceException(ItemCodes.INTERNAL_ERROR);
            }
            item.mature = value;
            repo.updateOriginalItem(item);

        } catch (PersistenceException pe) {
            log.log(Level.WARNING,
                "Failed to set flags [item=" + iid + ", value=" + value + "]", pe);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }
    }

    // from interface ItemService
    public void setFlags (WebIdent ident, ItemIdent iid, byte mask, byte value)
        throws ServiceException
    {
        requireAuthedUser(ident);
        try {
            ItemRepository<ItemRecord, ?, ?, ?> repo = MsoyServer.itemMan.getRepository(iid.type);
            // TODO: If things get really tight, this could use updatePartial() later.
            ItemRecord item = repo.loadItem(iid.itemId);
            if (item == null) {
                log.warning("Missing item for setFlags() [item=" + iid + ", mask=" + mask +
                            ", value=" + value + "].");
                throw new ServiceException(ItemCodes.INTERNAL_ERROR);
            }
            item.flagged = (byte) ((item.flagged & ~mask) | value);
            repo.updateOriginalItem(item);

        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Failed to set flags [item=" + iid + ", mask=" + mask +
                    ", value=" + value + "]", pe);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }
    }

    // from interface ItemService
    public List getFlaggedItems (WebIdent ident, int count)
        throws ServiceException
    {
        MemberRecord mRec = requireAuthedUser(ident);
        if (!mRec.isSupport()) {
            throw new ServiceException(ItemCodes.ACCESS_DENIED);
        }
        List<ItemDetail> items = new ArrayList<ItemDetail>();
        // it'd be nice to round-robin the item types or something, so the first items in
        // the queue aren't always from the same type... perhaps we'll just do something
        // clever in the UI
        try {
            for (byte type : MsoyServer.itemMan.getRepositoryTypes()) {
                ItemRepository<ItemRecord, ?, ?, ?> repo = MsoyServer.itemMan.getRepository(type);
                for (ItemRecord record : repo.loadFlaggedItems(count)) {
                    Item item = record.toItem();

                    // get auxillary info and construct an ItemDetail
                    ItemDetail detail = new ItemDetail();
                    detail.item = item;
                    detail.memberRating = 0; // not populated
                    MemberRecord memRec = MsoyServer.memberRepo.loadMember(record.creatorId);
                    detail.creator = memRec.getName();
                    detail.owner = null; // not populated

                    // add the detail to our result and see if we're done
                    items.add(detail);
                    if (items.size() == count) {
                        return items;
                    }
                }
            }
            return items;

        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Getting flagged items failed.", pe);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }
    }

    // from interface ItemService
    public Integer deleteItemAdmin (WebIdent ident, ItemIdent iident, String subject, String body)
        throws ServiceException
    {
        MemberRecord mRec = requireAuthedUser(ident);
        if (!mRec.isSupport()) {
            throw new ServiceException(ItemCodes.ACCESS_DENIED);
        }
        byte type = iident.type;
        ItemRepository<ItemRecord, ?, ?, ?> repo = MsoyServer.itemMan.getRepository(type);
        try {
            ItemRecord item = repo.loadOriginalItem(iident.itemId);
            IntSet owners = new ArrayIntSet();

            int deletionCount = 0;
            owners.add(item.creatorId);

            // this item may be listed; make sure to unlist it
            repo.removeListing(item.itemId);

            // then delete any potential clones
            for (CloneRecord record : repo.loadCloneRecords(item.itemId)) {
                repo.deleteItem(record.itemId);
                deletionCount ++;
                owners.add(record.ownerId);
            }

            // finally delete the actual item
            repo.deleteItem(item.itemId);
            deletionCount ++;

            // build a message record
            MailMessageRecord record = new MailMessageRecord();
            record.senderId = 0;
            record.folderId = MailFolder.INBOX_FOLDER_ID;
            record.subject = subject;
            record.sent = new Timestamp(System.currentTimeMillis());
            record.bodyText = body;
            record.unread = true;

            // and notify everybody
            for (int ownerId : owners) {
                record.ownerId = ownerId;
                record.recipientId = ownerId;
                MsoyServer.mailMan.getRepository().fileMessage(record);
            }
            return Integer.valueOf(deletionCount);

        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Admin item delete failed [item=" + iident + "].", pe);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }
    }
}
