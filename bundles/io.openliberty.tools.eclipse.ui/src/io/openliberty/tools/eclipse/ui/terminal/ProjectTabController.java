/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial implementation
*******************************************************************************/
package io.openliberty.tools.eclipse.ui.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Listener;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.terminal.view.ui.interfaces.ITerminalsView;
import org.eclipse.tm.terminal.view.ui.interfaces.IUIConstants;
import org.eclipse.tm.terminal.view.ui.manager.ConsoleManager;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.terminal.ProjectTab.State;

/**
 * Manages a set of terminal view project tab instances.
 */
public class ProjectTabController {

    /** Terminal view ID. */
    public static final String TERMINAL_VIEW_ID = "org.eclipse.tm.terminal.view.ui.TerminalsView";

    /** The set of active Terminal associated with different application projects. */
    private static final ConcurrentHashMap<String, ProjectTab> projectTabMap = new ConcurrentHashMap<String, ProjectTab>();

    /** The set of terminal listeners associated with the different application projects. */
    private static final ConcurrentHashMap<String, List<TerminalListener>> projectTerminalListenerMap = new ConcurrentHashMap<String, List<TerminalListener>>();

    /** TerminalManager instance. */
    private static ProjectTabController instance;

    /** Terminal console manager instance. */
    private ConsoleManager consoleMgr;

    /**
     * Constructor.
     */
    private ProjectTabController() {
        this.consoleMgr = ConsoleManager.getInstance();
        // registerListener();
    }

    private void registerListener() {
        // if (!listenersRegistered) {

        IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (activePage != null) {
            IViewPart viewPart = activePage.findView(IUIConstants.ID);
            if (viewPart != null || (viewPart instanceof ITerminalsView)) {
                ITerminalsView view = (ITerminalsView) viewPart;
                CTabFolder folder = view.getAdapter(CTabFolder.class);
                System.out.println("@ed: registering tab folder listener ...");

                // Manages individual tabs
                folder.addCTabFolder2Listener(new CTabFolder2Listener() {

                    public void close(CTabFolderEvent event) {
                        System.out.println("addCTabFolder2Listener close called. Event: " + event);
                        CTabItem item = (CTabItem) event.item;
                        if (item != null && !item.isDisposed()) {
                            try {
                                System.out.println("addCTabFolder2Listener close called. tooltip text tabitemt: " + item.getToolTipText());
                                String projectName = (String) item.getText();
                                ProjectTab projectTab = projectTabMap.get(projectName);
                                String cmd = "exit" + System.lineSeparator();

                                projectTab.writeToStream(cmd.getBytes());
                                System.out.println("addCTabFolder2Listener close called. successfully called exit");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            System.out.println("addCTabFolder2Listener close called but item is already disabled. Event: " + event);
                        }
                        // projectTab.getParent().removeCTabFolder2Listener(this);
                    }

                    @Override
                    public void maximize(CTabFolderEvent arg0) {
                    }

                    @Override
                    public void minimize(CTabFolderEvent arg0) {
                    }

                    @Override
                    public void restore(CTabFolderEvent arg0) {
                    }

                    @Override
                    public void showList(CTabFolderEvent arg0) {
                    }
                });
                // Manages all tabs. option 1. Not sure if it works ... it gets called prior to
                // the terminal service dispose calls. Need to test to see if it is too late.
                System.out.println("@ed: registering dispose ...");

                folder.addDisposeListener(new DisposeListener() {
                    @Override
                    public void widgetDisposed(DisposeEvent event) {
                        System.out.println("@ed: IPartListener close called. data: " + event.data);
                        cleanupTerminalView();
                    }
                });

                // Manages all tabs. option 2. I believe this works.
                IWorkbenchPartSite site = viewPart.getViewSite();
                IWorkbenchPage iwp = site.getPage();
                System.out.println("@ed: registering part listener ...");
                iwp.addPartListener(new IPartListener() {
                    @Override
                    public void partActivated(IWorkbenchPart arg0) { // TODO Auto-generated method stub

                    }

                    @Override
                    public void partBroughtToTop(IWorkbenchPart arg0) { // TODO Auto-generated method stub
                    }

                    @Override
                    public void partClosed(IWorkbenchPart part) {
                        System.out
                                .println("IPartListener close called. title: " + part.getTitle() + ", part id: " + part.getSite().getId());
                        if (IUIConstants.ID.equals(part.getSite().getId())) {
                            cleanupTerminalView();
                        }
                    }

                    @Override
                    public void partDeactivated(IWorkbenchPart arg0) { // TODO Auto-generated method stub

                    }

                    @Override
                    public void partOpened(IWorkbenchPart arg0) { // TODO Auto-generated method stub

                    }

                });

            }
        }

    }

    /**
     * Returns a singleton instance of this class.
     *
     * @return A singleton instance of this class.
     */
    public static ProjectTabController getInstance() {
        if (instance == null) {
            instance = new ProjectTabController();
        }

        return instance;
    }

    /**
     * Runs the specified command on a terminal.
     *
     * @param projectName The application project name.
     * @param projectPath The application project path.
     * @param command The command to execute on the terminal.
     * @param envs The environment properties to be set on the terminal.
     */
    public void runOnTerminal(String projectName, String projectPath, String command, List<String> envs) {
        ProjectTab projectTab = new ProjectTab(projectName);
        projectTabMap.put(projectName, projectTab);
        projectTab.runCommand(projectPath, command, envs);
    }

    /**
     * Writes the input data to the terminal tab associated with the input project name.
     *
     * @param projectName The application project name.
     * @param content The data to write.
     *
     * @throws Exception
     */
    public void writeTerminalStream(String projectName, byte[] data) throws Exception {
        ProjectTab projectTab = projectTabMap.get(projectName);

        if (projectTab == null) {
            String msg = "Unable to write to the terminal associated with project " + projectName
                    + ". Internal poject tab object not found.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg + ". Data to write: " + new String(data));
            }
            throw new Exception(msg);
        }

        projectTab.writeToStream(data);
    }

