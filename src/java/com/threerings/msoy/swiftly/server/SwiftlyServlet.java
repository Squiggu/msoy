//
// $Id$

package com.threerings.msoy.swiftly.server;

import static com.threerings.msoy.Log.log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;

import com.samskivert.io.PersistenceException;
import com.samskivert.jdbc.depot.DuplicateKeyException;

import com.threerings.presents.dobj.RootDObjectManager;

import com.threerings.msoy.item.data.all.Item;

import com.threerings.msoy.data.all.FriendEntry;
import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.server.ServerConfig;
import com.threerings.msoy.server.persist.MemberRecord;

import com.threerings.msoy.swiftly.data.SwiftlyCodes;
import com.threerings.msoy.swiftly.data.all.SwiftlyProject;
import com.threerings.msoy.swiftly.gwt.SwiftlyConnectConfig;
import com.threerings.msoy.swiftly.gwt.SwiftlyService;
import com.threerings.msoy.swiftly.server.persist.SwiftlyProjectRecord;
import com.threerings.msoy.swiftly.server.persist.SwiftlyRepository;
import com.threerings.msoy.swiftly.server.persist.SwiftlySVNStorageRecord;
import com.threerings.msoy.swiftly.server.storage.ProjectSVNStorage;
import com.threerings.msoy.swiftly.server.storage.ProjectStorageException;

import com.threerings.msoy.web.data.ConnectConfig;
import com.threerings.msoy.web.data.ServiceCodes;
import com.threerings.msoy.web.data.ServiceException;
import com.threerings.msoy.web.server.MsoyServiceServlet;
import com.threerings.msoy.web.server.ServletWaiter;

/**
 * Provides the server implementation of {@link SwiftlyService}.
 */
