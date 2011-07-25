//
// $Id$

package com.threerings.msoy.comment.server;

import java.util.Set;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.threerings.presents.annotation.BlockingThread;

import com.threerings.msoy.server.persist.MemberCardRecord;
import com.threerings.msoy.server.persist.MemberRecord;
import com.threerings.msoy.server.persist.MemberRepository;

import com.threerings.msoy.comment.data.all.Comment;
import com.threerings.msoy.comment.data.all.CommentType;
import com.threerings.msoy.comment.server.persist.CommentRecord;
import com.threerings.msoy.comment.server.persist.CommentRepository;

import com.threerings.msoy.web.gwt.MemberCard;

@BlockingThread @Singleton
public class CommentLogic
{
    public List<Comment> loadComments (CommentType etype, int eid, int offset, int count)
    {
        List<CommentRepository.CommentThread> threads = _commentRepo.loadComments(
            etype.toByte(), eid, offset, count, 2);

        // resolve the member cards for all commentors
        Set<Integer> memIds = Sets.newHashSet();
        for (CommentRepository.CommentThread thread : threads) {
            memIds.add(thread.comment.memberId);
            for (CommentRecord reply : thread.replies) {
                memIds.add(reply.memberId);
            }
        }
        Map<Integer, MemberCard> cards = MemberCardRecord.toMap(
            _memberRepo.loadMemberCards(memIds));

        // convert the comment records to runtime records
        List<Comment> comments = Lists.newArrayList();
        for (CommentRepository.CommentThread thread : threads) {
            Comment comment = thread.comment.toComment(cards);
            if (comment.commentor == null) {
                continue; // this member was deleted, shouldn't happen
            }
            for (CommentRecord reply : thread.replies) {
                comment.replies.add(reply.toComment(cards));
            }
            comment.hasMoreReplies = thread.hasMoreReplies;
            comments.add(comment);
        }

        return comments;
    }

    @Inject protected CommentRepository _commentRepo;
    @Inject protected MemberRepository _memberRepo;
}