    /**
     * Returns the terminal tab item associated with the specified project name and connector.
     *
     * @param projectName The application project name.
     * @param connector The terminal connector object associated with input project name.
     *
     * @return the terminal tab item associated with the specified project name and connector.
     */
    public CTabItem getTerminalTabItem(String projectName, ITerminalConnector connector) {
        CTabItem item = null;

        if (connector != null) {
            item = consoleMgr.findConsole(IUIConstants.ID, null, projectName, connector, null);
        }

        return item;
    }

    /**
     * Returns the ProjectTab instance associated with the specified project name.
     *
     * @param projectName The application project name.
     *
     * @return the ProjectTab instance associated with the specified project name.
     */
    public ProjectTab getProjectTab(String projectName) {
        return projectTabMap.get(projectName);
    }

    public State getTerminalState(String projectName) {
        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab != null) {
            return projectTab.getState();
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Internal project tab object associated with project " + projectName
                        + " was not found. ProjectTabMap: " + projectTabMap);
            }
        }

        return null;
    }

    public void setTerminalState(String projectName, State newState) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { projectName, newState });
        }

        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab == null) {
            String msg = "Internal project tab object associated with project: " + projectName + " was not found. Unable to set state.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg);
            }
            throw new Exception();
        }

        projectTab.setState(newState);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, projectTab.getState());
        }
    }

    /**
     * Returns the connector associated with a terminal running the application represented by the input project name.
     *
     * @param projectName The application project name.
     *
     * @return The Connector associated with a terminal running the application represented by the input project name.
     */
    public ITerminalConnector getProjectConnector(String projectName) {
        ITerminalConnector connector = null;
        ProjectTab projectTab = projectTabMap.get(projectName);

        if (projectTab != null) {
            connector = projectTab.getConnector();
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Internal project tab object associated with project " + projectName
                        + " was not found. Unable to retrieve connector. ProjectTabMap: " + projectTabMap);
            }
        }

        return connector;
    }

    /**
     * Saves the terminal connector instance on the terminal object represented by the input project name.
     *
     * @param projectName The application project name.
     * @param terminalConnector The terminal connector instance.
     */
    public void setProjectConnector(String projectName, ITerminalConnector terminalConnector) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { projectName, terminalConnector, projectTabMap.size() });
        }

        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab != null) {
            projectTab.setConnector(terminalConnector);
            projectTabMap.put(projectName, projectTab);
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Internal project tab object associated with project " + projectName
                        + " was not found. Unable to retrieve connector. ProjectTabMap: " + projectTabMap);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, "ProjectTabMapSize: " + projectTabMap.size());
        }
    }

    /**
     * Returns true if the tab title associated with the input project name was marked as closed. False, otherwise.
     *
     * @param projectName The application project name.
     *
     * @return true if the tab title associated with the input project name was marked as closed. False, otherwise.
     */
    public boolean isProjectTabMarkedClosed(String projectName) {
        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab != null) {
            String tabTitle = projectTab.getTitle();
            if (tabTitle != null && tabTitle.startsWith("<Closed>")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Exits Liberty dev mode running on all active terminal tabs.
     */
    public void cleanupTerminalView() {
        for (Map.Entry<String, ProjectTab> entry : projectTabMap.entrySet()) {
            String projectName = entry.getKey();
            ProjectTab projectTab = entry.getValue();
            exitDevMode(projectName, projectTab);
        }
    }

    public void exitDevMode(String projectName, ProjectTab projectTab) {
        if (projectTab != null) {
            try {
                System.out.println("@ed: exitDevModeRunningOnAllActiveTabs");
                projectTab.writeToStream(DevModeOperations.DEVMODE_COMMAND_EXIT.getBytes());
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (Exception e) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_UI, "Failed to exit dev mode associated with project " + projectName, e);
                }
            }
        }

    }

    /**
     * Cleans up the objects associated with the terminal object represented by the input project name.
     *
     * @param projectName The application project name.
     */
    public void cleanupTerminal(String projectName) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { projectName, projectTabMap.size() });
        }

        // Call the terminal object to do further cleanup.
        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab != null) {
            projectTab.cleanup();
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Internal project tab object associated with project " + projectName
                        + " was not found. ProjectTabMap: " + projectTabMap);
            }
        }

        // Remove the connector from the connector map cache.
        projectTabMap.remove(projectName);

        // Call cleanup on all registered terminal listeners and remove them from the terminal map cache.
        List<TerminalListener> listeners = projectTerminalListenerMap.get(projectName);
        if (listeners != null) {
            synchronized (listeners) {
                Iterator<TerminalListener> i = listeners.iterator();
                while (i.hasNext()) {
                    i.next().cleanup();
                }
            }
        }
        projectTerminalListenerMap.remove(projectName);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, "Project: " + projectName + ". ProjectTabMapSize: " + projectTabMap.size()
                    + ". projectTerminalListenerMapSize: " + projectTerminalListenerMap.size());
        }
    }

    /**
     * Registers the input terminal listener.
     * 
     * @param projectName The name of the project for which the listener is registered.
     * @param listener The listener implementation.
     */
    public void registerTerminalListener(String projectName, TerminalListener listener) {
        List<TerminalListener> listeners = projectTerminalListenerMap.get(projectName);
        if (listeners == null) {
            listeners = Collections.synchronizedList(new ArrayList<TerminalListener>());
        }

        listeners.add(listener);
        projectTerminalListenerMap.put(projectName, listeners);
    }

    /**
     * Deregisters the input terminal listener.
     * 
     * @param projectName The name of the project the input listener is registered for.
     * @param listener The listener implementation.
     */
    public void deregisterTerminalListener(String projectName, TerminalListener listener) {
        List<TerminalListener> listeners = projectTerminalListenerMap.get(projectName);
        if (listeners != null) {
            listeners.remove(listener);
            projectTerminalListenerMap.put(projectName, listeners);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Class: ").append(instance.getClass().getName()).append(": ");
        sb.append("projectTabMap size: ").append(projectTabMap.size()).append(", ");
        sb.append("projectTabMap: ").append(projectTabMap);
        return sb.toString();
    }
}