public class SwiftlyServlet extends MsoyServiceServlet
    implements SwiftlyService
{
    // from SwiftlyService
    public SwiftlyConnectConfig getConnectConfig (final int projectId)
        throws ServiceException
    {
        final MemberRecord memrec = requireAuthedUser();

        // load the project. this also verifies the user has permissions to view the project
        final SwiftlyProject project = loadProject(projectId);

        // run a task on the dobject thread that finds the ProjectRoomManager for this project
        // either on this server or on a different node and returns that server's ConnectConfig
        final ServletWaiter<ConnectConfig> waiter =
            new ServletWaiter<ConnectConfig>("resolveRoomManager[" + projectId + "]");
        _omgr.postRunnable(new Runnable() {
            public void run () {
                _swiftlyMan.resolveRoomManager(memrec.getName(), project, waiter);
            }
        });

        // block the servlet waiting for the dobject thread
        waiter.waitForResult();

        return new SwiftlyConnectConfig(waiter.getArgument(), project);
    }

    // from SwiftlyService
    public List<SwiftlyProject> getRemixableProjects ()
        throws ServiceException
    {
        requireAuthedUser();

        ArrayList<SwiftlyProject> projects = new ArrayList<SwiftlyProject>();
        try {
            for (SwiftlyProjectRecord pRec :
                _swiftlyRepo.findRemixableProjects()) {
                projects.add(pRec.toSwiftlyProject());
            }
        } catch (PersistenceException pe) {
            log.warning("Getting remixable projects failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        return projects;
    }

    // from SwiftlyService
    public List<SwiftlyProject> getMembersProjects ()
        throws ServiceException
    {
        MemberRecord memrec = requireAuthedUser();

        ArrayList<SwiftlyProject> projects = new ArrayList<SwiftlyProject>();
        try {
            for (SwiftlyProjectRecord pRec :
                _swiftlyRepo.findMembersProjects(memrec.memberId)) {
                projects.add(pRec.toSwiftlyProject());
            }
        } catch (PersistenceException pe) {
            log.warning("Getting user's projects failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        return projects;
    }

    // from SwiftlyService
    public SwiftlyProject createProject (String projectName, byte projectType, boolean remixable)
        throws ServiceException
    {
        MemberRecord memrec = requireAuthedUser();

        SwiftlyProject project;
        SwiftlyProjectRecord pRec;
        SwiftlySVNStorageRecord storeRec;

        // TODO Argument Validation
        /*
        if (!isValidName(project.name)) {
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }
        */

        if (!SwiftlyProject.isValidProjectType(projectType)) {
            log.warning("Failed to create project. Invalid project type. [type=" +
                projectType + "]");
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        // Initialize the project storage.
        try {
            // XXX We need to sort out how to properly create remote repositories.
            // Until then, we create them in a hard-wired local directory.
            String svnRoot = ServerConfig.serverRoot + "/data/swiftly/projects";
            storeRec = _swiftlyRepo.createSVNStorage(ProjectSVNStorage.PROTOCOL_FILE,
                "", 0, svnRoot);

        } catch (PersistenceException pe) {
            log.warning("Creating new project storage record failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        // Create the project record.
        try {
            pRec = _swiftlyRepo.createProject(
                memrec.memberId, projectName, projectType, storeRec.storageId, remixable);
            project = pRec.toSwiftlyProject();

            // Set the creator as the first collaborator.
            _swiftlyRepo.joinCollaborators(pRec.projectId, memrec.memberId);

        } catch (DuplicateKeyException dke) {
            throw new ServiceException(SwiftlyCodes.E_PROJECT_NAME_EXISTS);

        } catch (PersistenceException pe) {
            log.warning("Creating new project failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        // If the repository initialization fails, we do our best to roll back any database
        // modifications. Hopefully that works.  Oh, what I'd give for transactions!

        // Initialize the SVN storage
        try {
            // Load the template from the standard path
            File templatePath = new File(ServerConfig.serverRoot + "/data/swiftly/templates/"
                + Item.getTypeName(projectType));
            ProjectSVNStorage.initializeStorage(project, storeRec, templatePath);

        } catch (ProjectStorageException pse) {
            log.warning("Initializing swiftly project storage failed.", pse);
            try {
                _swiftlyRepo.deleteProject(pRec);
            } catch (PersistenceException pe) {
                log.warning("Deleting the partially-initialized swiftly project failed.", pe);
            }
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        return project;
    }

    // from SwiftlyService
    public void updateProject (SwiftlyProject project)
        throws ServiceException
    {
        MemberRecord memrec = requireAuthedUser();
        requireOwner(project.projectId, memrec.memberId);

        // TODO Argument Validation
        /*
        if (!isValidName(project.name)) {
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }
        */

        try {
            SwiftlyProjectRecord pRec = _swiftlyRepo.loadProject(project.projectId);
            if (pRec == null) {
                throw new PersistenceException("Swiftly project not found! [id=" +
                    project.projectId + "]");
            }
            Map<String, Object> updates = pRec.findUpdates(project);
            if (updates.size() > 0) {
                _swiftlyRepo.updateProject(project.projectId, updates);
                // inform the room manager, if resolved, that the project has changed
                updateRoomProject(project);
            }

        } catch (PersistenceException pe) {
            log.warning("Updating project failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }
    }

    // from SwiftlyService
    public void deleteProject (int projectId)
        throws ServiceException
    {
        MemberRecord memrec = requireAuthedUser();
        requireOwner(projectId, memrec.memberId);

        try {
            _swiftlyRepo.markProjectDeleted(projectId);
        } catch (PersistenceException pe) {
            log.warning("Marking project deleted failed [id= " + projectId + "].", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }
    }

    // from SwiftlyService
    public SwiftlyProject loadProject (int projectId)
        throws ServiceException
    {
        MemberRecord memrec = requireAuthedUser();

        try {
            SwiftlyProjectRecord pRec = _swiftlyRepo.loadProject(projectId);
            if (pRec == null) {
                throw new ServiceException(SwiftlyCodes.E_NO_SUCH_PROJECT);
            }
            // verify the user has permission to view this project
            // TODO: read access is defined here and the room object. Can we simplify this?
            if (!pRec.remixable && !isCollaborator(pRec.projectId, memrec.memberId)) {
                throw new ServiceException(SwiftlyCodes.ACCESS_DENIED);
            }
            return pRec.toSwiftlyProject();

        } catch (PersistenceException pe) {
            log.warning("Loading project failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }
    }

    // from SwiftlyService
    public MemberName getProjectOwner (int projectId)
        throws ServiceException
    {
        requireAuthedUser();

        try {
            MemberRecord mRec = _swiftlyRepo.loadProjectOwner(projectId);
            if (mRec == null) {
                throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
            }
            return mRec.getName();

        } catch (PersistenceException pe) {
            log.warning("Loading project owner failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }
    }

    // from SwiftlyService
    public List<MemberName> getProjectCollaborators (int projectId)
        throws ServiceException
    {
        MemberRecord memrec = requireAuthedUser();
        requireCollaborator(projectId, memrec.memberId);

        ArrayList<MemberName> members = new ArrayList<MemberName>();

        try {
            for (MemberRecord mRec :
                _swiftlyRepo.getCollaborators(projectId)) {
                members.add(mRec.getName());
            }
        } catch (PersistenceException pe) {
            log.warning("Getting project's collaborators failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        return members;
    }

    // from SwiftlyService
    public List<FriendEntry> getFriends ()
        throws ServiceException
    {
        MemberRecord memrec = requireAuthedUser();

        try {
            return _memberRepo.loadFriends(memrec.memberId, -1);
        } catch (PersistenceException pe) {
            log.warning("Getting member's friends failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }
    }

    // from SwiftlyService
    public void leaveCollaborators (int projectId, MemberName name)
        throws ServiceException
    {
        MemberRecord memrec = requireAuthedUser();
        requireOwner(projectId, memrec.memberId);

        // Don't let the owner remove themselves.
        if (isOwner(projectId, name.getMemberId())) {
            log.warning("Refusing to remove the project owner from collaborators. Aborting " +
                "request. [projectId=" + projectId + "]");
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        try {
            _swiftlyRepo.leaveCollaborators(projectId, name.getMemberId());
        } catch (PersistenceException pe) {
            log.warning("Removing project's collaborators failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        // inform the project room manager of the change in collaborators
        removeFromRoomCollaborators(projectId, name);
    }

    // from SwiftlyService
    public void joinCollaborators (int projectId, MemberName name)
        throws ServiceException
    {
        MemberRecord memrec = requireAuthedUser();
        requireOwner(projectId, memrec.memberId);

        // if the user is already a collaborator, do nothing
        if (isCollaborator(projectId, name.getMemberId())) {
            log.warning("Refusing to add an existing collaborator to project. Aborting request. " +
                "[projectId="+ projectId + ", memberId=" + name.getMemberId() + "]");
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        try {
            _swiftlyRepo.joinCollaborators(projectId, name.getMemberId());
        } catch (PersistenceException pe) {
            log.warning("Joining project's collaborators failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        // inform the project room manager of the change in collaborators
        addToRoomCollaborators(projectId, name);
    }

    /**
     * Informs the room manager for this project, if resolved, that a collaborator has
     * been added.
     */
    protected void addToRoomCollaborators (final int projectId, final MemberName name)
        throws ServiceException
    {
        // run a task on the dobject thread that first finds the ProjectRoomManager for this
        // project if it exists, and then tells it to update its local list of collaborators
        final ServletWaiter<Void> waiter =
            new ServletWaiter<Void>("addCollaborator[" + projectId + "]");
        _omgr.postRunnable(new Runnable() {
            public void run () {
                _swiftlyMan.addCollaborator(projectId, name, waiter);
            }
        });

        // block the servlet waiting for the dobject thread
        waiter.waitForResult();
    }

    /**
     * Informs the room manager for this project, if resolved, that a collaborator has
     * been removed.
     */
    protected void removeFromRoomCollaborators (final int projectId, final MemberName name)
        throws ServiceException
    {
        // run a task on the dobject thread that first finds the ProjectRoomManager for this
        // project if it exists, and then tells it to update its local list of collaborators
        final ServletWaiter<Void> waiter =
            new ServletWaiter<Void>("removeCollaborator[" + projectId + "]");
        _omgr.postRunnable(new Runnable() {
            public void run () {
                _swiftlyMan.removeCollaborator(projectId, name, waiter);
            }
        });

        // block the servlet waiting for the dobject thread
        waiter.waitForResult();
    }

    /**
     * Informs the room manager for this project, if resolved, that the project has been modified.
     */
    protected void updateRoomProject (final SwiftlyProject project)
        throws ServiceException
    {
        // run a task on the dobject thread that first finds the ProjectRoomManager for this
        // project if it exists, and then tells it to update its local swiftly project
        final ServletWaiter<Void> waiter =
            new ServletWaiter<Void>("updateProject[" + project.projectId + "]");
        _omgr.postRunnable(new Runnable() {
            public void run () {
                _swiftlyMan.updateProject(project, waiter);
            }
        });

        // block the servlet waiting for the dobject thread
        waiter.waitForResult();
    }

    /**
     * Verifies a member is a collaborator of a project.
     * @param projectId the id of the project being tested
     * @param memberId the id of the member being tested
     * @throws ServiceException thrown if the memberId is not a collaborator.
     */
    protected void requireCollaborator (int projectId, int memberId)
        throws ServiceException
    {
        if (!isCollaborator(projectId, memberId)) {
            throw new ServiceException(SwiftlyCodes.ACCESS_DENIED);
        }
    }

    /**
     * Verifies a member is the owner of a project.
     * @param projectId the id of the project being tested
     * @param memberId the id of the member being tested
     * @throws ServiceException thrown if the memberId is not the owner.
     */
    protected void requireOwner (int projectId, int memberId)
        throws ServiceException
    {
        if (!isOwner(projectId, memberId)) {
            throw new ServiceException(SwiftlyCodes.ACCESS_DENIED);
        }
    }

    /**
     * Determines if a member is a collaborator on the supplied project.
     * @param projectId the id of the project being tested
     * @param memberId the id of the member being tested
     * @return true if memberId is a collaborator, false otherwise.
     * @throws ServiceException thrown if PersistenceException is encountered
     */
    protected boolean isCollaborator (int projectId, int memberId)
        throws ServiceException
    {
        try {
            return _swiftlyRepo.isCollaborator(projectId, memberId);
        } catch (PersistenceException pe) {
            log.warning("Checking project membership failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }
    }

    /**
     * Determines if a member is the owner of the supplied project.
     * @param projectId the id of the project being tested
     * @param memberId the id of the member being tested
     * @return true if memberId is the owner, false otherwise.
     * @throws ServiceException thrown if PersistenceException is encountered
     */
    protected boolean isOwner (int projectId, int memberId)
        throws ServiceException
    {
        try {
            return _swiftlyRepo.isOwner(projectId, memberId);
        } catch (PersistenceException pe) {
            log.warning("Checking project ownership failed.", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }
    }

    // our dependencies
    @Inject protected RootDObjectManager _omgr;
    @Inject protected SwiftlyManager _swiftlyMan;
    @Inject protected SwiftlyRepository _swiftlyRepo;
}
