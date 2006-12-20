//
// $Id$

package com.threerings.msoy.server.persist;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.SQLException;

import com.samskivert.io.PersistenceException;
import com.samskivert.jdbc.JDBCUtil;
import com.samskivert.jdbc.ConnectionProvider;
import com.samskivert.jdbc.depot.DepotRepository;
import com.samskivert.jdbc.depot.Key;
import com.samskivert.jdbc.depot.clause.FieldOverride;
import com.samskivert.jdbc.depot.clause.FromOverride;
import com.samskivert.jdbc.depot.clause.Where;
import com.samskivert.jdbc.depot.operator.Conditionals.*;
import com.samskivert.jdbc.depot.expression.LiteralExp;
import com.samskivert.util.IntListUtil;
import com.samskivert.util.IntSet;

import com.threerings.msoy.server.MsoyServer;
import com.threerings.msoy.world.data.MsoySceneModel;
import com.threerings.msoy.web.data.Group;

/**
 * Manages the persistent store of group data.
 */
public class GroupRepository extends DepotRepository
{
    public GroupRepository (ConnectionProvider conprov)
    {
        super(conprov);
    }

    /**
     * Fetches all groups who's name starts with the given character.
     */
    public Collection<GroupRecord> findGroups (String startingCharacter)
        throws PersistenceException
    {
        return findAll(GroupRecord.class,
            new Where(new Equals(new LiteralExp("substring(name,1,1)"), startingCharacter)));
    }

    /**
     * Fetches a single group, by id. Returns null if there's no such group.
     */
    public GroupRecord loadGroup (int groupId)
        throws PersistenceException
    {
        return load(GroupRecord.class, groupId);
    }

    /**
     * Fetches multiple groups by id.
     */
    public Collection<GroupRecord> loadGroups (int[] groupIds)
        throws PersistenceException
    {
        if (groupIds.length == 0) {
            return Collections.emptyList();
        }
        Comparable[] idArr = IntListUtil.box(groupIds);
        return findAll(GroupRecord.class,
                       new Where(new In(GroupRecord.class, GroupRecord.GROUP_ID, idArr)));
    }

    /**
     * Creates a new group, defined by a {@link GroupRecord}. The key of the record must
     * be null -- it will be filled in through the insertion, and returned.  A blank room is 
     * also created that is owned by the group.
     */
    public int createGroup (GroupRecord record)
        throws PersistenceException
    {
        if (record.groupId != 0) {
            throw new PersistenceException(
                "Group record must have a null id for creation " + record);
        }
        insert(record);

        int sceneId = MsoyServer.sceneRepo.createBlankRoom(MsoySceneModel.OWNER_TYPE_GROUP, 
            record.groupId, /* TODO */ "Group " + record.name + "'s room");
        updateGroup(record.groupId, GroupRecord.HOME_SCENE_ID, sceneId);

        return record.groupId;
    }

    /**
     * Updates the specified group record with field/value pairs, e.g.
     *     updateGroup(groupId,
     *                 GroupRecord.CHARTER, newCharter,
     *                 GroupRecord.POLICY, Group.EXCLUSIVE);
     */
    public void updateGroup (int groupId, Object... fieldValues)
        throws PersistenceException
    {
        int rows = updatePartial(GroupRecord.class, groupId, fieldValues);
        if (rows == 0) {
            throw new PersistenceException(
                "Couldn't find group to modify [groupId=" + groupId + "]");
        }
    }

    /** 
     * Deletes the specified group from the repository.  This is generally only done when the
     * last member of a group leaves.
     */
    public void deleteGroup (GroupRecord group) 
        throws PersistenceException
    {
        delete(group);
    }
    
    /**
     * Makes a given person a member of a given group.
     */

    public GroupMembershipRecord joinGroup (int groupId, int memberId, byte rank)
        throws PersistenceException
    {
        GroupMembershipRecord record = new GroupMembershipRecord();
        record.groupId = groupId;
        record.memberId = memberId;
        record.rank = rank;
        insert(record);
        return record;
    }
    
    /**
     * Sets the rank of a member of a group.
     */
    public void setRank (int groupId, int memberId, byte newRank)
        throws PersistenceException
    {
        int rows = updatePartial(GroupMembershipRecord.class,
                                 new Key(GroupMembershipRecord.GROUP_ID, groupId,
                                         GroupMembershipRecord.MEMBER_ID, memberId),
                                 GroupMembershipRecord.RANK, newRank);
        if (rows == 0) {
            throw new PersistenceException(
                "Couldn't find group membership to modify [groupId=" + groupId +
                "memberId=" + memberId + "]");
        }
    }
    
    /**
     * Fetches the membership details for a given group and member, or null.
     * 
     */
    public GroupMembershipRecord getMembership(int groupId, int memberId)
        throws PersistenceException
    {
        return load(GroupMembershipRecord.class,
                    new Key(GroupMembershipRecord.GROUP_ID, groupId,
                            GroupMembershipRecord.MEMBER_ID, memberId));
    }

    /**
     * Remove a given person as member of a given group. This method returns
     * false if there was no membership to cancel.
     */
    public boolean leaveGroup (int groupId, int memberId)
        throws PersistenceException
    {
        int rows = deleteAll(GroupMembershipRecord.class,
                             new Key(GroupMembershipRecord.GROUP_ID, groupId,
                                     GroupMembershipRecord.MEMBER_ID, memberId));
        return rows > 0;
    }

    /**
     * Fetches the membership roster of a given group.
     */
    public int countMembers (int groupId)
        throws PersistenceException
    {
        GroupMembershipCount count =
            load(GroupMembershipCount.class,
                 new FieldOverride(GroupMembershipCount.COUNT, "count(*)"),
                 new Key(GroupMembershipRecord.GROUP_ID, groupId),
                 new FromOverride(GroupMembershipRecord.class));
        if (count == null) {
            throw new PersistenceException("Group not found [groupId=" + groupId + "]");
        }
        return count.count;
    }

    /**
     * Fetches the membership roster of a given group.
     */
    public Collection<GroupMembershipRecord> getMembers (int groupId)
        throws PersistenceException
    {
        return findAll(GroupMembershipRecord.class,
                       new Key(GroupMembershipRecord.GROUP_ID, groupId));
    }

    /**
     * Fetches the group memberships a given member belongs to.
     */
    public Collection<GroupMembershipRecord> getMemberships (int memberId)
        throws PersistenceException
    {
        return findAll(GroupMembershipRecord.class,
                       new Key(GroupMembershipRecord.MEMBER_ID, memberId));
    }

    /**
     * Fetches a list of the characters that start group names.  This method returns a 
     * List&lt;String&gt; instead of List&lt;Character&gt; because in GWT, java.lang.Character
     * is not Comparable.
     */
    public List<String> getCharacters () 
        throws PersistenceException
    {
        // force the creation of a GroupRecord table if necessary
        _ctx.getMarshaller(GroupRecord.class);
    
        // only one query of this type is ever performed, so the Comparable key is not important
        Key key = new Key("GroupNamePrefix", 0);
        return _ctx.invoke(new CollectionQuery<List<String>>(_ctx, GroupRecord.class, key) {
            public List<String> invoke (Connection conn)
                throws SQLException
            {
                String query = "select substring(name,1,1) as letter from GroupRecord " +
                    "where policy=" + Group.POLICY_PUBLIC + " group by letter";
                ArrayList<String> characters = new ArrayList<String>();
                Statement stmt = conn.createStatement();
                try {
                    ResultSet rs = stmt.executeQuery(query);
                    while (rs.next()) {
                        characters.add(rs.getString(1));
                    }
                    return characters;
                } finally {
                    JDBCUtil.close(stmt);
                }
            }
        });
    }
}
